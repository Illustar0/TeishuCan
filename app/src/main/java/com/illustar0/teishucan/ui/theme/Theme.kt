package com.illustar0.teishucan.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.illustar0.teishucan.domain.ColorSpec
import com.illustar0.teishucan.domain.PaletteStyle
import com.illustar0.teishucan.domain.SeedColor
import com.illustar0.teishucan.domain.ThemeMode
import com.illustar0.teishucan.domain.ThemeSettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

val LocalIsDarkTheme = staticCompositionLocalOf { false }

val isWallpaperColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val isPredictiveBackSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

val SeedColor.color: Color
    get() = when (this) {
        SeedColor.Blue -> Color(0xFF3482FF)
        SeedColor.Green -> Color(0xFF36D167)
        SeedColor.Purple -> Color(0xFF7C4DFF)
        SeedColor.Yellow -> Color(0xFFFFB21D)
        SeedColor.Orange -> Color(0xFFFF5722)
        SeedColor.Pink -> Color(0xFFE91E63)
        SeedColor.Teal -> Color(0xFF00BCD4)
        SeedColor.Red -> Color(0xFFE53935)
    }

private val ThemeSettings.colorSchemeMode: ColorSchemeMode
    get() = when (mode) {
        ThemeMode.System -> if (isMonetEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
        ThemeMode.Light -> if (isMonetEnabled) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        ThemeMode.Dark -> if (isMonetEnabled) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
    }

private fun PaletteStyle.toMiuix(): ThemePaletteStyle = when (this) {
    PaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
    PaletteStyle.Neutral -> ThemePaletteStyle.Neutral
    PaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
    PaletteStyle.Expressive -> ThemePaletteStyle.Expressive
    PaletteStyle.Rainbow -> ThemePaletteStyle.Rainbow
    PaletteStyle.FruitSalad -> ThemePaletteStyle.FruitSalad
    PaletteStyle.Monochrome -> ThemePaletteStyle.Monochrome
    PaletteStyle.Fidelity -> ThemePaletteStyle.Fidelity
    PaletteStyle.Content -> ThemePaletteStyle.Content
}

private fun ColorSpec.toMiuix(): ThemeColorSpec = when (this) {
    ColorSpec.Spec2021 -> ThemeColorSpec.Spec2021
    ColorSpec.Spec2025 -> ThemeColorSpec.Spec2025
}

@SuppressLint("NewApi") // Guarded by isWallpaperColorSupported.
@Composable
fun TeishuCanTheme(
    settings: ThemeSettings,
    isDark: Boolean,
    areHeavyEffectsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    // keyColor 为 null 时 Miuix 会忽略 paletteStyle 和 colorSpec，所以用强调色作种子。
    val keyColor = if (settings.usesWallpaperColor(isWallpaperColorSupported)) {
        colorResource(android.R.color.system_accent1_500)
    } else {
        settings.seedColor.color
    }
    val controller = remember(settings, isDark, keyColor) {
        ThemeController(
            colorSchemeMode = settings.colorSchemeMode,
            keyColor = keyColor,
            colorSpec = settings.effectiveColorSpec.toMiuix(),
            paletteStyle = settings.paletteStyle.toMiuix(),
            isDark = isDark,
        )
    }
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(
            LocalIsBlurEnabled provides (areHeavyEffectsEnabled && settings.isBlurEnabled),
            LocalIsLiquidGlassEnabled provides (areHeavyEffectsEnabled && settings.isLiquidGlassEnabled),
            LocalIsDarkTheme provides isDark,
            content = content,
        )
    }
}
