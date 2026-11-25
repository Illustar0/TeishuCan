package com.illustar0.teishucan.data

import android.util.Log
import com.illustar0.teishucan.domain.ThemeSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

private const val Tag = "TeishuCan.Theme"

@Singleton
class ThemeRepository @Inject constructor(stores: JsonDataStoreFactory) {
    private val store = stores.create(
        name = "theme_settings.json",
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
