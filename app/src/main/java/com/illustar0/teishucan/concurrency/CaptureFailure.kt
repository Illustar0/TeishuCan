package com.illustar0.teishucan.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

internal suspend fun MutableStateFlow<Exception?>.captureFailure(operation: suspend () -> Unit) {
    try {
        operation()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        value = cause
    }
}
