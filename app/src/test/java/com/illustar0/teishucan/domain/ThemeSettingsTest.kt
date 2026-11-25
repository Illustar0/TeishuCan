package com.illustar0.teishucan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSettingsTest {
    @Test
    fun effectiveColorSpec_styleSupportsSpec2025_keepsStoredSpec() {
        val settings = ThemeSettings(
            paletteStyle = PaletteStyle.Vibrant,
            colorSpec = ColorSpec.Spec2025,
        )

        assertEquals(ColorSpec.Spec2025, settings.effectiveColorSpec)
    }

    @Test
    fun effectiveColorSpec_styleLacksSpec2025_fallsBackToSpec2021() {
        val settings = ThemeSettings(
            paletteStyle = PaletteStyle.Rainbow,
            colorSpec = ColorSpec.Spec2025,
        )

        assertEquals(ColorSpec.Spec2021, settings.effectiveColorSpec)
    }

    @Test
    fun usesWallpaperColor_requiresMonetPreferenceAndDeviceSupport() {
        val settings = ThemeSettings(isMonetEnabled = true, isWallpaperColorEnabled = true)

        assertTrue(settings.usesWallpaperColor(isWallpaperColorSupported = true))
        assertFalse(settings.usesWallpaperColor(isWallpaperColorSupported = false))
        assertFalse(settings.copy(isMonetEnabled = false).usesWallpaperColor(isWallpaperColorSupported = true))
        assertFalse(settings.copy(isWallpaperColorEnabled = false).usesWallpaperColor(isWallpaperColorSupported = true))
    }

    @Test
    fun effectivePredictiveBackAnimation_supported_keepsStoredAnimation() {
        val settings = ThemeSettings(predictiveBackAnimation = PredictiveBackAnimation.Scale)

        assertEquals(
            PredictiveBackAnimation.Scale,
            settings.effectivePredictiveBackAnimation(isPredictiveBackSupported = true),
        )
    }

    @Test
    fun effectivePredictiveBackAnimation_unsupported_fallsBackToDefault() {
        val default = ThemeSettings().predictiveBackAnimation

        PredictiveBackAnimation.entries.forEach { stored ->
            val settings = ThemeSettings(predictiveBackAnimation = stored)

            assertEquals(default, settings.effectivePredictiveBackAnimation(isPredictiveBackSupported = false))
        }
    }
}
