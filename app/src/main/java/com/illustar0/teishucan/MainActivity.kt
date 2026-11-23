package com.illustar0.teishucan

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.memory.FairMemory
import com.illustar0.teishucan.ui.NavigationRecovery
import com.illustar0.teishucan.ui.NavigationSnapshot
import com.illustar0.teishucan.ui.TeishuCanApp
import com.illustar0.teishucan.ui.component.CustomTabsUriHandler
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import com.illustar0.teishucan.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    internal lateinit var fairMemory: FairMemory

    @Inject
    internal lateinit var navigationRecovery: NavigationRecovery

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by themeViewModel.settings.collectAsStateWithLifecycle()
            val restoredNavigation by produceState<NavigationSnapshot?>(null) {
                value = navigationRecovery.restore()
            }
            // 读出保存的主题之前什么也不画，第一帧才不会闪成错误的主题。
            val themeSettings = settings ?: return@setContent
            val navigation = restoredNavigation ?: return@setContent
            val isDark = themeSettings.mode.isDark(isSystemInDarkTheme())
            val defaultUriHandler = LocalUriHandler.current
            val uriHandler = remember(defaultUriHandler) {
                CustomTabsUriHandler(this, defaultUriHandler)
            }

            LaunchedEffect(isDark) {
                val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                window.isNavigationBarContrastEnforced = false
            }

            TeishuCanTheme(
                settings = themeSettings,
                isDark = isDark,
                areHeavyEffectsEnabled = fairMemory.areHeavyEffectsEnabled,
            ) {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    TeishuCanApp(
                        themeSettings = themeSettings,
                        onThemeSettingsUpdate = themeViewModel::update,
                        restoredNavigation = navigation,
                        onNavigationChange = navigationRecovery::record,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        fairMemory.resumeEffects()
    }
}
