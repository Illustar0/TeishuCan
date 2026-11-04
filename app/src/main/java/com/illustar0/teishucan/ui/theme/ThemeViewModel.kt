package com.illustar0.teishucan.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.illustar0.teishucan.data.ThemeRepository
import com.illustar0.teishucan.domain.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ThemeViewModel @Inject constructor(private val repository: ThemeRepository) : ViewModel() {
    /** 保存的主题设置，读出之前为 null。 */
    val settings: StateFlow<ThemeSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch { repository.update(transform) }
    }
}
