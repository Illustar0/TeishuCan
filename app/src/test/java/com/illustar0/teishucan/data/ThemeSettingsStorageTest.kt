package com.illustar0.teishucan.data

import androidx.datastore.core.CorruptionException
import com.illustar0.teishucan.domain.ColorSpec
import com.illustar0.teishucan.domain.PaletteStyle
import com.illustar0.teishucan.domain.PredictiveBackAnimation
import com.illustar0.teishucan.domain.PredictiveBackExitDirection
import com.illustar0.teishucan.domain.SeedColor
import com.illustar0.teishucan.domain.ThemeMode
import com.illustar0.teishucan.domain.ThemeSettings
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeSettingsStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
            predictiveBackAnimation = PredictiveBackAnimation.Scale,
            predictiveBackExitDirection = PredictiveBackExitDirection.FollowGesture,
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
    fun readFrom_corruptContent_reportsCorruption() {
        assertThrows(CorruptionException::class.java) {
            runBlocking { read("not json") }
        }
    }

    @Test
    fun data_corruptFile_readsDefaultsAndReplacesFile() = runTest {
        val file = temporaryFolder.newFile("theme_settings.json").apply { writeText("not json") }
        val store = JsonDataStore(file, ThemeSettings(), ThemeSettings.serializer(), scope = backgroundScope)

        assertEquals(ThemeSettings(), store.data.first())
        assertEquals(ThemeSettings(), read(file.readText()))
    }
}
