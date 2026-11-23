package com.illustar0.teishucan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.data.ThemeRepository
import com.illustar0.teishucan.data.ZzuSession
import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

internal sealed interface StartupState {
    data object Loading : StartupState

    data class Ready(
        val themeSettings: ThemeSettings,
        val restoredNavigation: NavigationSnapshot,
    ) : StartupState
}

/**
 * 合并主题设置、导航恢复与会话恢复状态，在三者都就绪后才发出 [StartupState.Ready]。
 *
 * 会话只需完成首次恢复即可退出启动等待，之后的登录或退出不再触发根部状态重建。
 */
internal fun startupStateFlow(
    themeSettings: Flow<ThemeSettings>,
    restoredNavigation: Flow<NavigationSnapshot>,
    account: Flow<AccountState>,
): Flow<StartupState> = combine(
    themeSettings,
    restoredNavigation,
    account.filter { it != AccountState.Restoring }.take(1),
) { settings, navigation, _ ->
    StartupState.Ready(themeSettings = settings, restoredNavigation = navigation)
}

@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val navigationRecovery: NavigationRecovery,
    session: ZzuSession,
) : ViewModel() {
    val state: StateFlow<StartupState> = startupStateFlow(
        themeSettings = themeRepository.settings,
        restoredNavigation = flow { emit(navigationRecovery.restore()) },
        account = session.account,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, StartupState.Loading)

    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch { themeRepository.update(transform) }
    }

    suspend fun recordNavigation(snapshot: NavigationSnapshot) {
        navigationRecovery.record(snapshot)
    }
}
