package com.illustar0.teishucan.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.data.StudentRepository
import com.illustar0.teishucan.data.ZzuSession
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.RechargeOrder
import com.illustar0.teishucan.domain.campusReason
import com.illustar0.teishucan.ui.component.ActionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 一次校园卡充值，从打开充值面板到关闭。每次打开都是新实例，结束后整体丢弃，下一次看不到这一次的金额与订单。 */
class CampusCardRechargeDraft {
    var amount by mutableStateOf("")
    var order by mutableStateOf<RechargeOrder?>(null)
    var isSubmitting by mutableStateOf(false)
    var hasFailed by mutableStateOf(false)
    var failureReason by mutableStateOf<String?>(null)

    val amountYuan: Int?
        get() = amount.toIntOrNull()?.takeIf { it > 0 }
}

@HiltViewModel
class CampusCardRechargeViewModel @Inject constructor(
    session: ZzuSession,
    private val student: StudentRepository,
) : ViewModel() {
    private val outcomeChannel = Channel<ActionOutcome>(Channel.CONFLATED)

    /** 进行中的充值，只在充值面板打开期间存在。 */
    var draft by mutableStateOf<CampusCardRechargeDraft?>(null)
        private set
    val outcomes: Flow<ActionOutcome> = outcomeChannel.receiveAsFlow()

    init {
        // 订单属于这次登录。登录因请求失败而结束时首页会直接换掉，面板来不及关闭，要在这里作废。
        viewModelScope.launch {
            session.account
                .filter { it == AccountState.SignedOut }
                .collect { draft = null }
        }
    }

    fun open() {
        if (draft == null) draft = CampusCardRechargeDraft()
    }

    fun dismiss() {
        draft = null
    }

    fun submitAmount() {
        val recharge = draft ?: return
        val amountYuan = recharge.amountYuan ?: return
        recharge.isSubmitting = true
        recharge.hasFailed = false
        viewModelScope.launch {
            try {
                recharge.order = student.createRechargeOrder(amountYuan)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                recharge.failureReason = cause.campusReason
                fail(recharge)
            } finally {
                recharge.isSubmitting = false
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
        draft?.let(::fail)
    }

    private fun fail(recharge: CampusCardRechargeDraft) {
        recharge.hasFailed = true
        outcomeChannel.trySend(ActionOutcome.Failed)
    }
}
