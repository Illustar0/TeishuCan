package com.illustar0.teishucan.data

import com.illustar0.teishucan.concurrency.RefreshTracker
import com.illustar0.teishucan.domain.BigDecimalAsStringSerializer
import com.illustar0.teishucan.domain.CampusFeature
import com.illustar0.teishucan.domain.PaymentChannel
import com.illustar0.teishucan.domain.RechargeOrder
import com.illustar0.zzu.toBigDecimal
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.nullable
import kotlin.time.Duration
import kotlin.time.Instant

/** 校园卡余额与充值，独立维护自身的刷新状态和快照核对时间。 */
@Singleton
class CampusCardRepository @Inject constructor(
    private val session: ZzuSession,
    stores: JsonDataStoreFactory,
) {
    private val balance = stores.createSnapshotStore(
        name = "campus_card_snapshot.json",
        initial = null,
        serializer = BigDecimalAsStringSerializer.nullable,
        session = session.state,
    )
    private val refreshTracker = RefreshTracker()

    val balanceYuan: StateFlow<BigDecimal?> = balance.value
    val isRefreshing: StateFlow<Boolean> = refreshTracker.isRefreshing

    suspend fun isStaleAt(now: Instant, maxAge: Duration): Boolean = balance.isStaleAt(now, maxAge)

    suspend fun refresh() = refreshTracker.track {
        val refreshVersion = balance.nextRefreshVersion()
        session.request(CampusFeature.CampusCard) {
            val amount = ecard().balance().toBigDecimal()
            balance.update(this, refreshVersion) { amount }
        }
    }

    /** 余额可能已在应用外变化，例如在收银台付了款。下次自动刷新时必定更新。 */
    fun expire() {
        balance.expire()
    }

    suspend fun createRechargeOrder(amountYuan: Int): RechargeOrder {
        require(amountYuan > 0)
        return session.request(CampusFeature.CampusCard) {
            val card = ecard()
            val order = card.createRechargeOrder(amountYuan)
            RechargeOrder(
                amountYuan = order.amount,
                channels = card.paymentChannels(order).map { PaymentChannel(it.name, order.checkoutUrlFor(it)) },
            )
        }
    }
}
