package com.siroha.resourcetransfer.ui.navigation

sealed class Destination(val route: String) {
    data object Splash : Destination("splash")
    data object Home : Destination("home")
    data object Send : Destination("send")
    data object Receive : Destination("receive")
    data object History : Destination("history")
    data object Settings : Destination("settings")
    data object About : Destination("about")
    data object Permissions : Destination("permissions")
    data object Logs : Destination("logs")
}
