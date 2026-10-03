package com.qtekfun.fexplo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.fexplo.core.model.ThemeMode
import com.qtekfun.fexplo.core.model.UserPreferences
import com.qtekfun.fexplo.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferencesRepository: UserPreferencesRepository) : ViewModel() {

    /** Null until the stored preferences have been read, so the UI does not flash the default theme. */
    val preferences: StateFlow<UserPreferences?> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }
}
