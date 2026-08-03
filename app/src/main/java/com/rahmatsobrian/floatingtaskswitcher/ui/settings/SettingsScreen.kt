package com.rahmatsobrian.floatingtaskswitcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption
import com.rahmatsobrian.floatingtaskswitcher.data.local.PanelStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pengaturan") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SectionTitle("Mode Tampilan")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.panelStyle == style,
                            onClick = { viewModel.onPanelStyleChange(style) },
                            label = {
                                Text(
                                    style.name.replace('_', ' ').lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                )
                            },
                        )
                    }
                }
            }
            item { Divider() }
            item {
                SectionTitle("Opacity: ${(settings.opacity * 100).toInt()}%")
                Slider(
                    value = settings.opacity,
                    onValueChange = viewModel::onOpacityChange,
                    valueRange = 0.2f..1f,
                )
            }
            item {
                SectionTitle("Corner Radius: ${settings.cornerRadiusDp}dp")
                Slider(
                    value = settings.cornerRadiusDp.toFloat(),
                    onValueChange = { viewModel.onCornerRadiusChange(it.toInt()) },
                    valueRange = 0f..48f,
                )
            }
            item { Divider() }
            item {
                SettingsSwitchRow(
                    title = "Auto Hide",
                    checked = settings.autoHideEnabled,
                    onCheckedChange = viewModel::onAutoHideChange,
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Dynamic Color (Material You)",
                    checked = settings.dynamicColorEnabled,
                    onCheckedChange = viewModel::onDynamicColorChange,
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Gaming Mode (mini bubble otomatis saat game)",
                    checked = settings.gamingModeEnabled,
                    onCheckedChange = viewModel::onGamingModeChange,
                )
            }
            item { Divider() }
            item {
                SectionTitle("Dark Mode")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkModeOption.entries.forEach { option ->
                        FilterChip(
                            selected = settings.darkModeOption == option,
                            onClick = { viewModel.onDarkModeChange(option) },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
