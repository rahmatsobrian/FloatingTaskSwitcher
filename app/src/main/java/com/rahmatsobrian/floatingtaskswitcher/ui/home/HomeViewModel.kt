package com.rahmatsobrian.floatingtaskswitcher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahmatsobrian.floatingtaskswitcher.core.permission.OperatingModeManager
import com.rahmatsobrian.floatingtaskswitcher.data.local.SettingsDataStore
import com.rahmatsobrian.floatingtaskswitcher.domain.model.OperatingMode
import com.rahmatsobrian.floatingtaskswitcher.domain.repository.PermissionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val floatingEnabled: Boolean = false,
    val operatingMode: OperatingMode = OperatingMode.STANDARD,
    val essentialsGranted: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val permissionRepository: PermissionRepository,
    operatingModeManager: OperatingModeManager,
) : ViewModel() {

    private val floatingEnabledFlow = settingsDataStore.settings

    val uiState: StateFlow<HomeUiState> = combine(
        floatingEnabledFlow,
        operatingModeManager.currentMode,
    ) { settings, mode ->
        HomeUiState(
            floatingEnabled = settings.floatingServiceEnabled,
            operatingMode = mode,
            essentialsGranted = permissionRepository.isOverlayGranted() && permissionRepository.isUsageAccessGranted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFloatingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateFloatingServiceEnabled(enabled)
        }
    }
}
