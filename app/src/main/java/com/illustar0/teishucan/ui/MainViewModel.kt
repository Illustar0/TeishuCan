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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

internal sealed interface StartupState {
    data object Loading : StartupState

    data class Ready(val themeSettings: ThemeSettings) : StartupState
}

/**
 * 合并主题设置与会话恢复状态，在两者都就绪后才发出 [StartupState.Ready]。
 *
 * 会话只需完成首次恢复即可退出启动等待，之后的登录或退出不再触发根部状态重建。
 */
internal fun startupStateFlow(
    themeSettings: Flow<ThemeSettings>,
    account: Flow<AccountState>,
): Flow<StartupState> = combine(
    themeSettings,
    account.filter { it != AccountState.Restoring }.take(1),
) { settings, _ ->
    StartupState.Ready(themeSettings = settings)
}

@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    session: ZzuSession,
) : ViewModel() {
    val account: StateFlow<AccountState> = session.account

    val state: StateFlow<StartupState> = startupStateFlow(
        themeSettings = themeRepository.settings,
        account = session.account,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, StartupState.Loading)

    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch { themeRepository.update(transform) }
    }
}
