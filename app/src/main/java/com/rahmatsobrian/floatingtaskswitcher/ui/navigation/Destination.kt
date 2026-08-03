package com.rahmatsobrian.floatingtaskswitcher.ui.navigation

sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object PermissionManager : Destination("permission_manager")
    data object Settings : Destination("settings")
}
