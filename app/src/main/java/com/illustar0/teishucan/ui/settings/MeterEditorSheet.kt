package com.illustar0.teishucan.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ElectricityMeter
import com.illustar0.teishucan.domain.MeterLocation
import com.illustar0.teishucan.domain.MeterOption
import com.illustar0.teishucan.domain.MeterReading
import com.illustar0.teishucan.ui.component.LoadingButton
import com.illustar0.teishucan.ui.component.RechargeAmountInput
import com.illustar0.teishucan.ui.component.SheetActionButton
import com.illustar0.teishucan.ui.component.formatYuan
import com.illustar0.teishucan.ui.component.pageSlide
import com.illustar0.teishucan.ui.component.rememberShownContent
import java.math.BigDecimal
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val MeterListHeightFraction = 0.5f
private const val PaymentPasswordLength = 6

private val PaymentPasswordInput = InputTransformation.byValue { _, proposed ->
    proposed.filter(Char::isDigit).take(PaymentPasswordLength)
}

private val MeterLevelTitles = listOf(
    R.string.meter_level_campus,
    R.string.meter_level_building,
    R.string.meter_level_floor,
    R.string.meter_level_room,
)

sealed interface MeterOptionsState {
    data object Loading : MeterOptionsState
    data class Failed(val reason: String?) : MeterOptionsState
    data class Loaded(val options: List<MeterOption>) : MeterOptionsState
}

/** 按从入口页向内的顺序声明，声明顺序决定切页滑动的方向。 */
enum class MeterSheetPage { Detail, Recharge, Form, LocationPicker }

sealed interface RechargeStatus {
    data object Idle : RechargeStatus

    data object Submitting : RechargeStatus

    /** 确定没有扣款，[reason] 是校园服务返回的原话。 */
    data class Rejected(val reason: String?) : RechargeStatus

    /** 可能已经扣款，用户必须先核对余额。 */
    data object Unconfirmed : RechargeStatus
}

/** 一次打开 [MeterEditorSheet] 时的草稿；[meter] 为 null 表示新增。关闭弹层后整体丢弃。 */
class MeterEditorState(val meter: ElectricityMeter?) {
    private var currentPage by mutableStateOf(if (meter == null) MeterSheetPage.Form else MeterSheetPage.Detail)

    /** 当前页。进入充值页时开始新的一次充值，离开就丢弃它，支付密码不会留到别的页面。 */
    var page: MeterSheetPage
        get() = currentPage
        set(value) {
            recharge = if (value == MeterSheetPage.Recharge) recharge ?: MeterRecharge() else null
            currentPage = value
        }

    /** 只在充值页上存在。 */
    var recharge by mutableStateOf<MeterRecharge?>(null)
        private set
    val name = TextFieldState(meter?.name.orEmpty())
    var location by mutableStateOf(meter?.location)
    var levelPath by mutableStateOf(emptyList<MeterOption>())

    /** 以已选路径为键的下一级选项，回到上一级时不用重新加载。 */
    val levelOptions = mutableStateMapOf<List<MeterOption>, MeterOptionsState>()

    val canSave: Boolean
        get() = name.text.isNotBlank() && location != null
}

/** 草稿里的一次充值。 */
class MeterRecharge {
    val amount = TextFieldState()
    val paymentPassword = TextFieldState()
    var status by mutableStateOf<RechargeStatus>(RechargeStatus.Idle)

    val amountYuan: Int?
        get() = amount.text.toString().toIntOrNull()?.takeIf { it > 0 }

    val canSubmit: Boolean
        get() = amountYuan != null &&
            paymentPassword.text.length == PaymentPasswordLength &&
            status != RechargeStatus.Submitting
}

