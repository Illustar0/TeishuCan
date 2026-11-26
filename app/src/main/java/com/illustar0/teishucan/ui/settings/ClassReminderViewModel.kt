package com.illustar0.teishucan.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.reminder.ClassReminderAccess
import com.illustar0.teishucan.reminder.ClassReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val StateStopTimeout = 5.seconds

/** 设置页里的上课提醒：开关 [isEnabled]，以及用户在系统设置里没有允许的 [missingAccess]。 */
@Immutable
data class ClassReminderSettingsState(val isEnabled: Boolean, val missingAccess: List<ClassReminderAccess>)

@HiltViewModel
class ClassReminderViewModel @Inject constructor(private val reminders: ClassReminders) : ViewModel() {
    /** 系统设置只能在页面上读，读到之前为 null。 */
    private val missingAccess = MutableStateFlow<List<ClassReminderAccess>?>(null)

    /** 开关和系统设置都读出之前为 null，免得开关先关后开地闪一下。 */
    val state: StateFlow<ClassReminderSettingsState?> =
        combine(reminders.isEnabled, missingAccess.filterNotNull(), ::ClassReminderSettingsState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = StateStopTimeout), null)

    fun setEnabled(isEnabled: Boolean) {
        viewModelScope.launch { reminders.setEnabled(isEnabled) }
    }

    /**
     * 页面读到的系统设置。用户刚在系统设置里改过的话立即对齐一次：比如刚允许了通知，
     * 正在进行的提醒不必等到下一个闹钟才出现。
     */
    fun onAccessChecked(missing: List<ClassReminderAccess>) {
        val previous = missingAccess.value
        missingAccess.value = missing
        if (previous != null && previous != missing) {
            viewModelScope.launch { reminders.sync() }
        }
    }
}
