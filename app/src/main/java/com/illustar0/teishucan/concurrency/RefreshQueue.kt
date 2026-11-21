package com.illustar0.teishucan.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * 刷新期间到达的请求合并成结束后再跑一次，确保支付后的刷新能发起新查询。
 */
internal class RefreshQueue(scope: CoroutineScope, refresh: suspend () -> Unit) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val running = MutableStateFlow(false)

    val isRefreshing = running.asStateFlow()

    init {
        scope.launch {
            requests.consumeEach {
                running.value = true
                try {
                    refresh()
                } catch (_: CancellationException) {
                    ensureActive()
                } finally {
                    running.value = false
                }
            }
        }
    }

    fun request() {
        requests.trySend(Unit)
    }
}

/** 同时运行全部 [operations]，互不影响；都结束后抛出第一个失败。 */
internal suspend fun refreshAll(vararg operations: suspend () -> Unit): Unit = supervisorScope {
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
