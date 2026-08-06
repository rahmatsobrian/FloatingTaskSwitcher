package com.rahmatsobrian.floatingtaskswitcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption
import com.rahmatsobrian.floatingtaskswitcher.data.local.FloatingSettings
import com.rahmatsobrian.floatingtaskswitcher.data.local.PanelStyle
import com.rahmatsobrian.floatingtaskswitcher.data.local.SettingsDataStore
import com.rahmatsobrian.floatingtaskswitcher.data.repository.CrashLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val crashLogRepository: CrashLogRepository,
) : ViewModel() {

    val settings: StateFlow<FloatingSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingSettings())

    private val _crashLog = MutableStateFlow(crashLogRepository.read())
    val crashLog: StateFlow<String?> = _crashLog.asStateFlow()

    fun onPanelStyleChange(style: PanelStyle) = viewModelScope.launch {
        settingsDataStore.updatePanelStyle(style)
    }

    fun onOpacityChange(value: Float) = viewModelScope.launch {
        settingsDataStore.updateOpacity(value)
    }

    fun onCornerRadiusChange(dp: Int) = viewModelScope.launch {
        settingsDataStore.updateCornerRadius(dp)
    }

    fun onAutoHideChange(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updateAutoHide(enabled)
    }

    fun onDynamicColorChange(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updateDynamicColor(enabled)
    }

    fun onDarkModeChange(option: DarkModeOption) = viewModelScope.launch {
        settingsDataStore.updateDarkMode(option)
    }

    fun onGamingModeChange(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.updateGamingMode(enabled)
    }

    fun refreshCrashLog() {
        _crashLog.value = crashLogRepository.read()
    }

    fun clearCrashLog() {
        crashLogRepository.clear()
        _crashLog.value = null
    }
}
