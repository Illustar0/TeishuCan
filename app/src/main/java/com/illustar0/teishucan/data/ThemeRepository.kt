package com.illustar0.teishucan.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.illustar0.teishucan.domain.ThemeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

private val Context.themeStore: DataStore<ThemeSettings> by dataStore(
    fileName = "theme_settings.json",
    serializer = JsonSerializer(ThemeSettings(), ThemeSettings.serializer()),
)

@Singleton
class ThemeRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.themeStore

    val settings: Flow<ThemeSettings> = store.dataOr(ThemeSettings())

    /** 写入失败时保持原样，主题设置不值得让应用崩溃。 */
    suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        try {
            store.updateData { transform(it) }
        } catch (cause: IOException) {
            Log.w("TeishuCan.Theme", "Could not save the theme settings", cause)
        }
    }
}
