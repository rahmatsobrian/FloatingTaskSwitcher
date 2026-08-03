package com.rahmatsobrian.floatingtaskswitcher.ui.overlay

import com.rahmatsobrian.floatingtaskswitcher.data.local.PanelStyle
import com.rahmatsobrian.floatingtaskswitcher.domain.model.OperatingMode
import com.rahmatsobrian.floatingtaskswitcher.domain.model.RunningApp

data class OverlayUiState(
    val isExpanded: Boolean = false,
    val apps: List<RunningApp> = emptyList(),
    val searchQuery: String = "",
    val panelStyle: PanelStyle = PanelStyle.HORIZONTAL_DOCK,
    val opacity: Float = 1f,
    val cornerRadiusDp: Int = 24,
    val operatingMode: OperatingMode = OperatingMode.STANDARD,
    val isLoading: Boolean = false,
) {
    val filteredApps: List<RunningApp>
        get() = if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
}