/** 查看、编辑或充值 [editor] 里的电表；[editor] 为 null 时弹层关闭。 */
@Composable
fun MeterEditorSheet(
    editor: MeterEditorState?,
    meters: List<MeterReading>?,
    campusCardBalanceYuan: BigDecimal?,
    onLoadOptions: (List<MeterOption>) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onSubmitRecharge: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val sheet = rememberShownContent(editor)
    val state = sheet.value ?: return
    val isSubmitting = state.recharge?.status == RechargeStatus.Submitting
    val previousPage = when (state.page) {
        MeterSheetPage.Detail -> null
        MeterSheetPage.Recharge -> MeterSheetPage.Detail
        MeterSheetPage.Form -> MeterSheetPage.Detail.takeIf { state.meter != null }
        MeterSheetPage.LocationPicker -> MeterSheetPage.Form
    }

    OverlayBottomSheet(
        show = editor != null,
        title = sheetTitle(state),
        startAction = {
            if (previousPage == null) {
                SheetActionButton(
                    icon = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.electricity_meter_close),
                    onClick = onDismissRequest,
                )
            } else {
                SheetActionButton(
                    icon = MiuixIcons.Back,
                    contentDescription = stringResource(R.string.navigate_back),
                    enabled = !isSubmitting,
                    onClick = { state.page = previousPage },
                )
            }
        },
        endAction = {
            if (state.page == MeterSheetPage.Form) {
                SheetActionButton(
                    icon = MiuixIcons.Ok,
                    contentDescription = stringResource(R.string.electricity_meter_save),
                    enabled = state.canSave,
                    onClick = {
                        onSave()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                )
            }
        },
        onDismissRequest = onDismissRequest,
        onDismissFinished = sheet.release,
        allowDismiss = !isSubmitting,
    ) {
        AnimatedContent(
            targetState = state.page,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            transitionSpec = { pageSlide(isForward = targetState.ordinal > initialState.ordinal) },
        ) { page ->
            when (page) {
                MeterSheetPage.Detail -> state.meter?.let { meter ->
                    MeterDetail(
                        meter = meter,
                        reading = meters?.find { it.meter.id == meter.id }?.kilowattHours,
                        onEdit = { state.page = MeterSheetPage.Form },
                        onRecharge = { state.page = MeterSheetPage.Recharge },
                    )
                }

                // 离开充值页时这次充值立刻丢弃，滑出动画期间还要画它。
                MeterSheetPage.Recharge -> rememberShownContent(state.recharge).value?.let { recharge ->
                    MeterRechargeForm(
                        recharge = recharge,
                        campusCardBalanceYuan = campusCardBalanceYuan,
                        onSubmit = onSubmitRecharge,
                    )
                }

                MeterSheetPage.Form -> MeterForm(
                    state = state,
                    onDelete = {
                        onDelete()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                )

                MeterSheetPage.LocationPicker -> MeterLocationPicker(state = state, onLoadOptions = onLoadOptions)
            }
        }
    }
}

@Composable
private fun sheetTitle(state: MeterEditorState): String = when (state.page) {
    MeterSheetPage.Detail -> state.meter?.name.orEmpty()
    MeterSheetPage.Recharge -> stringResource(R.string.electricity_recharge)
    MeterSheetPage.Form -> stringResource(
        if (state.meter == null) R.string.electricity_meter_add else R.string.electricity_meter_edit,
    )

    MeterSheetPage.LocationPicker -> stringResource(R.string.electricity_meter_choose_location)
}

@Composable
private fun MeterDetail(
    meter: ElectricityMeter,
    reading: Double?,
    onEdit: () -> Unit,
    onRecharge: () -> Unit,
) {
    Column {
        Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
            BasicComponent(
                title = stringResource(R.string.home_remaining_electricity),
                endActions = { MeterReadingText(kilowattHours = reading) },
            )
            BasicComponent(
                title = stringResource(R.string.electricity_meter_location),
                summary = meter.location.path,
            )
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = stringResource(R.string.electricity_meter_edit_action),
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.electricity_recharge),
                onClick = onRecharge,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun MeterRechargeForm(
    recharge: MeterRecharge,
    campusCardBalanceYuan: BigDecimal?,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val isSubmitting = recharge.status == RechargeStatus.Submitting

    Column(modifier = Modifier.sensitiveContent()) {
        TextField(
            state = recharge.amount,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.electricity_recharge_amount),
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = RechargeAmountInput,
        )
        Text(
            text = stringResource(
                R.string.electricity_recharge_balance,
                campusCardBalanceYuan?.let(::formatYuan) ?: stringResource(R.string.home_not_fetched),
            ),
            modifier = Modifier.padding(start = TextFieldDefaults.InsideMargin.width, top = 8.dp),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        TextField(
            state = recharge.paymentPassword,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .semantics { password() },
            label = stringResource(R.string.electricity_recharge_password, PaymentPasswordLength),
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = PaymentPasswordInput,
            outputTransformation = PasswordMask,
        )
        rechargeFailureText(recharge.status)?.let { failure ->
            Text(
                text = failure,
                modifier = Modifier.padding(top = 8.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
        LoadingButton(
            text = stringResource(
                if (isSubmitting) R.string.electricity_recharge_submitting else R.string.electricity_recharge_confirm,
            ),
            isLoading = isSubmitting,
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            enabled = recharge.canSubmit,
        )
    }
}

@Composable
private fun rechargeFailureText(status: RechargeStatus): String? = when (status) {
    is RechargeStatus.Rejected -> status.reason ?: stringResource(R.string.electricity_recharge_failed)
    RechargeStatus.Unconfirmed -> stringResource(R.string.electricity_recharge_unconfirmed)
    RechargeStatus.Idle, RechargeStatus.Submitting -> null
}

@Composable
private fun MeterForm(
    state: MeterEditorState,
    onDelete: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column {
        TextField(
            state = state.name,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.electricity_meter_name),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        Card(
            modifier = Modifier.padding(top = 12.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        ) {
            ArrowPreference(
                title = stringResource(R.string.electricity_meter_location),
                summary = state.location?.path ?: stringResource(R.string.electricity_meter_not_selected),
                onClick = {
                    focusManager.clearFocus()
                    state.levelPath = emptyList()
                    state.page = MeterSheetPage.LocationPicker
                },
            )
        }
        if (state.meter != null) {
            TextButton(
                text = stringResource(R.string.electricity_meter_delete),
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
            )
        }
    }
}

@Composable
private fun MeterLocationPicker(
    state: MeterEditorState,
    onLoadOptions: (List<MeterOption>) -> Unit,
) {
    Column {
        // 当前层级排在路径末尾，点击它之前的选项会回到那一级。
        val trail = state.levelPath.map { it.name } + stringResource(MeterLevelTitles[state.levelPath.size])
        BreadcrumbBar(
            items = trail.map(::BreadcrumbItem),
            onItemClick = { index -> state.levelPath = state.levelPath.take(index) },
            insideMargin = PaddingValues(vertical = 8.dp),
        )
        // 固定高度，层级加载和切换时弹层不会跳动。
        val listHeight = LocalWindowInfo.current.containerDpSize.height * MeterListHeightFraction
        AnimatedContent(
            targetState = state.levelPath,
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight),
            transitionSpec = { pageSlide(isForward = targetState.size > initialState.size) },
        ) { levelPath ->
            LaunchedEffect(levelPath, onLoadOptions) { onLoadOptions(levelPath) }
            MeterLevel(
                state = state.levelOptions[levelPath] ?: MeterOptionsState.Loading,
                onRetry = { onLoadOptions(levelPath) },
                onOptionSelect = { option ->
                    val selectedPath = levelPath + option
                    if (selectedPath.size < MeterLevelTitles.size) {
                        state.levelPath = selectedPath
                    } else {
                        state.location = MeterLocation(
                            meterId = option.id,
                            path = selectedPath.joinToString(" / ") { it.name },
                        )
                        state.page = MeterSheetPage.Form
                    }
                },
            )
        }
    }
}

@Composable
private fun MeterLevel(
    state: MeterOptionsState,
    onRetry: () -> Unit,
    onOptionSelect: (MeterOption) -> Unit,
) {
    Crossfade(targetState = state) { current ->
        when (current) {
            MeterOptionsState.Loading -> CenteredBox {
                InfiniteProgressIndicator(color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }

            is MeterOptionsState.Failed -> CenteredBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MeterLevelMessage(current.reason ?: stringResource(R.string.meter_options_failed))
                    TextButton(
                        text = stringResource(R.string.meter_options_retry),
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            is MeterOptionsState.Loaded -> if (current.options.isEmpty()) {
                CenteredBox { MeterLevelMessage(stringResource(R.string.meter_options_empty)) }
            } else {
                MeterOptionList(options = current.options, onOptionSelect = onOptionSelect)
            }
        }
    }
}

@Composable
private fun MeterOptionList(
    options: List<MeterOption>,
    onOptionSelect: (MeterOption) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical(),
        overscrollEffect = null,
    ) {
        item(key = "options") {
            Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                options.forEach { option ->
                    ArrowPreference(
                        title = option.name,
                        onClick = { onOptionSelect(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun MeterLevelMessage(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
