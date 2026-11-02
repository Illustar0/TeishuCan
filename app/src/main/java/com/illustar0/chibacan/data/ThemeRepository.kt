package com.illustar0.chibacan.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.illustar0.chibacan.domain.ThemeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

private val Context.themeStore: DataStore<ThemeSettings> by dataStore(
    fileName = "theme_settings.json",
    serializer = JsonSerializer(ThemeSettings(), ThemeSettings.serializer()),
)

/** 保存在本机的主题设置。 */
@Singleton
class ThemeRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.themeStore

    /** 保存的主题设置；文件不可读时为默认设置。 */
    val settings: Flow<ThemeSettings> = store.dataOr(ThemeSettings())

    /** 保存 [transform] 的结果。写不进去时保持原样，主题设置不值得让应用崩溃。 */
    suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        try {
            store.updateData { transform(it) }
        } catch (cause: IOException) {
            Log.w("ChibaCan.Theme", "Could not save the theme settings", cause)
        }
    }
}
