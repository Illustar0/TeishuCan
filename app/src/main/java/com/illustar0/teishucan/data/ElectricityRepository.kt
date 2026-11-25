package com.illustar0.teishucan.data

import android.content.Context
import androidx.datastore.dataStoreFile
import com.illustar0.teishucan.concurrency.RefreshTracker
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.CampusRequestException
import com.illustar0.teishucan.domain.ElectricityMeter
import com.illustar0.teishucan.domain.InvalidMeterLocationException
import com.illustar0.teishucan.domain.MeterLocation
import com.illustar0.teishucan.domain.MeterOption
import com.illustar0.teishucan.domain.MeterReading
import com.illustar0.teishucan.domain.MeterRechargeResult
import com.illustar0.zzu.ZzuException
import com.illustar0.zzu.ecard.RoomId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Instant

/** 用户添加的电表（保存在本机）及校园电控服务。 */
@Singleton
class ElectricityRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val session: ZzuSession,
    @ApplicationScope scope: CoroutineScope,
) {
    private val store = JsonDataStore(
        file = context.dataStoreFile("electricity_meters.json"),
        defaultValue = emptyList(),
        serializer = serializer<List<ElectricityMeter>>(),
    )
    private val savedMeters = store.data

    /** 各房间的剩余电量，以 [MeterLocation.meterId] 为键。 */
    private val remainingEnergy = session.createSnapshotCache(
        "electricity.json",
        emptyMap(),
        serializer<Map<String, Double>>(),
    )
    private val refreshTracker = RefreshTracker()

    /** 已添加的电表及其读数；保存的列表读出之前为 null。 */
    val meters: StateFlow<List<MeterReading>?> = combine(savedMeters, remainingEnergy.value) { meters, energy ->
        meters.map { MeterReading(it, energy[it.location.meterId]) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val isRefreshing: StateFlow<Boolean> = refreshTracker.isRefreshing

    /**
     * 已保存电表的读数在 [now] 是否需要更新：超过 [maxAge] 未更新，或有电表还没有读数。
     * 只看位置有效的电表，位置无效要用户重选，刷新解决不了；没有这样的电表就没有可更新的内容。
     */
    suspend fun isStaleAt(now: Instant, maxAge: Duration): Boolean {
        val roomIds = savedMeters.first().map { it.location.meterId }.filter { parseRoom(it) != null }
        if (roomIds.isEmpty()) return false
        return remainingEnergy.isStaleAt(now, maxAge) { energy -> roomIds.all { it in energy } }
    }

    /** 添加 [meter]，或替换 id 相同的电表。 */
    suspend fun saveMeter(meter: ElectricityMeter) {
        store.update { meters ->
            if (meters.any { it.id == meter.id }) {
                meters.map { if (it.id == meter.id) meter else it }
            } else {
                meters + meter
            }
        }
    }

    suspend fun deleteMeter(meter: ElectricityMeter) {
        store.update { meters -> meters.filterNot { it.id == meter.id } }
    }

    /** 电表层级中 [path] 下一级的选项。 */
    suspend fun meterOptions(path: List<MeterOption>): List<MeterOption> =
        session.request(CampusFeature.Electricity) {
            ecard().locations(locationParent(path)).map { MeterOption(it.id, it.name) }
        }

    /** 为 [meter] 充值。调用方被取消时也会执行到底，因为中途放弃只会让扣款结果成谜。 */
    suspend fun recharge(
        meter: ElectricityMeter,
        amountYuan: Int,
        paymentPassword: String,
    ): MeterRechargeResult {
        require(amountYuan > 0)
        return withContext(NonCancellable) {
            try {
                session.request(CampusFeature.Electricity) {
                    val room = RoomId.parse(meter.location.meterId)
                    ecard().rechargeEnergy(paymentPassword, amountYuan, room)
                }
                MeterRechargeResult.Succeeded
            } catch (cause: CampusRequestException) {
                cause.toRechargeResult()
            }
        }
    }

    /** 查询已保存电表的剩余电量。有无效位置时，其余电表照常更新后抛出 [InvalidMeterLocationException]。 */
    suspend fun refresh() = refreshTracker.track {
        val refreshVersion = remainingEnergy.nextRefreshVersion()
        val roomIds = savedMeters.first().map { it.location.meterId }.distinct()
        val rooms = roomIds.mapNotNull { id -> parseRoom(id)?.let { room -> id to room } }.toMap()
        if (rooms.isNotEmpty()) {
            session.request(CampusFeature.Electricity) {
                val card = ecard()
                val energy = coroutineScope {
                    rooms.map { (id, room) -> async { id to card.remainingEnergy(room) } }.awaitAll().toMap()
                }
                remainingEnergy.update(this, refreshVersion) { energy }
            }
        }
        if (rooms.size < roomIds.size) throw InvalidMeterLocationException()
    }
}

/** 目录按 `area`、`area-building`、`area-building--level` 逐级查询，而前三级选项的 id 只是本级编号。 */
internal fun locationParent(path: List<MeterOption>): String {
    val building = path.take(2).joinToString("-") { it.id }
    return path.getOrNull(2)?.let { level -> "$building--${level.id}" } ?: building
}

private fun parseRoom(meterId: String): RoomId? = try {
    RoomId.parse(meterId)
} catch (_: ZzuException.InvalidInput) {
    null
}

/**
 * 只有能排除扣款的失败才算拒绝。SDK 明确警告超时或响应无法解码后服务端可能已受理，
 * 其余情况一律视为结果未知。
 */
private fun CampusRequestException.toRechargeResult(): MeterRechargeResult = when (cause) {
    is ZzuException.Remote,
    is ZzuException.Authentication,
    is ZzuException.InvalidInput,
    is IllegalStateException,
    -> MeterRechargeResult.Rejected(reason)

    else -> MeterRechargeResult.Unconfirmed
}
