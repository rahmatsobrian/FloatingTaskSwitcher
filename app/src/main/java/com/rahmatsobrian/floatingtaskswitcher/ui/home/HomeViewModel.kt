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

    // Bumped by refresh() so returning from a system Settings screen (where the user just
    // granted overlay/usage-access permission) re-evaluates essentialsGranted immediately,
    // instead of waiting for settingsDataStore/operatingMode to happen to change on their own.
    private val refreshTick = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = combine(
        settingsDataStore.settings,
        operatingModeManager.currentMode,
        refreshTick,
    ) { settings, mode, _ ->
        HomeUiState(
            floatingEnabled = settings.floatingServiceEnabled,
            operatingMode = mode,
            essentialsGranted = permissionRepository.isOverlayGranted() && permissionRepository.isUsageAccessGranted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        refreshTick.value += 1
    }

    fun setFloatingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateFloatingServiceEnabled(enabled)
        }
    }
}
