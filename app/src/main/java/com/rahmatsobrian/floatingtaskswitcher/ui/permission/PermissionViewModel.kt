package com.rahmatsobrian.floatingtaskswitcher.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rahmatsobrian.floatingtaskswitcher.core.permission.OperatingModeManager
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionSnapshot
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionStatus
import com.rahmatsobrian.floatingtaskswitcher.domain.repository.PermissionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val emptySnapshot = PermissionSnapshot(
    overlay = PermissionStatus.CHECKING,
    accessibility = PermissionStatus.CHECKING,
    usageAccess = PermissionStatus.CHECKING,
    notification = PermissionStatus.CHECKING,
    batteryOptimizationExempt = PermissionStatus.CHECKING,
    autoStart = PermissionStatus.CHECKING,
    shizuku = PermissionStatus.CHECKING,
    root = PermissionStatus.CHECKING,
)

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val permissionRepository: PermissionRepository,
    private val operatingModeManager: OperatingModeManager,
) : ViewModel() {

    private val _snapshot = MutableStateFlow(emptySnapshot)
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()

    private val _rootRequestInFlight = MutableStateFlow(false)
    val rootRequestInFlight: StateFlow<Boolean> = _rootRequestInFlight.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _snapshot.value = permissionRepository.refreshSnapshot()
        }
    }

    fun onRequestRoot() {
        viewModelScope.launch {
            _rootRequestInFlight.value = true
            operatingModeManager.userRequestsRoot()
            _rootRequestInFlight.value = false
            refresh()
        }
    }

    fun onRequestShizuku() {
        operatingModeManager.userRequestsShizukuPermission()
        refresh()
    }
}
