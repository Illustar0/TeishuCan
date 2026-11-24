package com.illustar0.teishucan.ui.settings

import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.R
import com.illustar0.teishucan.concurrency.RefreshQueue
import com.illustar0.teishucan.concurrency.captureFailure
import com.illustar0.teishucan.concurrency.refreshAll
import com.illustar0.teishucan.data.CampusCardRepository
import com.illustar0.teishucan.data.ElectricityRepository
import com.illustar0.teishucan.domain.ElectricityMeter
import com.illustar0.teishucan.domain.MeterOption
import com.illustar0.teishucan.domain.MeterReading
import com.illustar0.teishucan.domain.MeterRechargeResult
import com.illustar0.teishucan.domain.campusReason
import com.illustar0.teishucan.ui.component.ActionOutcome
import com.illustar0.teishucan.ui.component.FailureToasts
import com.illustar0.teishucan.ui.component.OutcomeHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

data class ElectricityUiState(
    val meters: List<MeterReading>?,
    val campusCardBalanceYuan: BigDecimal?,
)

@HiltViewModel
class ElectricityViewModel @Inject constructor(
    private val electricity: ElectricityRepository,
    campusCard: CampusCardRepository,
) : ViewModel() {
    private val rechargeOutcomeChannel = Channel<ActionOutcome>(Channel.CONFLATED)
    private val pendingFailure = MutableStateFlow<Exception?>(null)

    private val refreshQueue = RefreshQueue(viewModelScope) {
        pendingFailure.captureFailure {
            // 这里的余额只服务于电表充值，没有电表就没有要更新的内容。
            if (electricity.meters.filterNotNull().first().isNotEmpty()) {
                refreshAll(electricity::refresh, campusCard::refresh)
            }
        }
    }
    val isRefreshing: StateFlow<Boolean> = combine(
        refreshQueue.isRefreshing,
        electricity.isRefreshing,
        campusCard.isRefreshing,
    ) { isPageRefreshing, isElectricityRefreshing, isCardRefreshing ->
        isPageRefreshing || isElectricityRefreshing || isCardRefreshing
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        refreshQueue.isRefreshing.value || electricity.isRefreshing.value || campusCard.isRefreshing.value,
    )
    val failure = pendingFailure.asStateFlow()
    val rechargeOutcomes: Flow<ActionOutcome> = rechargeOutcomeChannel.receiveAsFlow()

    val state: StateFlow<ElectricityUiState> = combine(electricity.meters, campusCard.balanceYuan) { meters, balance ->
        ElectricityUiState(meters, balance)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ElectricityUiState(electricity.meters.value, campusCard.balanceYuan.value),
    )

    /** 进行中的电表草稿，只在弹层打开期间存在。 */
    var editor by mutableStateOf<MeterEditorState?>(null)
        private set

    fun openEditor(meter: ElectricityMeter?) {
        editor = MeterEditorState(meter)
    }

    fun dismissEditor() {
        editor = null
    }

    fun refresh() = refreshQueue.request()

    fun onFailureShown(failure: Exception) {
        pendingFailure.compareAndSet(failure, null)
    }

    fun saveMeter() {
        val draft = editor ?: return
        val location = draft.location ?: return
        val meter = ElectricityMeter(
            id = draft.meter?.id ?: UUID.randomUUID().toString(),
            name = draft.name.trim(),
            location = location,
        )
        dismissEditor()
        viewModelScope.launch {
            pendingFailure.captureFailure {
                electricity.saveMeter(meter)
                electricity.refresh()
            }
        }
    }

    fun deleteMeter() {
        val meter = editor?.meter ?: return
        dismissEditor()
        viewModelScope.launch { pendingFailure.captureFailure { electricity.deleteMeter(meter) } }
    }

    /** 把 [path] 下一级的选项加载进当前草稿。已加载或正在加载时不做事，失败后再调用即重试。 */
    fun loadMeterOptions(path: List<MeterOption>) {
        val levelOptions = editor?.levelOptions ?: return
        val current = levelOptions[path]
        if (current != null && current !is MeterOptionsState.Failed) return
        levelOptions[path] = MeterOptionsState.Loading
        viewModelScope.launch {
            levelOptions[path] = try {
                MeterOptionsState.Loaded(electricity.meterOptions(path))
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                MeterOptionsState.Failed(cause.campusReason)
            }
        }
    }

    /** 提交充值。在 ViewModel 作用域内运行，页面重建也能拿到结果；只要可能已扣款就刷新余额。 */
    fun submitRecharge() {
        val meter = editor?.meter ?: return
        val recharge = editor?.recharge ?: return
        val amountYuan = recharge.amountYuan ?: return
        recharge.status = RechargeStatus.Submitting
        viewModelScope.launch {
            val result = electricity.recharge(meter, amountYuan, recharge.paymentPassword.text.toString())
            recharge.paymentPassword.clearText()
            when (result) {
                MeterRechargeResult.Succeeded -> {
                    recharge.status = RechargeStatus.Idle
                    rechargeOutcomeChannel.trySend(ActionOutcome.Succeeded)
                    dismissEditor()
                    refresh()
                }

                is MeterRechargeResult.Rejected -> {
                    recharge.status = RechargeStatus.Rejected(result.reason)
                    rechargeOutcomeChannel.trySend(ActionOutcome.Failed)
                }

                MeterRechargeResult.Unconfirmed -> {
                    recharge.status = RechargeStatus.Unconfirmed
                    rechargeOutcomeChannel.trySend(ActionOutcome.Failed)
                    refresh()
                }
            }
        }
    }
}

@Composable
fun ElectricitySettingsRoute(onBack: () -> Unit) {
    val viewModel = hiltViewModel<ElectricityViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val rechargeSuccessMessage = stringResource(R.string.electricity_recharge_success)

    FailureToasts(viewModel.failure, onAcknowledge = viewModel::onFailureShown)
    OutcomeHaptics(
        outcomes = viewModel.rechargeOutcomes,
        onSucceed = { snackbarHostState.showSnackbar(rechargeSuccessMessage) },
    )

    ElectricitySettingsPage(
        meters = state.meters,
        campusCardBalanceYuan = state.campusCardBalanceYuan,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        editor = viewModel.editor,
        onOpenEditor = viewModel::openEditor,
        onDismissEditor = viewModel::dismissEditor,
        onSaveMeter = viewModel::saveMeter,
        onDeleteMeter = viewModel::deleteMeter,
        onSubmitRecharge = viewModel::submitRecharge,
        onLoadMeterOptions = viewModel::loadMeterOptions,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    )
}
