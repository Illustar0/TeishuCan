package com.illustar0.teishucan.ui.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.R
import com.illustar0.teishucan.data.StudentRepository
import com.illustar0.teishucan.domain.CampusService
import com.illustar0.teishucan.ui.component.OpenPendingUrl
import com.illustar0.teishucan.ui.component.PendingUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val student: StudentRepository,
) : ViewModel() {
    private val openingServiceFlow = MutableStateFlow<CampusService?>(null)

    val serviceUrl = PendingUrl(viewModelScope)

    /** 正在获取地址的服务，没有时为 null。 */
    val openingService: StateFlow<CampusService?> = openingServiceFlow.asStateFlow()

    fun open(service: CampusService) {
        serviceUrl.fetch {
            openingServiceFlow.value = service
            try {
                student.serviceLoginUrl(service)
            } finally {
                openingServiceFlow.value = null
            }
        }
    }
}

@Composable
fun ServicesRoute(onOpenUndergradEas: () -> Unit, bottomPadding: Dp) {
    val viewModel = hiltViewModel<ServicesViewModel>()
    val openingService by viewModel.openingService.collectAsStateWithLifecycle()

    OpenPendingUrl(viewModel.serviceUrl, failureMessage = R.string.service_open_failed)

    ServicesPage(
        openingService = openingService,
        onOpenService = viewModel::open,
        onOpenUndergradEas = onOpenUndergradEas,
        bottomPadding = bottomPadding,
    )
}

@Composable
fun UndergradEasRoute(onBack: () -> Unit) {
    val viewModel = hiltViewModel<ServicesViewModel>()
    val openingService by viewModel.openingService.collectAsStateWithLifecycle()

    OpenPendingUrl(viewModel.serviceUrl, failureMessage = R.string.service_open_failed)

    UndergradEasPage(
        openingService = openingService,
        onOpenService = viewModel::open,
        onBack = onBack,
    )
}
