package com.siroha.resourcetransfer.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siroha.resourcetransfer.data.datastore.AppSettings
import com.siroha.resourcetransfer.data.datastore.SettingsDataStore
import com.siroha.resourcetransfer.data.datastore.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setDynamicColor(enabled) }
    fun setAutoRetry(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setAutoRetry(enabled) }
    fun setEncryption(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setEncryption(enabled) }
    fun setCompression(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setCompression(enabled) }
    fun setDeveloperMode(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setDeveloperMode(enabled) }
    fun setChunkSizeKb(kb: Int) = viewModelScope.launch { settingsDataStore.setChunkSizeKb(kb) }

    fun resetToDefaults() = viewModelScope.launch {
        val defaults = AppSettings()
        settingsDataStore.setThemeMode(defaults.themeMode)
        settingsDataStore.setDynamicColor(defaults.dynamicColorEnabled)
        settingsDataStore.setAutoRetry(defaults.autoRetryEnabled)
        settingsDataStore.setEncryption(defaults.encryptionEnabled)
        settingsDataStore.setCompression(defaults.compressionEnabled)
        settingsDataStore.setDeveloperMode(defaults.developerModeEnabled)
        settingsDataStore.setChunkSizeKb(defaults.chunkSizeKb)
    }
}
