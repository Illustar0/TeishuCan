package com.illustar0.teishucan.ui.component

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.concurrency.captureFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 页面等待在浏览器中打开的地址，由 ViewModel 持有，只在主线程使用。
 *
 * 用状态而不是事件保存：在后台就绪的地址等回到前台再打开，打开后由界面确认。
 * 地址只对发起请求的页面有意义，页面离开时用 [cancel] 连同进行中的请求一起作废，
 * 否则它会在用户回到页面时突然打开，甚至打开上一个账号的地址。
 */
class PendingUrl(private val scope: CoroutineScope) {
    private val pendingFailure = MutableStateFlow<Exception?>(null)
    private val readyUrl = MutableStateFlow<String?>(null)
    private var fetching: Job? = null

    val url: StateFlow<String?> = readyUrl.asStateFlow()
    internal val failure = pendingFailure.asStateFlow()

    internal fun onFailureShown(failure: Exception) {
        pendingFailure.compareAndSet(failure, null)
    }

    /** 在后台用 [fetchUrl] 获取地址。上一次还没结束时忽略，否则连点会打开多个浏览器页面。 */
    fun fetch(fetchUrl: suspend () -> String) {
        if (fetching?.isActive == true) return
        fetching = scope.launch { pendingFailure.captureFailure { readyUrl.value = fetchUrl() } }
    }

    /** 确认 [url] 已处理。这期间又就绪的新地址会保留。 */
    fun consume(url: String) {
        readyUrl.compareAndSet(url, null)
    }

    fun cancel() {
        fetching?.cancel()
        readyUrl.value = null
    }
}

@Composable
fun OpenPendingUrl(pendingUrl: PendingUrl, @StringRes failureMessage: Int) {
    val url by pendingUrl.url.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    FailureToasts(pendingUrl.failure, fallbackMessage = failureMessage, onAcknowledge = pendingUrl::onFailureShown)

    url?.let { readyUrl ->
        LaunchedEffect(readyUrl) {
            if (!uriHandler.tryOpenUri(readyUrl)) {
                Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
            }
            pendingUrl.consume(readyUrl)
        }
    }
    DisposableEffect(pendingUrl) {
        onDispose(pendingUrl::cancel)
    }
}
