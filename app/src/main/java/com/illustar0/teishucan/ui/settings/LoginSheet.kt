package com.illustar0.teishucan.ui.settings

import android.content.MutableContextWrapper
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.illustar0.teishucan.R
import com.illustar0.teishucan.ui.component.LoadingButton
import com.illustar0.teishucan.ui.component.SheetActionButton
import com.illustar0.teishucan.ui.component.pageSlide
import com.illustar0.teishucan.ui.component.rememberShownContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MfaResendIntervalSeconds = 60
private const val MillisPerSecond = 1000L

/** 为进行中的 [attempt] 收集账号密码，登录要求验证码时切到验证码页；[attempt] 为 null 时面板关闭。 */
@Composable
fun LoginSheet(
    attempt: LoginAttempt?,
    passwordSaves: Flow<CreatePasswordRequest>,
    onSubmitCredentials: () -> Unit,
    onSubmitSavedPassword: (PasswordCredential) -> Unit,
    onSendMfaCode: () -> Unit,
    onVerifyMfa: () -> Unit,
    onLeaveMfa: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val autofillManager = LocalAutofillManager.current
    val activity = LocalActivity.current
    val credentialContext = remember(activity) {
        if (supportsSystemPasswordManager && activity != null) MutableContextWrapper(activity) else null
    }
    val credentialManager = remember(credentialContext) {
        credentialContext?.let { CredentialManager.create(it.applicationContext) }
    }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isChoosingPassword by remember { mutableStateOf(false) }
    if (credentialContext != null && credentialManager != null) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(credentialManager, lifecycle, passwordSaves, autofillManager) {
            passwordSaves.collect { request ->
                lifecycle.withResumed { autofillManager?.cancel() }
                try {
                    credentialManager.createCredential(credentialContext, request)
                } catch (_: CreateCredentialCancellationException) {
                    return@collect
                } catch (cause: CreateCredentialException) {
                    Log.w("TeishuCan.Account", "Could not save password: ${cause.javaClass.simpleName}")
                }
            }
        }
    }

    val sheet = rememberShownContent(attempt)
    val shown = sheet.value ?: return
    val isBusy = shown.isSubmitting || isChoosingPassword

    fun chooseSavedPassword() {
        if (credentialContext == null || credentialManager == null) return
        // 关闭动画期间尝试已经结束，不再为它发起任何操作。
        val target = attempt ?: return
        if (isChoosingPassword || !target.canSubmitCredentials) return
        focusManager.clearFocus()
        target.failure = null
        target.passwordManagerFeedback = null
        isChoosingPassword = true
        scope.launch {
            try {
                val request = GetCredentialRequest(listOf(GetPasswordOption()))
                val password = credentialManager.getCredential(credentialContext, request).credential
                if (password is PasswordCredential) {
                    autofillManager?.cancel()
                    onSubmitSavedPassword(password)
                } else {
                    target.passwordManagerFeedback = PasswordManagerFeedback(
                        messageRes = R.string.login_saved_password_unavailable,
                        isError = true,
                    )
                }
            } catch (cause: GetCredentialException) {
                target.passwordManagerFeedback = when (cause) {
                    is GetCredentialCancellationException -> return@launch
                    is NoCredentialException -> PasswordManagerFeedback(
                        messageRes = R.string.login_no_saved_password,
                        isError = false,
                    )

                    else -> PasswordManagerFeedback(
                        messageRes = R.string.login_saved_password_unavailable,
                        isError = true,
                    )
                }
            } finally {
                isChoosingPassword = false
            }
        }
    }

    val dismissLogin: () -> Unit = {
        autofillManager?.cancel()
        onDismissRequest()
    }

    OverlayBottomSheet(
        show = attempt != null,
        title = stringResource(if (shown.mfa != null) R.string.mfa_title else R.string.settings_login),
        startAction = {
            if (shown.mfa != null) {
                SheetActionButton(
                    icon = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.navigate_back),
                    enabled = !isBusy,
                    onClick = onLeaveMfa,
                )
            } else {
                SheetActionButton(
                    icon = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.electricity_meter_close),
                    enabled = !isBusy,
                    onClick = dismissLogin,
                )
            }
        },
        onDismissRequest = dismissLogin,
        onDismissFinished = sheet.release,
        allowDismiss = !isBusy,
    ) {
        AnimatedContent(
            targetState = shown.mfa,
            modifier = Modifier
                .fillMaxWidth()
                .sensitiveContent()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            transitionSpec = { pageSlide(isForward = targetState != null) },
        ) { mfa ->
            if (mfa != null) {
                MfaCodePage(attempt = shown, mfa = mfa, onSendCode = onSendMfaCode, onVerifyCode = onVerifyMfa)
            } else {
                CredentialsPage(
                    attempt = shown,
                    isChoosingPassword = isChoosingPassword,
                    onChooseSavedPassword = if (credentialManager != null) ::chooseSavedPassword else null,
                    onSubmit = { if (!isChoosingPassword) onSubmitCredentials() },
                )
            }
        }
    }
}

