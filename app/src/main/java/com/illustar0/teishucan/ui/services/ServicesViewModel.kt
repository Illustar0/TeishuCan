package com.illustar0.teishucan.ui.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.R
import com.illustar0.teishucan.data.CampusServiceRepository
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.ui.component.OpenPendingUrl
import com.illustar0.teishucan.ui.component.PendingUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 协调服务条目的打开动作：外部网页走 [serviceUrl] 异步换取单点登录地址，
 * 切往原生页面时立即作废在途的网页请求，避免旧地址随后在原生页面上弹出浏览器。
 */
internal class ServiceOpener(
    scope: CoroutineScope,
    private val fetchLoginUrl: suspend (CampusService) -> String,
) {
    private val openingServiceFlow = MutableStateFlow<CampusService?>(null)

    val serviceUrl = PendingUrl(scope)

    /** 正在获取地址的服务，没有时为 null。 */
    val openingService: StateFlow<CampusService?> = openingServiceFlow.asStateFlow()

    fun open(target: ServiceTarget, onOpenNative: (NativeService) -> Unit) {
        when (target) {
            is ServiceTarget.Web -> serviceUrl.fetch {
                openingServiceFlow.value = target.service
                try {
                    fetchLoginUrl(target.service)
                } finally {
                    openingServiceFlow.compareAndSet(target.service, null)
                }
            }

            is ServiceTarget.Native -> {
                serviceUrl.cancel()
                openingServiceFlow.value = null
                onOpenNative(target.service)
            }
        }
    }
}

@HiltViewModel
class ServicesViewModel @Inject constructor(
    services: CampusServiceRepository,
) : ViewModel() {
    private val opener = ServiceOpener(viewModelScope, services::loginUrl)

    val serviceUrl: PendingUrl
        get() = opener.serviceUrl

    /** 正在获取地址的服务，没有时为 null。 */
    val openingService: StateFlow<CampusService?>
        get() = opener.openingService

    fun open(target: ServiceTarget, onOpenNative: (NativeService) -> Unit) {
        opener.open(target, onOpenNative)
    }
}

@Composable
fun ServicesRoute(
    onOpenNative: (NativeService) -> Unit,
    bottomPadding: Dp,
    onSearchActiveChange: (Boolean) -> Unit = {},
) {
    val viewModel = hiltViewModel<ServicesViewModel>()
    val openingService by viewModel.openingService.collectAsStateWithLifecycle()

    OpenPendingUrl(viewModel.serviceUrl, failureMessage = R.string.service_open_failed)

    ServicesPage(
        openingService = openingService,
        onOpen = { target -> viewModel.open(target, onOpenNative) },
        bottomPadding = bottomPadding,
        onSearchActiveChange = onSearchActiveChange,
    )
}

@Composable
fun UndergradEasRoute(
    onOpenNative: (NativeService) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<ServicesViewModel>()
    val openingService by viewModel.openingService.collectAsStateWithLifecycle()

    OpenPendingUrl(viewModel.serviceUrl, failureMessage = R.string.service_open_failed)

    UndergradEasPage(
        openingService = openingService,
        onOpen = { target -> viewModel.open(target, onOpenNative) },
        onBack = onBack,
    )
}
