package com.illustar0.teishucan.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 最近一次等待界面提示的失败。
 *
 * 用状态而不是事件保存：界面不在前台时发生的失败，等回到前台再提示。界面提示后用 [acknowledge] 确认，
 * 这期间又发生的新失败会保留。
 */
class PendingFailure {
    private val pending = MutableStateFlow<Exception?>(null)

    /** 待提示的失败，没有时为 null。 */
    val failure: StateFlow<Exception?> = pending.asStateFlow()

    /** 运行 [operation]，把它抛出的异常记为待提示的失败。取消照常向上传播。 */
    suspend fun capture(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            pending.value = cause
        }
    }

    /** 确认 [failure] 已经提示过。 */
    fun acknowledge(failure: Exception) {
        pending.compareAndSet(failure, null)
    }

    /** 丢弃待提示的失败，例如它所属的登录已经结束。 */
    fun clear() {
        pending.value = null
    }
}
