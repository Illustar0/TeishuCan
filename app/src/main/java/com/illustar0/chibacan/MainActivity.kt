package com.illustar0.chibacan

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.chibacan.ui.ChibaCanApp
import com.illustar0.chibacan.ui.theme.ChibaCanTheme
import com.illustar0.chibacan.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings by themeViewModel.settings.collectAsStateWithLifecycle()
            // 读出保存的主题之前什么也不画，第一帧才不会闪成错误的主题。
            val themeSettings = settings ?: return@setContent
            val isDark = themeSettings.mode.isDark(isSystemInDarkTheme())

            LaunchedEffect(isDark) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                window.isNavigationBarContrastEnforced = false
            }

            ChibaCanTheme(settings = themeSettings, isDark = isDark) {
                ChibaCanApp(
                    themeSettings = themeSettings,
                    onThemeSettingsUpdate = themeViewModel::update,
                )
            }
        }
    }
}
