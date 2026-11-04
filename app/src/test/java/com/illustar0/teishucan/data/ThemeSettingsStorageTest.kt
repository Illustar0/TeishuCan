package com.illustar0.teishucan.data

import com.illustar0.teishucan.domain.ColorSpec
import com.illustar0.teishucan.domain.PaletteStyle
import com.illustar0.teishucan.domain.SeedColor
import com.illustar0.teishucan.domain.ThemeMode
import com.illustar0.teishucan.domain.ThemeSettings
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsStorageTest {
    private val serializer = JsonSerializer(ThemeSettings(), ThemeSettings.serializer())

    private suspend fun read(json: String): ThemeSettings = serializer.readFrom(json.byteInputStream())

    @Test
    fun readFrom_writtenSettings_returnsThem() = runTest {
        val settings = ThemeSettings(
            mode = ThemeMode.Dark,
            isMonetEnabled = true,
            isWallpaperColorEnabled = false,
            seedColor = SeedColor.Teal,
            paletteStyle = PaletteStyle.Expressive,
            colorSpec = ColorSpec.Spec2021,
        )
        val output = ByteArrayOutputStream()

        serializer.writeTo(settings, output)

        assertEquals(settings, read(output.toString()))
    }

    @Test
    fun readFrom_unknownEnumName_defaultsOnlyThatField() = runTest {
        val settings = read("""{"mode":"Dark","paletteStyle":"RemovedStyle"}""")

        assertEquals(ThemeSettings(mode = ThemeMode.Dark), settings)
    }

    @Test
    fun readFrom_corruptContent_returnsDefaults() = runTest {
        assertEquals(ThemeSettings(), read("not json"))
    }
}
