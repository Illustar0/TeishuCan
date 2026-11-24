package com.illustar0.teishucan.concurrency

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 追踪单个数据源当前是否有进行中的刷新。
 *
 * 用活跃计数而不是布尔值，重叠的自动刷新与手动刷新全部结束后才会回到 false。
 */
internal class RefreshTracker {
    private var activeCount = 0
    private val refreshing = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    suspend fun <T> track(block: suspend () -> T): T {
        synchronized(this) {
            activeCount++
            refreshing.value = true
        }
        try {
            return block()
        } finally {
            synchronized(this) {
                activeCount--
                refreshing.value = activeCount > 0
            }
        }
    }
}
