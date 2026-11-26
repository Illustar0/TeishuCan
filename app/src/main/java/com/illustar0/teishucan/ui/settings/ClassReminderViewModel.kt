package com.illustar0.teishucan.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.reminder.ClassReminderAccess
import com.illustar0.teishucan.reminder.ClassReminders
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
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
    private var lastLocale: Locale? = null

    /** 开关和系统设置都读出之前为 null，免得开关先关后开地闪一下。 */
    val state: StateFlow<ClassReminderSettingsState?> =
        combine(reminders.isEnabled, missingAccess.filterNotNull(), ::ClassReminderSettingsState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = StateStopTimeout), null)

    fun setEnabled(isEnabled: Boolean) {
        viewModelScope.launch { reminders.setEnabled(isEnabled) }
    }

    /**
     * 页面读到的系统设置 [missing] 与当前语言 [locale]。用户刚改过系统权限或切换过应用语言的话立即对齐一次：
     * 比如刚允许了通知，正在进行的提醒不必等到下一个闹钟才出现；刚切了语言，在显通知与渠道名也立即跟着更新。
     */
    fun onEnvironmentChecked(missing: List<ClassReminderAccess>, locale: Locale) {
        val previousMissing = missingAccess.value
        val previousLocale = lastLocale
        missingAccess.value = missing
        lastLocale = locale
        val hasAccessChanged = previousMissing != null && previousMissing != missing
        val hasLocaleChanged = previousLocale != null && previousLocale != locale
        if (hasAccessChanged || hasLocaleChanged) {
            viewModelScope.launch { reminders.sync() }
        }
    }
}