@Composable
private fun CredentialsPage(
    attempt: LoginAttempt,
    isChoosingPassword: Boolean,
    onChooseSavedPassword: (() -> Unit)?,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val isBusy = attempt.isSubmitting || isChoosingPassword
    val isManualLogin = attempt.activeAction == LoginAction.Login
    val isSavedPasswordLogin = attempt.activeAction == LoginAction.SavedPasswordLogin

    Column {
        TextField(
            value = attempt.account,
            onValueChange = { attempt.account = it },
            modifier = Modifier
                .fillMaxWidth()
                .contentType(ContentType.Username),
            label = stringResource(R.string.login_account),
            enabled = !isBusy,
            singleLine = true,
        )
        TextField(
            state = attempt.password,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .contentType(ContentType.Password)
                .semantics { password() },
            label = stringResource(R.string.login_password),
            enabled = !isBusy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            lineLimits = TextFieldLineLimits.SingleLine,
            outputTransformation = PasswordMask,
        )
        LoginFailureText(attempt.failure)
        LoginHelperText(stringResource(R.string.login_credentials_notice))
        LoadingButton(
            text = stringResource(if (isManualLogin) R.string.login_submitting else R.string.settings_login),
            isLoading = isManualLogin,
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            enabled = attempt.account.isNotBlank() && attempt.password.text.isNotEmpty() && !isBusy,
            colors = ButtonDefaults.buttonColorsPrimary(),
        )
        if (onChooseSavedPassword != null) {
            LoadingButton(
                text = stringResource(
                    if (isSavedPasswordLogin) R.string.login_submitting else R.string.login_use_saved_password,
                ),
                isLoading = isChoosingPassword || isSavedPasswordLogin,
                onClick = onChooseSavedPassword,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(),
            )
            PasswordManagerFeedbackText(attempt.passwordManagerFeedback)
        }
    }
}

@Composable
private fun MfaCodePage(
    attempt: LoginAttempt,
    mfa: MfaVerification,
    onSendCode: () -> Unit,
    onVerifyCode: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var resendSecondsLeft by remember { mutableIntStateOf(0) }
    val isCodeRequested = mfa.sentCodeCount > 0
    val canEnterCode = isCodeRequested && !attempt.isSubmitting

    LaunchedEffect(canEnterCode) {
        if (canEnterCode) {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(mfa.sentCodeCount) {
        if (mfa.sentCodeCount == 0) return@LaunchedEffect
        resendSecondsLeft = MfaResendIntervalSeconds
        while (resendSecondsLeft > 0) {
            delay(MillisPerSecond)
            resendSecondsLeft--
        }
    }

    Column {
        TextField(
            value = mfa.code,
            onValueChange = { input -> mfa.code = input.filter(Char::isDigit) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .contentType(ContentType.SmsOtpCode),
            label = stringResource(R.string.mfa_code),
            enabled = canEnterCode,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        LoginHelperText(
            stringResource(if (isCodeRequested) R.string.mfa_code_sent else R.string.mfa_code_destination),
        )
        LoginFailureText(attempt.failure)
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingButton(
                text = when {
                    attempt.activeAction == LoginAction.SendCode -> stringResource(R.string.mfa_sending)
                    !isCodeRequested -> stringResource(R.string.mfa_send)
                    resendSecondsLeft > 0 -> stringResource(R.string.mfa_resend_countdown, resendSecondsLeft)
                    else -> stringResource(R.string.mfa_resend)
                },
                isLoading = attempt.activeAction == LoginAction.SendCode,
                onClick = onSendCode,
                modifier = Modifier.weight(1f),
                enabled = resendSecondsLeft == 0 && !attempt.isSubmitting,
                colors = if (isCodeRequested) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            )
            LoadingButton(
                text = stringResource(
                    if (attempt.activeAction == LoginAction.VerifyCode) R.string.mfa_verifying else R.string.mfa_verify,
                ),
                isLoading = attempt.activeAction == LoginAction.VerifyCode,
                onClick = {
                    focusManager.clearFocus()
                    onVerifyCode()
                },
                modifier = Modifier.weight(1f),
                enabled = canEnterCode && mfa.code.isNotEmpty(),
                colors = if (isCodeRequested) {
                    ButtonDefaults.buttonColorsPrimary()
                } else {
                    ButtonDefaults.buttonColors()
                },
            )
        }
    }
}

@Composable
private fun LoginFailureText(failure: LoginFailure?) {
    if (failure != null) {
        LoginHelperText(
            text = failure.reason ?: stringResource(failure.fallbackMessage),
            color = MiuixTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PasswordManagerFeedbackText(feedback: PasswordManagerFeedback?) {
    if (feedback != null) {
        LoginHelperText(
            text = stringResource(feedback.messageRes),
            color = if (feedback.isError) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

@Composable
private fun LoginHelperText(
    text: String,
    color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
) {
    Text(
        text = text,
        modifier = Modifier.padding(
            PaddingValues(
                start = TextFieldDefaults.InsideMargin.width,
                end = TextFieldDefaults.InsideMargin.width,
                top = 8.dp,
            ),
        ),
        style = MiuixTheme.textStyles.footnote1,
        color = color,
    )
}
