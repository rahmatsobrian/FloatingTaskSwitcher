package com.rahmatsobrian.floatingtaskswitcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.ui.navigation.FloatingTaskSwitcherNavGraph
import com.rahmatsobrian.floatingtaskswitcher.ui.settings.SettingsViewModel
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
