package com.illustar0.teishucan.domain

import kotlinx.serialization.Serializable

enum class ThemeMode {
    System,
    Light,
    Dark;

    fun isDark(isSystemDark: Boolean): Boolean = when (this) {
        System -> isSystemDark
        Light -> false
        Dark -> true
    }
}

enum class SeedColor { Blue, Green, Purple, Yellow, Orange, Pink, Teal, Red }

enum class PaletteStyle {
    TonalSpot,
    Neutral,
    Vibrant,
    Expressive,
    Rainbow,
    FruitSalad,
    Monochrome,
    Fidelity,
    Content;

    val supportsSpec2025: Boolean
        get() = when (this) {
            TonalSpot, Neutral, Vibrant, Expressive -> true
            Rainbow, FruitSalad, Monochrome, Fidelity, Content -> false
        }
}

enum class ColorSpec { Spec2021, Spec2025 }

enum class PredictiveBackAnimation {
    None,
    Aosp,
    Miuix,
    Scale,
    Classic,
}

enum class PredictiveBackExitDirection {
    FollowGesture,
    AlwaysRight,
    AlwaysLeft,
}

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    val isMonetEnabled: Boolean = false,
    val isWallpaperColorEnabled: Boolean = true,
    val seedColor: SeedColor = SeedColor.Blue,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec = ColorSpec.Spec2025,
    val isBlurEnabled: Boolean = true,
    val isFloatingNavigationBarEnabled: Boolean = false,
    val isLiquidGlassEnabled: Boolean = false,
    val predictiveBackAnimation: PredictiveBackAnimation = PredictiveBackAnimation.Miuix,
    val predictiveBackExitDirection: PredictiveBackExitDirection = PredictiveBackExitDirection.AlwaysRight,
) {
    /** 实际采用的规格；切到只支持 2021 的风格再切回来时，保存的选择不会丢。 */
    val effectiveColorSpec: ColorSpec
        get() = if (paletteStyle.supportsSpec2025) colorSpec else ColorSpec.Spec2021

    fun usesWallpaperColor(isWallpaperColorSupported: Boolean): Boolean =
        isMonetEnabled && isWallpaperColorEnabled && isWallpaperColorSupported
}
