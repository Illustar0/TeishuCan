package com.illustar0.teishucan.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.illustar0.teishucan.R
import com.illustar0.teishucan.domain.ColorSpec
import com.illustar0.teishucan.domain.PaletteStyle
import com.illustar0.teishucan.domain.SeedColor
import com.illustar0.teishucan.domain.ThemeMode
import com.illustar0.teishucan.domain.ThemeSettings
import com.illustar0.teishucan.ui.theme.BlurredBar
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.blurSource
import com.illustar0.teishucan.ui.theme.color
import com.illustar0.teishucan.ui.theme.isBlurSupported
import com.illustar0.teishucan.ui.theme.isPredictiveBackSupported
import com.illustar0.teishucan.ui.theme.isWallpaperColorSupported
import com.illustar0.teishucan.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ThemeSettingsPage(
    settings: ThemeSettings,
    onUpdate: ((ThemeSettings) -> ThemeSettings) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop) { containerColor ->
                TopAppBar(
                    title = stringResource(R.string.settings_theme),
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
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .blurSource(backdrop)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item(key = "theme") {
                ThemeSettingsContent(settings = settings, onUpdate = onUpdate)
            }
        }
    }
}

@Composable
private fun ThemeSettingsContent(
    settings: ThemeSettings,
    onUpdate: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Card(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_theme_mode),
                items = ThemeMode.entries.map { stringResource(it.labelRes) },
                selectedIndex = settings.mode.ordinal,
                onSelectedIndexChange = { index -> onUpdate { it.copy(mode = ThemeMode.entries[index]) } },
            )
            MonetPreferences(settings = settings, onUpdate = onUpdate)
        }
        AnimatedVisibility(
            visible = settings.isMonetEnabled && !settings.usesWallpaperColor(isWallpaperColorSupported),
        ) {
            Column {
                SmallTitle(
                    text = stringResource(R.string.settings_theme_seed_color),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SeedColorPicker(
                        selectedColor = settings.seedColor,
                        onColorSelect = { color -> onUpdate { it.copy(seedColor = color) } },
                    )
                }
            }
        }
        Card(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            if (isBlurSupported) {
                SwitchPreference(
                    title = stringResource(R.string.settings_theme_blur),
                    summary = stringResource(R.string.settings_theme_blur_summary),
                    checked = settings.isBlurEnabled,
                    onCheckedChange = { isEnabled -> onUpdate { it.copy(isBlurEnabled = isEnabled) } },
                )
            }
            SwitchPreference(
                title = stringResource(R.string.settings_theme_floating_navigation_bar),
                summary = stringResource(R.string.settings_theme_floating_navigation_bar_summary),
                checked = settings.isFloatingNavigationBarEnabled,
                onCheckedChange = { isEnabled -> onUpdate { it.copy(isFloatingNavigationBarEnabled = isEnabled) } },
            )
            if (isBlurSupported) {
                SwitchPreference(
                    title = stringResource(R.string.settings_theme_liquid_glass),
                    summary = stringResource(R.string.settings_theme_liquid_glass_summary),
                    checked = settings.isLiquidGlassEnabled,
                    onCheckedChange = { isEnabled -> onUpdate { it.copy(isLiquidGlassEnabled = isEnabled) } },
                )
            }
            if (isPredictiveBackSupported) {
                SwitchPreference(
                    title = stringResource(R.string.settings_theme_predictive_back),
                    summary = stringResource(R.string.settings_theme_predictive_back_summary),
                    checked = settings.isPredictiveBackEnabled,
                    onCheckedChange = { isEnabled -> onUpdate { it.copy(isPredictiveBackEnabled = isEnabled) } },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MonetPreferences(
    settings: ThemeSettings,
    onUpdate: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    SwitchPreference(
        title = stringResource(R.string.settings_theme_monet),
        summary = stringResource(R.string.settings_theme_monet_summary),
        checked = settings.isMonetEnabled,
        onCheckedChange = { isEnabled -> onUpdate { it.copy(isMonetEnabled = isEnabled) } },
    )
    AnimatedVisibility(visible = settings.isMonetEnabled) {
        Column {
            if (isWallpaperColorSupported) {
                SwitchPreference(
                    title = stringResource(R.string.settings_theme_wallpaper_color),
                    summary = stringResource(R.string.settings_theme_wallpaper_color_summary),
                    checked = settings.isWallpaperColorEnabled,
                    onCheckedChange = { isEnabled -> onUpdate { it.copy(isWallpaperColorEnabled = isEnabled) } },
                )
            }
            OverlayDropdownPreference(
                title = stringResource(R.string.settings_theme_palette_style),
                items = PaletteStyle.entries.map { stringResource(it.labelRes) },
                selectedIndex = settings.paletteStyle.ordinal,
                onSelectedIndexChange = { index ->
                    onUpdate { it.copy(paletteStyle = PaletteStyle.entries[index]) }
                },
            )
            ColorSpecPreference(settings = settings, onUpdate = onUpdate)
        }
    }
}

@Composable
private fun ColorSpecPreference(
    settings: ThemeSettings,
    onUpdate: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val isChoiceAvailable = settings.paletteStyle.supportsSpec2025
    val specs = if (isChoiceAvailable) ColorSpec.entries else listOf(ColorSpec.Spec2021)
    OverlayDropdownPreference(
        title = stringResource(R.string.settings_theme_color_spec),
        summary = if (isChoiceAvailable) null else stringResource(R.string.settings_theme_color_spec_only_2021),
        items = specs.map { stringResource(it.labelRes) },
        selectedIndex = specs.indexOf(settings.effectiveColorSpec),
        enabled = isChoiceAvailable,
        onSelectedIndexChange = { index -> onUpdate { it.copy(colorSpec = specs[index]) } },
    )
}

@Composable
private fun SeedColorPicker(
    selectedColor: SeedColor,
    onColorSelect: (SeedColor) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = 4,
    ) {
        SeedColor.entries.forEach { color ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = color == selectedColor,
                        role = Role.RadioButton,
                        onClick = {
                            if (color != selectedColor) {
                                onColorSelect(color)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            }
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.color),
                    contentAlignment = Alignment.Center,
                ) {
                    if (color == selectedColor) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Check,
                            contentDescription = null,
                            tint = if (color.color.luminance() > 0.4f) Color.Black else Color.White,
                        )
                    }
                }
                Text(
                    text = stringResource(color.labelRes),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
}

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_mode_system
        ThemeMode.Light -> R.string.theme_mode_light
        ThemeMode.Dark -> R.string.theme_mode_dark
    }

@get:StringRes
private val SeedColor.labelRes: Int
    get() = when (this) {
        SeedColor.Blue -> R.string.seed_color_blue
        SeedColor.Green -> R.string.seed_color_green
        SeedColor.Purple -> R.string.seed_color_purple
        SeedColor.Yellow -> R.string.seed_color_yellow
        SeedColor.Orange -> R.string.seed_color_orange
        SeedColor.Pink -> R.string.seed_color_pink
        SeedColor.Teal -> R.string.seed_color_teal
        SeedColor.Red -> R.string.seed_color_red
    }

@get:StringRes
private val PaletteStyle.labelRes: Int
    get() = when (this) {
        PaletteStyle.TonalSpot -> R.string.palette_style_tonal_spot
        PaletteStyle.Neutral -> R.string.palette_style_neutral
        PaletteStyle.Vibrant -> R.string.palette_style_vibrant
        PaletteStyle.Expressive -> R.string.palette_style_expressive
        PaletteStyle.Rainbow -> R.string.palette_style_rainbow
        PaletteStyle.FruitSalad -> R.string.palette_style_fruit_salad
        PaletteStyle.Monochrome -> R.string.palette_style_monochrome
        PaletteStyle.Fidelity -> R.string.palette_style_fidelity
        PaletteStyle.Content -> R.string.palette_style_content
    }

@get:StringRes
private val ColorSpec.labelRes: Int
    get() = when (this) {
        ColorSpec.Spec2021 -> R.string.color_spec_2021
        ColorSpec.Spec2025 -> R.string.color_spec_2025
    }

@Preview(showBackground = true)
@Composable
private fun ThemeSettingsPagePreview() {
    val settings = ThemeSettings(
        isMonetEnabled = true,
        isWallpaperColorEnabled = false,
        isBlurEnabled = false,
    )
    TeishuCanTheme(settings = settings, isDark = false) {
        ThemeSettingsPage(
            settings = settings,
            onUpdate = {},
            onBack = {},
        )
    }
}
