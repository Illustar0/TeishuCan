package com.illustar0.teishucan.data

import android.content.Context
import android.util.Log
import androidx.datastore.dataStoreFile
import com.illustar0.teishucan.domain.ThemeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

private const val Tag = "TeishuCan.Theme"

@Singleton
class ThemeRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = JsonDataStore(
        file = context.dataStoreFile("theme_settings.json"),
        defaultValue = ThemeSettings(),
        serializer = ThemeSettings.serializer(),
    )

    val settings: Flow<ThemeSettings> = store.data

    /** 写入失败时保持原样，主题设置不值得让应用崩溃。 */
    suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        try {
            store.update(transform)
        } catch (cause: IOException) {
            Log.w(Tag,"Could not save the theme settings", cause)
        }
    }
}
