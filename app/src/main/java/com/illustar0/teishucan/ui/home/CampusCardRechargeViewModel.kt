package com.illustar0.teishucan.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.data.StudentRepository
import com.illustar0.teishucan.domain.RechargeOrder
import com.illustar0.teishucan.domain.campusReason
import com.illustar0.teishucan.ui.component.ActionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 一次打开校园卡充值弹层时的草稿。 */
class CampusCardRechargeState {
    var isVisible by mutableStateOf(false)
    var amount by mutableStateOf("")
    var order by mutableStateOf<RechargeOrder?>(null)
    var isSubmitting by mutableStateOf(false)
    var hasFailed by mutableStateOf(false)

    /** 校园服务给出的失败原因，没有时为 null。 */
    var failureReason by mutableStateOf<String?>(null)

    val amountYuan: Int?
        get() = amount.toIntOrNull()?.takeIf { it > 0 }
}

@HiltViewModel
class CampusCardRechargeViewModel @Inject constructor(
    private val student: StudentRepository,
) : ViewModel() {
    private val outcomeChannel = Channel<ActionOutcome>(Channel.CONFLATED)

    var state by mutableStateOf(CampusCardRechargeState())
        private set
    val outcomes: Flow<ActionOutcome> = outcomeChannel.receiveAsFlow()

    fun open() {
        state.isVisible = true
    }

    fun dismiss() {
        state.isVisible = false
    }

    /** 弹层完全收起后丢弃草稿。 */
    fun reset() {
        state = CampusCardRechargeState()
    }

    fun submitAmount() {
        val draft = state
        val amountYuan = draft.amountYuan ?: return
        draft.isSubmitting = true
        draft.hasFailed = false
        viewModelScope.launch {
            try {
                draft.order = student.createRechargeOrder(amountYuan)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                draft.failureReason = cause.campusReason
                failCheckout()
            } finally {
                draft.isSubmitting = false
            }
        }
    }

    /** 收银台已在浏览器中打开。支付结果要等回到应用时靠刷新得知，所以让余额届时必定刷新。 */
    fun finishCheckout() {
        student.expire()
        outcomeChannel.trySend(ActionOutcome.Succeeded)
        dismiss()
    }

    fun failCheckout() {
        state.hasFailed = true
        outcomeChannel.trySend(ActionOutcome.Failed)
    }
}
