package com.illustar0.teishucan

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.memory.FairMemory
import com.illustar0.teishucan.ui.MainViewModel
import com.illustar0.teishucan.ui.StartupState
import com.illustar0.teishucan.ui.TeishuCanApp
import com.illustar0.teishucan.ui.component.CustomTabsUriHandler
import com.illustar0.teishucan.ui.theme.TeishuCanTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    internal lateinit var fairMemory: FairMemory

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val startupState = viewModel.state
        // 启动屏保持到主题、导航和会话全部就绪，避免首帧闪白或闪成错误的主题与登录态。
        splashScreen.setKeepOnScreenCondition { startupState.value is StartupState.Loading }
        setContent {
            val state = startupState.collectAsStateWithLifecycle()
            ReportDrawnWhen { state.value is StartupState.Ready }
            val ready = state.value as? StartupState.Ready ?: return@setContent
            val account by viewModel.account.collectAsStateWithLifecycle()
            val isDark = ready.themeSettings.mode.isDark(isSystemInDarkTheme())
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
                settings = ready.themeSettings,
                isDark = isDark,
                areHeavyEffectsEnabled = fairMemory.areHeavyEffectsEnabled,
            ) {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    TeishuCanApp(
                        themeSettings = ready.themeSettings,
                        onThemeSettingsUpdate = viewModel::updateTheme,
                        restoredNavigation = ready.restoredNavigation,
                        onNavigationChange = viewModel::recordNavigation,
                        isSignedOut = account == AccountState.SignedOut,
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
