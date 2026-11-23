package com.illustar0.teishucan.ui.settings

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.ClearCredentialException
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val password = TextFieldState()
    var code by mutableStateOf("")
    var isAwaitingMfa by mutableStateOf(false)
    var sentCodeCount by mutableIntStateOf(0)
    var activeAction by mutableStateOf<LoginAction?>(null)
    var failure by mutableStateOf<LoginFailure?>(null)
    internal var passwordSaveAfterMfa: CreatePasswordRequest? = null

    val isSubmitting: Boolean
        get() = activeAction != null

    internal val canSubmitCredentials: Boolean
        get() = isVisible && !isSubmitting && !isAwaitingMfa

    fun leaveMfa() {
        code = ""
        sentCodeCount = 0
        failure = null
        passwordSaveAfterMfa = null
        isAwaitingMfa = false
    }
}

data class LoginFailure(@param:StringRes val fallbackMessage: Int, val reason: String?)

internal val supportsSystemPasswordManager: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val session: ZzuSession,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val outcomes = Channel<ActionOutcome>(Channel.CONFLATED)
    private val passwordSaveRequests = Channel<CreatePasswordRequest>(Channel.CONFLATED)
    private val pendingLogoutFailure = MutableStateFlow<Exception?>(null)

    val account: StateFlow<AccountState> = session.account
    val loginForm = LoginFormState()
    val loginOutcomes: Flow<ActionOutcome> = outcomes.receiveAsFlow()
    val passwordSaves: Flow<CreatePasswordRequest> = passwordSaveRequests.receiveAsFlow()
    val logoutFailure = pendingLogoutFailure.asStateFlow()

    fun submitCredentials() {
        authenticate(loginForm.account.trim(), loginForm.password.text.toString(), supportsSystemPasswordManager)
    }

    fun submitSavedPassword(credential: PasswordCredential) {
        if (!loginForm.canSubmitCredentials) return
        loginForm.password.clearText()
        authenticate(credential.id.trim(), credential.password, shouldOfferPasswordSave = false)
    }

    private fun authenticate(account: String, password: String, shouldOfferPasswordSave: Boolean) {
        if (!loginForm.canSubmitCredentials) return
        if (account.isEmpty() || password.isEmpty()) {
            loginForm.failure = LoginFailure(R.string.login_failed, reason = null)
            return
        }
        loginForm.account = account
        submit(LoginAction.Login, R.string.login_failed, onFailure = { loginForm.password.clearText() }) {
            val passwordSave = if (shouldOfferPasswordSave) CreatePasswordRequest(account, password) else null
            when (session.login(account, password)) {
                LoginResult.SignedIn -> finishLogin(passwordSave)
                LoginResult.MfaRequired -> {
                    loginForm.passwordSaveAfterMfa = passwordSave
                    loginForm.isAwaitingMfa = true
                }
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
            finishLogin(loginForm.passwordSaveAfterMfa)
            loginForm.passwordSaveAfterMfa = null
        }
    }

    fun logout() {
        viewModelScope.launch {
            pendingLogoutFailure.captureFailure {
                session.logout()
                passwordSaveRequests.tryReceive()
                if (supportsSystemPasswordManager) {
                    try {
                        CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
                    } catch (cause: ClearCredentialException) {
                        Log.w("TeishuCan.Account", "Could not clear credential state: ${cause.javaClass.simpleName}")
                    }
                }
            }
        }
    }

    fun onLogoutFailureShown(failure: Exception) {
        pendingLogoutFailure.compareAndSet(failure, null)
    }

    private fun finishLogin(passwordSave: CreatePasswordRequest?) {
        outcomes.trySend(ActionOutcome.Succeeded)
        loginForm.isVisible = false
        if (passwordSave != null) {
            passwordSaveRequests.trySend(passwordSave)
        }
    }

    private fun submit(
        action: LoginAction,
        @StringRes failureMessage: Int,
        onFailure: () -> Unit = {},
        step: suspend () -> Unit,
    ) {
        if (!loginForm.isVisible || loginForm.isSubmitting) return
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
