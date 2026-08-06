package com.rahmatsobrian.floatingtaskswitcher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rahmatsobrian.floatingtaskswitcher.ui.home.HomeScreen
import com.rahmatsobrian.floatingtaskswitcher.ui.permission.PermissionManagerScreen
import com.rahmatsobrian.floatingtaskswitcher.ui.settings.SettingsScreen

@Composable
fun FloatingTaskSwitcherNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Destination.Home.route) {
        composable(Destination.Home.route) {
            HomeScreen(
                onOpenPermissionManager = { navController.navigate(Destination.PermissionManager.route) },
                onOpenSettings = { navController.navigate(Destination.Settings.route) },
            )
        }
        composable(Destination.PermissionManager.route) {
            PermissionManagerScreen(onBack = navController::popBackStack)
        }
        composable(Destination.Settings.route) {
            SettingsScreen(onBack = navController::popBackStack)
        }
    }
}
