package com.illustar0.chibacan.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * 在 [scope] 中运行的后台任务及其状态，寿命与 [scope] 相同，因此不受页面重建影响。
 *
 * 刷新期间到达的刷新请求会合并成结束后再跑一次，而不是被丢弃，
 * 否则支付后的刷新可能撞上一次早已开始的刷新，留下过期的余额。
 */
class BackgroundTasks(private val scope: CoroutineScope, private val refreshContent: suspend () -> Unit = {}) {
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val latestFailure = MutableStateFlow<Exception?>(null)
    private val running = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = running.asStateFlow()

    /**
     * 尚未告知用户的最新失败，没有时为 null。
     *
     * 用状态而不是事件保存：只有界面处于前台时才会读到，提示后由 [consumeFailure] 确认。
     * 新的失败覆盖旧的，用户回到前台时不会看到一连串过期的提示。
     */
    val failure: StateFlow<Exception?> = latestFailure.asStateFlow()

    init {
        scope.launch {
            refreshRequests.receiveAsFlow().collect {
                running.value = true
                reportingFailure(refreshContent)
                running.value = false
            }
        }
    }

    fun refresh() {
        refreshRequests.trySend(Unit)
    }

    /** 在后台运行 [task]，失败时记入 [failure]。 */
    fun launch(task: suspend () -> Unit): Job = scope.launch { reportingFailure(task) }

    /** 确认 [failure] 已告知用户。这期间又发生的新失败会保留。 */
    fun consumeFailure(failure: Exception) {
        latestFailure.compareAndSet(failure, null)
    }

    private suspend fun reportingFailure(task: suspend () -> Unit) {
        try {
            task()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            latestFailure.value = cause
        }
    }
}

/** 同时运行全部 [operations]，互不影响；都结束后抛出第一个失败。 */
suspend fun refreshAll(vararg operations: suspend () -> Unit): Unit = supervisorScope {
    val failures = operations.map { operation ->
        async {
            try {
                operation()
                null
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                cause
            }
        }
    }.awaitAll().filterNotNull()
    failures.firstOrNull()?.let { throw it }
}
