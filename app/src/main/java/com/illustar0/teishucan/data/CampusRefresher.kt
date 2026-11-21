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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
 * 不归属任何页面，所以无论应用恢复到哪一页，数据都会加载。
 */
@Singleton
class CampusRefresher @Inject constructor(
    private val session: ZzuSession,
    timetable: TimetableRepository,
    student: StudentRepository,
    electricity: ElectricityRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val pendingFailure = MutableStateFlow<Exception?>(null)
    private val manualRefresh = RefreshQueue(scope) {
        pendingFailure.captureFailure { refreshAll(timetable::refresh, student::refresh, electricity::refresh) }
    }
    private val isAutoRefreshing = MutableStateFlow(false)

    val refreshFailure = pendingFailure.asStateFlow()
    val isRefreshing: StateFlow<Boolean> =
        combine(manualRefresh.isRefreshing, isAutoRefreshing) { isRequested, isAutomatic -> isRequested || isAutomatic }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** 余额随消费常变，电量下降缓慢，课表一学期都很少变动。 */
    private val autoRefreshed = listOf(
        AutoRefreshed(maxAge = 5.minutes, student::isStaleAt, student::refresh),
        AutoRefreshed(maxAge = 30.minutes, electricity::isStaleAt, electricity::refresh),
        AutoRefreshed(maxAge = 12.hours, timetable::isStaleAt, timetable::refresh),
    )

    fun refresh() = manualRefresh.request()

    fun onRefreshFailureShown(failure: Exception) {
        pendingFailure.compareAndSet(failure, null)
    }

    /** 全应用唯一的自动刷新：应用进入前台时，或在前台完成登录时。 */
    fun refreshOnForeground(isForeground: Flow<Boolean>) {
        val isSignedIn = session.account.map { it is AccountState.SignedIn }
        scope.launch {
            combine(isForeground, isSignedIn) { foreground, signedIn -> foreground && signedIn }
                .distinctUntilChanged()
                .filter { it }
                .collect { refreshStale() }
        }
    }

    /** 只更新已经过期的数据。失败不提示：屏幕上仍有上次的数据，想要最新的用户会自己下拉。 */
    private suspend fun refreshStale() {
        val now = Clock.System.now()
        val stale = autoRefreshed.filter { it.isStaleAt(now) }
        if (stale.isEmpty()) return
        isAutoRefreshing.value = true
        try {
            refreshAll(*stale.map { it.refresh }.toTypedArray())
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            // ZzuSession 已经记录了请求失败的日志。
        } finally {
            isAutoRefreshing.value = false
        }
    }
}

private class AutoRefreshed(
    private val maxAge: Duration,
    private val isStale: suspend (now: Instant, maxAge: Duration) -> Boolean,
    val refresh: suspend () -> Unit,
) {
    suspend fun isStaleAt(now: Instant): Boolean = isStale(now, maxAge)
}
