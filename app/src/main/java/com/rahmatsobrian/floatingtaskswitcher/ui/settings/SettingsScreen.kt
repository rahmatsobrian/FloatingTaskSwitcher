package com.rahmatsobrian.floatingtaskswitcher.ui.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DensitySmall
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.data.local.DarkModeOption
import com.rahmatsobrian.floatingtaskswitcher.data.local.PanelStyle
import com.rahmatsobrian.floatingtaskswitcher.ui.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
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
        topBar = { AppTopBar(title = "Pengaturan") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SectionTitle("Mode Tampilan")
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelStyle.entries.chunked(3).forEach { rowStyles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowStyles.forEach { style ->
                                PanelStyleCard(
                                    style = style,
                                    selected = settings.panelStyle == style,
                                    onClick = { viewModel.onPanelStyleChange(style) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowStyles.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
                Text(
                    text = settings.panelStyle.description(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
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
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DarkModeOption.entries.forEach { option ->
                        FilterChip(
                            selected = settings.darkModeOption == option,
                            onClick = { viewModel.onDarkModeChange(option) },
                            label = { Text(option.displayLabel()) },
                            leadingIcon = {
                                Icon(imageVector = option.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }
            item { Divider() }
            item {
                SectionTitle("Tentang")
                Spacer(modifier = Modifier.height(8.dp))
                Card(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(imageVector = Icons.Filled.Info, contentDescription = null)
                            Text(text = "Tentang Aplikasi", style = MaterialTheme.typography.bodyLarge)
                        }
                        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null)
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

@Composable
private fun PanelStyleCard(
    style: PanelStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = style.icon(), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = style.displayLabel(),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

private fun PanelStyle.icon(): ImageVector = when (this) {
    PanelStyle.HORIZONTAL_DOCK -> Icons.Filled.ViewCarousel
    PanelStyle.VERTICAL_DOCK -> Icons.Filled.ViewStream
    PanelStyle.GRID -> Icons.Filled.GridView
    PanelStyle.COMPACT -> Icons.Filled.DensitySmall
    PanelStyle.MINI_BUBBLE -> Icons.Filled.Bolt
    PanelStyle.EXPAND_PANEL -> Icons.Filled.PushPin
}

private fun PanelStyle.displayLabel(): String = when (this) {
    PanelStyle.HORIZONTAL_DOCK -> "Horizontal Dock"
    PanelStyle.VERTICAL_DOCK -> "Vertical Dock"
    PanelStyle.GRID -> "Grid"
    PanelStyle.COMPACT -> "Compact"
    PanelStyle.MINI_BUBBLE -> "Ganti Cepat"
    PanelStyle.EXPAND_PANEL -> "Selalu Terbuka"
}

private fun PanelStyle.description(): String = when (this) {
    PanelStyle.HORIZONTAL_DOCK -> "Tap bubble untuk membuka panel berbentuk baris mendatar."
    PanelStyle.VERTICAL_DOCK -> "Tap bubble untuk membuka panel berbentuk kolom tegak."
    PanelStyle.GRID -> "Tap bubble untuk membuka panel grid 4 kolom."
    PanelStyle.COMPACT -> "Panel baris dengan icon lebih kecil dan tanpa nama aplikasi."
    PanelStyle.MINI_BUBBLE -> "Tap bubble langsung pindah ke aplikasi terakhir, tanpa membuka panel sama sekali."
    PanelStyle.EXPAND_PANEL -> "Panel selalu terbuka begitu floating aktif dan tidak bisa dikecilkan manual - cocok untuk dock permanen."
}

private fun DarkModeOption.icon(): ImageVector = when (this) {
    DarkModeOption.LIGHT -> Icons.Filled.LightMode
    DarkModeOption.DARK -> Icons.Filled.DarkMode
    DarkModeOption.AMOLED -> Icons.Filled.Contrast
    DarkModeOption.SYSTEM -> Icons.Filled.BrightnessAuto
}

private fun DarkModeOption.displayLabel(): String = when (this) {
    DarkModeOption.LIGHT -> "Light"
    DarkModeOption.DARK -> "Dark"
    DarkModeOption.AMOLED -> "Amoled"
    DarkModeOption.SYSTEM -> "System"
}
