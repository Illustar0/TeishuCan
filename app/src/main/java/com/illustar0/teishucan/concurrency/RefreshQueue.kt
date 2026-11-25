package com.illustar0.teishucan.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope

/**
 * 刷新期间到达的请求合并成结束后再跑一次，确保支付后的刷新能发起新查询。
 *
 * 请求由调用方协程里的 [run] 处理，刷新活多久由那个协程决定。
 */
internal class RefreshQueue(private val refresh: suspend () -> Unit) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val running = MutableStateFlow(false)

    val isRefreshing = running.asStateFlow()

    /** 处理请求直到被取消。取消时进行中的刷新随之取消，之后到达的请求留给下一次 [run]。 */
    suspend fun run() {
        for (request in requests) {
            running.value = true
            try {
                refresh()
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
            } finally {
                running.value = false
            }
        }
    }

    fun request() {
        requests.trySend(Unit)
    }
}

/** 同时运行全部 [operations]，互不影响；都结束后抛出第一个失败。 */
internal suspend fun refreshAll(operations: List<suspend () -> Unit>): Unit = supervisorScope {
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
