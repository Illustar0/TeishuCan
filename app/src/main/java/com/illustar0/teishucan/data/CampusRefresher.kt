package com.illustar0.teishucan.data

import com.illustar0.teishucan.concurrency.RefreshQueue
import com.illustar0.teishucan.concurrency.captureFailure
import com.illustar0.teishucan.concurrency.refreshAll
import com.illustar0.teishucan.di.ApplicationScope
import com.illustar0.teishucan.domain.AccountState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 全应用数据的刷新入口：首页下拉调用 [refresh]，自动刷新由 [refreshOnForeground] 安排。
 *
 * 各数据域独立维护刷新状态与核对时间，互不阻塞，也不归属任何页面，
 * 所以无论应用恢复到哪一页，过期数据都会各自更新。
 */
@Singleton
class CampusRefresher @Inject constructor(
    private val session: ZzuSession,
    timetable: TimetableRepository,
    campusCard: CampusCardRepository,
    electricity: ElectricityRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val pendingFailure = MutableStateFlow<Exception?>(null)
    private val manualRefresh = RefreshQueue(scope) {
        pendingFailure.captureFailure {
            refreshAll(
                timetable::refresh,
                session::refreshProfile,
                campusCard::refresh,
                electricity::refresh,
            )
        }
    }

    /** 余额常变，电量下降缓慢，课表与个人资料很少变动。 */
    private val autoRefreshed = listOf(
        AutoRefreshSource(maxAge = 5.minutes, campusCard.isRefreshing, campusCard::isStaleAt, campusCard::refresh),
        AutoRefreshSource(maxAge = 30.minutes, electricity.isRefreshing, electricity::isStaleAt, electricity::refresh),
        AutoRefreshSource(maxAge = 12.hours, timetable.isRefreshing, timetable::isStaleAt, timetable::refresh),
        AutoRefreshSource(maxAge = 12.hours, session.isProfileRefreshing, session::isProfileStaleAt, session::refreshProfile),
    )

    val refreshFailure = pendingFailure.asStateFlow()
    val isRefreshing: StateFlow<Boolean> = combine(
        listOf(
            manualRefresh.isRefreshing,
            timetable.isRefreshing,
            session.isProfileRefreshing,
            campusCard.isRefreshing,
            electricity.isRefreshing,
        ),
    ) { states -> states.any { it } }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun refresh() = manualRefresh.request()

    fun onRefreshFailureShown(failure: Exception) {
        pendingFailure.compareAndSet(failure, null)
    }

    /** 全应用唯一的自动刷新：应用进入前台时，或在前台完成登录时。 */
    fun refreshOnForeground(isForeground: Flow<Boolean>) {
        val isSignedIn = session.account.map { it is AccountState.SignedIn }
        scope.launch {
            combine(isForeground, isSignedIn) { foreground, signedIn -> foreground to signedIn }
                .distinctUntilChanged()
                .collect { (foreground, signedIn) ->
                    if (!signedIn) {
                        autoRefreshed.forEach(AutoRefreshSource::cancel)
                    } else if (foreground) {
                        refreshStale()
                    }
                }
        }
    }

    /** 只更新已经过期的数据。各自独立并发执行，失败不提示：屏幕上仍有上次的数据，想要最新的用户会自己下拉。 */
    private fun refreshStale() {
        val now = Clock.System.now()
        autoRefreshed.forEach { it.refreshIfStale(scope, now) }
    }
}

/**
 * 单个数据域的自动刷新调度单元，持有独立的在途任务句柄。
 *
 * 某个后端变慢或失败时不会阻塞其他数据域在下次回到前台时立即触发刷新。
 */
internal class AutoRefreshSource(
    private val maxAge: Duration,
    private val isRefreshing: StateFlow<Boolean>,
    private val isStale: suspend (now: Instant, maxAge: Duration) -> Boolean,
    private val refresh: suspend () -> Unit,
) {
    private var inFlight: Job? = null

    fun refreshIfStale(scope: CoroutineScope, now: Instant) {
        if (inFlight?.isActive == true || isRefreshing.value) return
        inFlight = scope.launch {
            if (!isStale(now, maxAge)) return@launch
            try {
                refresh()
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                // ZzuSession 已经记录了请求失败的日志。
            }
        }
    }

    fun cancel() {
        inFlight?.cancel()
        inFlight = null
    }
}
