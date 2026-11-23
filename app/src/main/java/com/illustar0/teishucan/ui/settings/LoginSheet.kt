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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MfaResendIntervalSeconds = 60
private const val MillisPerSecond = 1000L

/** 收集账号密码；登录要求验证码时切到验证码页。 */
@Composable
fun LoginSheet(
    form: LoginFormState,
    passwordSaves: Flow<CreatePasswordRequest>,
    onSubmitCredentials: () -> Unit,
    onSubmitSavedPassword: (PasswordCredential) -> Unit,
    onSendMfaCode: () -> Unit,
    onVerifyMfa: () -> Unit,
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
    val isBusy = form.isSubmitting || isChoosingPassword
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

    fun chooseSavedPassword() {
        if (credentialContext == null || credentialManager == null) return
        if (isChoosingPassword || !form.canSubmitCredentials) return
        focusManager.clearFocus()
        form.failure = null
        isChoosingPassword = true
        scope.launch {
            try {
                val request = GetCredentialRequest(listOf(GetPasswordOption()))
                val password = credentialManager.getCredential(credentialContext, request).credential
                if (password is PasswordCredential) {
                    autofillManager?.cancel()
                    onSubmitSavedPassword(password)
                } else {
                    form.failure = LoginFailure(R.string.login_saved_password_unavailable, reason = null)
                }
            } catch (cause: GetCredentialException) {
                val message = when (cause) {
                    is GetCredentialCancellationException -> return@launch
                    is NoCredentialException -> R.string.login_no_saved_password
                    else -> R.string.login_saved_password_unavailable
                }
                form.failure = LoginFailure(message, reason = null)
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
        show = form.isVisible,
        title = stringResource(if (form.isAwaitingMfa) R.string.mfa_title else R.string.settings_login),
        startAction = {
            if (form.isAwaitingMfa) {
                SheetActionButton(
                    icon = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.navigate_back),
                    enabled = !isBusy,
                    onClick = form::leaveMfa,
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
        onDismissFinished = {
            form.password.clearText()
            form.leaveMfa()
        },
        allowDismiss = !isBusy,
    ) {
        AnimatedContent(
            targetState = form.isAwaitingMfa,
            modifier = Modifier
                .fillMaxWidth()
                .sensitiveContent()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            transitionSpec = { pageSlide(isForward = targetState) },
        ) { isAwaitingMfa ->
            if (isAwaitingMfa) {
                MfaCodePage(form = form, onSendCode = onSendMfaCode, onVerifyCode = onVerifyMfa)
            } else {
                CredentialsPage(
                    form = form,
                    isBusy = isBusy,
                    onChooseSavedPassword = if (credentialManager != null) ::chooseSavedPassword else null,
                    onSubmit = { if (!isChoosingPassword) onSubmitCredentials() },
                )
            }
        }
    }
}

@Composable
private fun CredentialsPage(
    form: LoginFormState,
    isBusy: Boolean,
    onChooseSavedPassword: (() -> Unit)?,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column {
        TextField(
            value = form.account,
            onValueChange = { form.account = it },
            modifier = Modifier
                .fillMaxWidth()
                .contentType(ContentType.Username),
            label = stringResource(R.string.login_account),
            enabled = !isBusy,
            singleLine = true,
        )
        TextField(
            state = form.password,
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
        LoginFailureText(form.failure)
        LoginHelperText(stringResource(R.string.login_credentials_notice))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onChooseSavedPassword != null) {
                TextButton(
                    text = stringResource(R.string.login_use_saved_password),
                    onClick = onChooseSavedPassword,
                    modifier = Modifier.weight(1f),
                    enabled = !isBusy,
                )
            }
            LoadingButton(
                text = stringResource(if (form.isSubmitting) R.string.login_submitting else R.string.settings_login),
                isLoading = form.isSubmitting,
                onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                },
                modifier = Modifier.weight(1f),
                enabled = form.account.isNotBlank() && form.password.text.isNotEmpty() && !isBusy,
                colors = ButtonDefaults.buttonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun MfaCodePage(
    form: LoginFormState,
    onSendCode: () -> Unit,
    onVerifyCode: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var resendSecondsLeft by remember { mutableIntStateOf(0) }
    val isCodeRequested = form.sentCodeCount > 0
    val canEnterCode = isCodeRequested && !form.isSubmitting

    LaunchedEffect(canEnterCode) {
        if (canEnterCode) {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(form.sentCodeCount) {
        if (form.sentCodeCount == 0) return@LaunchedEffect
        resendSecondsLeft = MfaResendIntervalSeconds
        while (resendSecondsLeft > 0) {
            delay(MillisPerSecond)
            resendSecondsLeft--
        }
    }

    Column {
        TextField(
            value = form.code,
            onValueChange = { input -> form.code = input.filter(Char::isDigit) },
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
        LoginFailureText(form.failure)
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingButton(
                text = when {
                    form.activeAction == LoginAction.SendCode -> stringResource(R.string.mfa_sending)
                    !isCodeRequested -> stringResource(R.string.mfa_send)
                    resendSecondsLeft > 0 -> stringResource(R.string.mfa_resend_countdown, resendSecondsLeft)
                    else -> stringResource(R.string.mfa_resend)
                },
                isLoading = form.activeAction == LoginAction.SendCode,
                onClick = onSendCode,
                modifier = Modifier.weight(1f),
                enabled = resendSecondsLeft == 0 && !form.isSubmitting,
                colors = if (isCodeRequested) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            )
            LoadingButton(
                text = stringResource(
                    if (form.activeAction == LoginAction.VerifyCode) R.string.mfa_verifying else R.string.mfa_verify,
                ),
                isLoading = form.activeAction == LoginAction.VerifyCode,
                onClick = {
                    focusManager.clearFocus()
                    onVerifyCode()
                },
                modifier = Modifier.weight(1f),
                enabled = canEnterCode && form.code.isNotEmpty(),
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
