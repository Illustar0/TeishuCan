package com.illustar0.teishucan.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ElectricityMeter
import com.illustar0.teishucan.domain.MeterLocation
import com.illustar0.teishucan.domain.MeterOption
import com.illustar0.teishucan.domain.MeterReading
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.ui.component.RefreshContainer
import com.illustar0.teishucan.ui.component.formatKilowattHours
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import java.math.BigDecimal
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ElectricitySettingsPage(
    meters: List<MeterReading>?,
    campusCardBalanceYuan: BigDecimal?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    editor: MeterEditorState,
    isEditorVisible: Boolean,
    onOpenEditor: (ElectricityMeter?) -> Unit,
    onDismissEditor: () -> Unit,
    onSaveMeter: () -> Unit,
    onDeleteMeter: () -> Unit,
    onSubmitRecharge: () -> Unit,
    onLoadMeterOptions: (List<MeterOption>) -> Unit,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.settings_electricity),
                    color = containerColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.navigate_back),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onOpenEditor(null) }) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = stringResource(R.string.electricity_meter_add),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        RefreshContainer(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            scrollBehavior = scrollBehavior,
            contentPadding = innerPadding,
            modifier = Modifier.fillMaxSize().blurSource(backdrop),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item(key = "meters") {
                    when {
                        meters == null -> Unit
                        meters.isEmpty() -> MeterListEmptyState()
                        else -> Card(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
                            meters.forEach { (meter, kilowattHours) ->
                                ArrowPreference(
                                    title = meter.name,
                                    summary = meter.location.path,
                                    endActions = { MeterReadingText(kilowattHours = kilowattHours) },
                                    onClick = { onOpenEditor(meter) },
                                )
                            }
                        }
                    }
                }
            }
        }
        MeterEditorSheet(
            show = isEditorVisible,
            state = editor,
            reading = meters?.find { it.meter.id == editor.meter?.id }?.kilowattHours,
            campusCardBalanceYuan = campusCardBalanceYuan,
            onLoadOptions = onLoadMeterOptions,
            onSave = onSaveMeter,
            onDelete = onDeleteMeter,
            onSubmitRecharge = onSubmitRecharge,
            onDismissRequest = onDismissEditor,
        )
    }
}

@Composable
private fun MeterListEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.electricity_meter_empty),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = stringResource(R.string.electricity_meter_empty_hint),
            modifier = Modifier.padding(top = 6.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun MeterReadingText(kilowattHours: Double?) {
    if (kilowattHours == null) {
        Text(
            text = stringResource(R.string.home_not_fetched),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    } else {
        Text(
            text = stringResource(R.string.electricity_meter_kilowatt_hours, formatKilowattHours(kilowattHours)),
            style = MiuixTheme.textStyles.headline2,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ElectricitySettingsPagePreview() {
    TeishuCanTheme(settings = ThemeSettings(isBlurEnabled = false), isDark = false) {
        ElectricitySettingsPage(
            meters = listOf(
                MeterReading(
                    ElectricityMeter("1", "Air conditioning", MeterLocation("a", "Campus / A / 3 / 301")),
                    42.5,
                ),
                MeterReading(ElectricityMeter("2", "Lighting", MeterLocation("b", "Campus / A / 3 / 302")), 123.45),
            ),
            campusCardBalanceYuan = "56.78".toBigDecimal(),
            isRefreshing = false,
            onRefresh = {},
            editor = MeterEditorState(meter = null),
            isEditorVisible = false,
            onOpenEditor = {},
            onDismissEditor = {},
            onSaveMeter = {},
            onDeleteMeter = {},
            onSubmitRecharge = {},
            onLoadMeterOptions = {},
            snackbarHostState = SnackbarHostState(),
            onBack = {},
        )
    }
}
