package com.illustar0.teishucan.ui.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.R
import com.illustar0.teishucan.concurrency.captureFailure
import com.illustar0.teishucan.data.ZzuSession
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.LoginResult
import com.illustar0.teishucan.domain.campusReason
import com.illustar0.teishucan.ui.component.ActionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class LoginAction { Login, SendCode, VerifyCode }

/** 放在 ViewModel 里，转屏时验证码流程才不会丢。 */
class LoginFormState {
    var isVisible by mutableStateOf(false)
    var account by mutableStateOf("")
    var password by mutableStateOf("")
    var code by mutableStateOf("")
    var isAwaitingMfa by mutableStateOf(false)
    var sentCodeCount by mutableIntStateOf(0)
    var activeAction by mutableStateOf<LoginAction?>(null)
    var failure by mutableStateOf<LoginFailure?>(null)

    val isSubmitting: Boolean
        get() = activeAction != null

    fun leaveMfa() {
        code = ""
        sentCodeCount = 0
        failure = null
        isAwaitingMfa = false
    }
}

data class LoginFailure(@param:StringRes val fallbackMessage: Int, val reason: String?)

@HiltViewModel
class AccountViewModel @Inject constructor(private val session: ZzuSession) : ViewModel() {
    private val outcomes = Channel<ActionOutcome>(Channel.CONFLATED)
    private val pendingLogoutFailure = MutableStateFlow<Exception?>(null)

    val account: StateFlow<AccountState> = session.account
    val loginForm = LoginFormState()
    val loginOutcomes: Flow<ActionOutcome> = outcomes.receiveAsFlow()
    val logoutFailure = pendingLogoutFailure.asStateFlow()

    fun submitCredentials() {
        submit(LoginAction.Login, R.string.login_failed, onFailure = { loginForm.password = "" }) {
            when (session.login(loginForm.account.trim(), loginForm.password)) {
                LoginResult.SignedIn -> finishLogin()
                LoginResult.MfaRequired -> loginForm.isAwaitingMfa = true
            }
        }
    }

    fun sendMfaCode() {
        submit(LoginAction.SendCode, R.string.mfa_send_failed) {
            session.sendMfaCode()
            loginForm.sentCodeCount++
        }
    }

    fun verifyMfa() {
        submit(LoginAction.VerifyCode, R.string.mfa_failed, onFailure = { loginForm.code = "" }) {
            session.verifyMfa(loginForm.code)
            finishLogin()
        }
    }

    fun logout() {
        viewModelScope.launch { pendingLogoutFailure.captureFailure { session.logout() } }
    }

    fun onLogoutFailureShown(failure: Exception) {
        pendingLogoutFailure.compareAndSet(failure, null)
    }

    private fun finishLogin() {
        outcomes.trySend(ActionOutcome.Succeeded)
        loginForm.isVisible = false
    }

    private fun submit(
        action: LoginAction,
        @StringRes failureMessage: Int,
        onFailure: () -> Unit = {},
        step: suspend () -> Unit,
    ) {
        loginForm.failure = null
        loginForm.activeAction = action
        viewModelScope.launch {
            try {
                step()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                Log.w("TeishuCan.Account", "$action failed", cause)
                onFailure()
                loginForm.failure = LoginFailure(failureMessage, cause.campusReason)
                outcomes.trySend(ActionOutcome.Failed)
            } finally {
                loginForm.activeAction = null
            }
        }
    }
}
