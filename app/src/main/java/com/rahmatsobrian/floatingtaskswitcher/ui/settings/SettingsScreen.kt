package com.rahmatsobrian.floatingtaskswitcher.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption
import com.rahmatsobrian.floatingtaskswitcher.data.local.PanelStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val crashLog by viewModel.crashLog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshCrashLog()
    }

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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PanelStyle.entries.forEach { style ->
                        FilterChip(
                            selected = settings.panelStyle == style,
                            onClick = { viewModel.onPanelStyleChange(style) },
                            label = { Text(style.displayLabel()) },
                        )
                    }
                }
                Text(
                    text = settings.panelStyle.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
                    title = "Gaming Mode (otomatis mengecil ke bubble saat main game)",
                    checked = settings.gamingModeEnabled,
                    onCheckedChange = viewModel::onGamingModeChange,
                )
            }
            item { Divider() }
            item {
                SectionTitle("Dark Mode")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkModeOption.entries.forEach { option ->
                        FilterChip(
                            selected = settings.darkModeOption == option,
                            onClick = { viewModel.onDarkModeChange(option) },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
            if (crashLog != null) {
                item { Divider() }
                item {
                    SectionTitle("Log & Diagnostik")
                    Card(modifier = Modifier.padding(top = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Aplikasi sempat force close. Log tersimpan di bawah ini - kirim ke pengembang untuk diperbaiki.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = crashLog.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 8.dp),
                                maxLines = 12,
                            )
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, crashLog.orEmpty())
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Kirim log crash"))
                                }) { Text("Bagikan") }
                                OutlinedButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(crashLog.orEmpty()))
                                }) { Text("Salin") }
                                TextButton(onClick = viewModel::clearCrashLog) { Text("Hapus") }
                            }
                        }
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun PanelStyle.displayLabel(): String = when (this) {
    PanelStyle.HORIZONTAL_DOCK -> "Horizontal Dock"
    PanelStyle.VERTICAL_DOCK -> "Vertical Dock"
    PanelStyle.GRID -> "Grid"
    PanelStyle.COMPACT -> "Compact"
    PanelStyle.MINI_BUBBLE -> "Ganti Cepat"
    PanelStyle.EXPAND_PANEL -> "Panel Selalu Terbuka"
}

private fun PanelStyle.description(): String = when (this) {
    PanelStyle.HORIZONTAL_DOCK -> "Tap bubble untuk membuka panel berbentuk baris mendatar."
    PanelStyle.VERTICAL_DOCK -> "Tap bubble untuk membuka panel berbentuk kolom tegak."
    PanelStyle.GRID -> "Tap bubble untuk membuka panel grid 4 kolom."
    PanelStyle.COMPACT -> "Panel baris dengan icon lebih kecil dan tanpa nama aplikasi."
    PanelStyle.MINI_BUBBLE -> "Tap bubble langsung pindah ke aplikasi terakhir, tanpa membuka panel sama sekali."
    PanelStyle.EXPAND_PANEL -> "Panel selalu terbuka begitu floating aktif dan tidak bisa dikecilkan manual - cocok untuk dock permanen."
}
