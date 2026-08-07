package com.rahmatsobrian.floatingtaskswitcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption
import com.rahmatsobrian.floatingtaskswitcher.ui.navigation.FloatingTaskSwitcherNavGraph
import com.rahmatsobrian.floatingtaskswitcher.ui.settings.SettingsViewModel
import com.rahmatsobrian.floatingtaskswitcher.ui.theme.AppAmoledColors
import com.rahmatsobrian.floatingtaskswitcher.ui.theme.AppDarkColors
import com.rahmatsobrian.floatingtaskswitcher.ui.theme.AppLightColors
import com.rahmatsobrian.floatingtaskswitcher.ui.theme.FloatingTaskSwitcherTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            // Status bar / nav bar icon+background follow the SAME resolved theme as the rest
            // of the UI: AMOLED -> solid black with light icons, Light -> solid white with dark
            // icons, Dark/System-dark -> the app's dark surface color with light icons.
            val useDark = when (settings.darkModeOption) {
                DarkModeOption.LIGHT -> false
                DarkModeOption.DARK, DarkModeOption.AMOLED -> true
                DarkModeOption.SYSTEM -> systemDark
            }
            val barColorArgb = when {
                settings.darkModeOption == DarkModeOption.AMOLED -> AppAmoledColors.background.toArgb()
                useDark -> AppDarkColors.surface.toArgb()
                else -> AppLightColors.surface.toArgb()
            }

            LaunchedEffect(useDark, barColorArgb) {
                val style = if (useDark) {
                    SystemBarStyle.dark(barColorArgb)
                } else {
                    SystemBarStyle.light(barColorArgb, barColorArgb)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            FloatingTaskSwitcherTheme(
                darkModeOption = settings.darkModeOption,
                dynamicColorEnabled = settings.dynamicColorEnabled,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FloatingTaskSwitcherNavGraph()
                }
            }
        }
    }
}
