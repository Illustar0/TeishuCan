package com.illustar0.teishucan.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.PaymentChannel
import com.illustar0.teishucan.domain.RechargeOrder
import com.illustar0.teishucan.ui.component.LoadingButton
import com.illustar0.teishucan.ui.component.SheetActionButton
import com.illustar0.teishucan.ui.component.formatYuan
import com.illustar0.teishucan.ui.component.pageSlide
import com.illustar0.teishucan.ui.component.rechargeAmountInput
import com.illustar0.teishucan.ui.component.rememberShownContent
import com.illustar0.teishucan.ui.component.tryOpenUri
import java.math.BigDecimal
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 为进行中的 [draft] 选择金额并创建订单，再选择付款渠道；[draft] 为 null 时面板关闭。 */
@Composable
fun CampusCardRechargeSheet(
    draft: CampusCardRechargeDraft?,
    campusCardBalanceYuan: BigDecimal?,
    onSubmitAmount: () -> Unit,
    onCheckoutOpen: () -> Unit,
    onCheckoutFail: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val sheet = rememberShownContent(draft)
    val shown = sheet.value ?: return

    OverlayBottomSheet(
        show = draft != null,
        title = stringResource(
            if (shown.order == null) R.string.electricity_recharge else R.string.campus_card_recharge_channel_title,
        ),
        startAction = {
            if (shown.order == null) {
                SheetActionButton(
                    icon = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.electricity_meter_close),
                    enabled = !shown.isSubmitting,
                    onClick = onDismissRequest,
                )
            } else {
                SheetActionButton(
                    icon = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.navigate_back),
                    onClick = {
                        shown.hasFailed = false
                        shown.order = null
                    },
                )
            }
        },
        onDismissRequest = onDismissRequest,
        onDismissFinished = sheet.release,
        allowDismiss = !shown.isSubmitting,
    ) {
        AnimatedContent(
            targetState = shown.order,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            transitionSpec = { pageSlide(isForward = targetState != null) },
        ) { order ->
            if (order == null) {
                RechargeAmountPage(
                    state = shown,
                    campusCardBalanceYuan = campusCardBalanceYuan,
                    onSubmit = onSubmitAmount,
                )
            } else {
                PaymentChannelPage(
                    order = order,
                    hasFailed = shown.hasFailed,
                    onChannelSelect = { channel ->
                        if (uriHandler.tryOpenUri(channel.checkoutUrl)) onCheckoutOpen() else onCheckoutFail()
                    },
                )
            }
        }
    }
}

@Composable
private fun RechargeAmountPage(
    state: CampusCardRechargeDraft,
    campusCardBalanceYuan: BigDecimal?,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column {
        TextField(
            value = state.amount,
            onValueChange = { input -> state.amount = rechargeAmountInput(input) },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.electricity_recharge_amount),
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        RechargeHelperText(
            stringResource(
                R.string.electricity_recharge_balance,
                campusCardBalanceYuan?.let(::formatYuan) ?: stringResource(R.string.home_not_fetched),
            ),
        )
        if (state.hasFailed) {
            RechargeFailureText(state.failureReason)
        }
        RechargeHelperText(stringResource(R.string.campus_card_recharge_notice))
        LoadingButton(
            text = stringResource(
                if (state.isSubmitting) {
                    R.string.electricity_recharge_submitting
                } else {
                    R.string.campus_card_recharge_next
                },
            ),
            isLoading = state.isSubmitting,
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            enabled = state.amountYuan != null && !state.isSubmitting,
        )
    }
}

@Composable
private fun PaymentChannelPage(
    order: RechargeOrder,
    hasFailed: Boolean,
    onChannelSelect: (PaymentChannel) -> Unit,
) {
    Column {
        Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
            BasicComponent(
                title = stringResource(R.string.campus_card_recharge_amount),
                endActions = {
                    Text(
                        text = formatYuan(order.amountYuan),
                        style = MiuixTheme.textStyles.headline2,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                },
            )
        }
        Card(
            modifier = Modifier.padding(top = 12.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        ) {
            order.channels.forEach { channel ->
                ArrowPreference(
                    title = channel.name,
                    onClick = { onChannelSelect(channel) },
                )
            }
        }
        if (hasFailed) {
            RechargeFailureText(reason = null)
        }
    }
}

private val RechargeHelperTextPadding: PaddingValues
    get() = PaddingValues(
        start = TextFieldDefaults.InsideMargin.width,
        end = TextFieldDefaults.InsideMargin.width,
        top = 8.dp,
    )

@Composable
private fun RechargeHelperText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(RechargeHelperTextPadding),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun RechargeFailureText(reason: String?) {
    Text(
        text = reason ?: stringResource(R.string.campus_card_recharge_failed),
        modifier = Modifier.padding(RechargeHelperTextPadding),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.error,
    )
}
