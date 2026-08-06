package com.siroha.resourcetransfer.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.siroha.resourcetransfer.ui.screens.about.AboutScreen
import com.siroha.resourcetransfer.ui.screens.history.HistoryScreen
import com.siroha.resourcetransfer.ui.screens.home.HomeScreen
import com.siroha.resourcetransfer.ui.screens.logs.LogsScreen
import com.siroha.resourcetransfer.ui.screens.permissions.PermissionsScreen
import com.siroha.resourcetransfer.ui.screens.receive.ReceiveScreen
import com.siroha.resourcetransfer.ui.screens.send.SendScreen
import com.siroha.resourcetransfer.ui.screens.settings.SettingsScreen
import com.siroha.resourcetransfer.ui.screens.splash.SplashScreen

@Composable
fun ResourceTransferNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Destination.Splash.route,
        enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) + slideInHorizontally(initialOffsetX = { it / 6 }) },
        exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) },
        popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) },
        popExitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) + slideOutHorizontally(targetOffsetX = { it / 6 }) }
    ) {
        composable(Destination.Splash.route) {
            SplashScreen(onFinished = {
                navController.navigate(Destination.Home.route) {
                    popUpTo(Destination.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Destination.Home.route) {
            HomeScreen(
                onNavigateSend = { navController.navigate(Destination.Send.route) },
                onNavigateReceive = { navController.navigate(Destination.Receive.route) },
                onNavigateHistory = { navController.navigate(Destination.History.route) },
                onNavigateSettings = { navController.navigate(Destination.Settings.route) },
                onNavigateAbout = { navController.navigate(Destination.About.route) }
            )
        }
        composable(Destination.Send.route) { SendScreen(onBack = { navController.popBackStack() }) }
        composable(Destination.Receive.route) { ReceiveScreen(onBack = { navController.popBackStack() }) }
        composable(Destination.History.route) { HistoryScreen(onBack = { navController.popBackStack() }) }
        composable(Destination.Settings.route) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Destination.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onNavigatePermissions = { navController.navigate(Destination.Permissions.route) }
            )
        }
        composable(Destination.Permissions.route) { PermissionsScreen(onBack = { navController.popBackStack() }) }
        composable(Destination.Logs.route) { LogsScreen(onBack = { navController.popBackStack() }) }
    }
}
