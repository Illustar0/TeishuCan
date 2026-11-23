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

enum class LoginAction { Login, SavedPasswordLogin, SendCode, VerifyCode }

/**
 * 一次登录尝试，从打开登录面板到面板关闭。
 *
 * 每次打开都是新实例，结束后整体丢弃，因此不必逐项清理，下一次也看不到这一次的任何输入。
 * 由 ViewModel 持有，转屏时验证码流程才不会丢。
 */
class LoginAttempt {
    var account by mutableStateOf("")
    val password = TextFieldState()
    var activeAction by mutableStateOf<LoginAction?>(null)
    var failure by mutableStateOf<LoginFailure?>(null)
    var passwordManagerFeedback by mutableStateOf<PasswordManagerFeedback?>(null)

    /** 等待短信验证时不为 null，回到账号密码页就丢弃。 */
    var mfa by mutableStateOf<MfaVerification?>(null)

    val isSubmitting: Boolean
        get() = activeAction != null

    internal val canSubmitCredentials: Boolean
        get() = !isSubmitting && mfa == null
}

/** 登录尝试里的短信验证一步。[passwordSave] 等验证通过后才交给系统密码管理器。 */
class MfaVerification(internal val passwordSave: CreatePasswordRequest?) {
    var code by mutableStateOf("")
    var sentCodeCount by mutableIntStateOf(0)
}

data class LoginFailure(@param:StringRes val fallbackMessage: Int, val reason: String?)

data class PasswordManagerFeedback(@param:StringRes val messageRes: Int, val isError: Boolean)

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
    val loginOutcomes: Flow<ActionOutcome> = outcomes.receiveAsFlow()
    val passwordSaves: Flow<CreatePasswordRequest> = passwordSaveRequests.receiveAsFlow()
    val logoutFailure = pendingLogoutFailure.asStateFlow()

    /** 进行中的登录尝试，只在登录面板打开期间存在。 */
    var login by mutableStateOf<LoginAttempt?>(null)
        private set

    fun startLogin() {
        if (login == null) login = LoginAttempt()
    }

    /** 用户关闭登录面板，尝试就此结束，服务端那次未完成的登录也一并放弃。 */
    fun cancelLogin() {
        val attempt = login ?: return
        if (attempt.isSubmitting) return
        login = null
        session.abandonLogin()
    }

    fun submitCredentials() {
        val attempt = login ?: return
        authenticate(attempt, attempt.account.trim(), attempt.password.text.toString(), LoginAction.Login)
    }

    fun submitSavedPassword(credential: PasswordCredential) {
        val attempt = login ?: return
        if (!attempt.canSubmitCredentials) return
        attempt.password.clearText()
        authenticate(attempt, credential.id.trim(), credential.password, LoginAction.SavedPasswordLogin)
    }

    private fun authenticate(
        attempt: LoginAttempt,
        account: String,
        password: String,
        action: LoginAction,
    ) {
        if (!attempt.canSubmitCredentials) return
        if (account.isEmpty() || password.isEmpty()) {
            attempt.failure = LoginFailure(R.string.login_failed, reason = null)
            return
        }
        attempt.account = account
        val shouldOfferPasswordSave = action == LoginAction.Login && supportsSystemPasswordManager
        submit(attempt, action, R.string.login_failed, onFailure = { attempt.password.clearText() }) {
            val passwordSave = if (shouldOfferPasswordSave) CreatePasswordRequest(account, password) else null
            when (session.login(account, password)) {
                LoginResult.SignedIn -> finishLogin(passwordSave)
                LoginResult.MfaRequired -> attempt.mfa = MfaVerification(passwordSave)
            }
        }
    }

    fun sendMfaCode() {
        val attempt = login ?: return
        val mfa = attempt.mfa ?: return
        submit(attempt, LoginAction.SendCode, R.string.mfa_send_failed) {
            session.sendMfaCode()
            mfa.sentCodeCount++
        }
    }

    fun verifyMfa() {
        val attempt = login ?: return
        val mfa = attempt.mfa ?: return
        submit(attempt, LoginAction.VerifyCode, R.string.mfa_failed, onFailure = { mfa.code = "" }) {
            session.verifyMfa(mfa.code)
            finishLogin(mfa.passwordSave)
        }
    }

    /** 从验证码页回到账号密码页。等待验证的那次登录随之放弃，重新提交会开始新的一次。 */
    fun leaveMfa() {
        val attempt = login ?: return
        if (attempt.mfa == null || attempt.isSubmitting) return
        attempt.mfa = null
        attempt.failure = null
        session.abandonLogin()
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

    override fun onCleared() {
        // 尝试活不过持有它的界面，界面销毁时服务端那次未完成的登录也不该留下。
        session.abandonLogin()
    }

    private fun finishLogin(passwordSave: CreatePasswordRequest?) {
        outcomes.trySend(ActionOutcome.Succeeded)
        login = null
        if (passwordSave != null) {
            passwordSaveRequests.trySend(passwordSave)
        }
    }

    private fun submit(
        attempt: LoginAttempt,
        action: LoginAction,
        @StringRes failureMessage: Int,
        onFailure: () -> Unit = {},
        step: suspend () -> Unit,
    ) {
        if (attempt.isSubmitting) return
        attempt.failure = null
        attempt.passwordManagerFeedback = null
        attempt.activeAction = action
        viewModelScope.launch {
            try {
                step()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                Log.w("TeishuCan.Account", "$action failed", cause)
                onFailure()
                attempt.failure = LoginFailure(failureMessage, cause.campusReason)
                outcomes.trySend(ActionOutcome.Failed)
            } finally {
                attempt.activeAction = null
            }
        }
    }
}
