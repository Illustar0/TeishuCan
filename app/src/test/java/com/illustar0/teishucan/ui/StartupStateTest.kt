package com.illustar0.teishucan.ui

import com.illustar0.teishucan.domain.AccountState
import com.illustar0.teishucan.domain.StudentProfile
import com.illustar0.teishucan.domain.ThemeMode
import com.illustar0.teishucan.domain.ThemeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupStateTest {
    @Test
    fun staysLoading_untilThemeAndSessionBothComplete() = runTest {
        val theme = MutableSharedFlow<ThemeSettings>(replay = 1)
        val account = MutableStateFlow<AccountState>(AccountState.Restoring)

        val state = startupStateFlow(theme, account)
            .stateIn(backgroundScope, SharingStarted.Eagerly, StartupState.Loading)

        runCurrent()
        assertEquals(StartupState.Loading, state.value)

        val initialTheme = ThemeSettings(mode = ThemeMode.Dark)
        theme.emit(initialTheme)
        runCurrent()
        assertEquals(StartupState.Loading, state.value)

        account.value = AccountState.SignedOut
        runCurrent()
        assertEquals(StartupState.Ready(initialTheme), state.value)
    }

    @Test
    fun readyState_updatesOnThemeChangesButIgnoresLaterAccountChanges() = runTest {
        val theme = MutableSharedFlow<ThemeSettings>(replay = 1)
        val account = MutableStateFlow<AccountState>(AccountState.Restoring)
        val emissions = mutableListOf<StartupState>()

        backgroundScope.launch {
            startupStateFlow(theme, account).collect { emissions.add(it) }
        }

        val initialTheme = ThemeSettings(mode = ThemeMode.System)
        theme.emit(initialTheme)
        account.value = AccountState.SignedOut
        runCurrent()
        assertEquals(listOf(StartupState.Ready(initialTheme)), emissions)

        account.value = AccountState.SignedIn(
            StudentProfile(name = "Test", faculty = "CS", identityType = "Undergrad"),
        )
        runCurrent()
        assertEquals(1, emissions.size)

        val updatedTheme = initialTheme.copy(mode = ThemeMode.Light)
        theme.emit(updatedTheme)
        runCurrent()
        assertEquals(
            listOf(StartupState.Ready(initialTheme), StartupState.Ready(updatedTheme)),
            emissions,
        )
    }
}
