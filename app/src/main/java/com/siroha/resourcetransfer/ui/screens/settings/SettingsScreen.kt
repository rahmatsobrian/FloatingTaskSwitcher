package com.siroha.resourcetransfer.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.siroha.resourcetransfer.data.datastore.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset semua pengaturan?") },
            text = { Text("Mengembalikan tema, ukuran chunk, dan opsi lain di halaman ini ke nilai bawaan.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetToDefaults(); showResetConfirm = false }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    // Icon alone was confusing ("kenapa ada tombol refresh?") — the confirm
                    // dialog above now makes the action's purpose explicit the moment it's tapped.
                    IconButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset ke Default")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(title = "Tampilan") {
                Text("Tema", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    ThemeChip("Sistem", Icons.Filled.SettingsSuggest, ThemeMode.SYSTEM, settings.themeMode) { viewModel.setThemeMode(it) }
                    ThemeChip("Terang", Icons.Filled.LightMode, ThemeMode.LIGHT, settings.themeMode) { viewModel.setThemeMode(it) }
                    ThemeChip("Gelap", Icons.Filled.DarkMode, ThemeMode.DARK, settings.themeMode) { viewModel.setThemeMode(it) }
                    ThemeChip("AMOLED", Icons.Filled.Brightness4, ThemeMode.AMOLED, settings.themeMode) { viewModel.setThemeMode(it) }
                }
                Text(
                    "Menentukan warna latar aplikasi. \"Sistem\" mengikuti pengaturan tema Android kamu. " +
                        "\"AMOLED\" memakai hitam pekat — hemat baterai di layar OLED.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionDivider()

                SettingSwitchRow(
                    label = "Warna Dinamis (Material You)",
                    description = "Mengambil warna aksen dari wallpaper HP kamu (Android 12+). Kalau dimatikan, " +
                        "aplikasi memakai warna biru bawaan (KernelSU Clean Light).",
                    checked = settings.dynamicColorEnabled,
                    onCheckedChange = { viewModel.setDynamicColor(it) }
                )
            }

            SettingsSection(title = "Transfer") {
                SettingSwitchRow(
                    label = "Coba Ulang Otomatis",
                    description = "Kalau koneksi terputus di tengah transfer, aplikasi otomatis mencoba " +
                        "menyambung ulang tanpa perlu kamu mulai dari awal.",
                    checked = settings.autoRetryEnabled,
                    onCheckedChange = { viewModel.setAutoRetry(it) }
                )

                SectionDivider()

                Text("Ukuran Chunk: ${settings.chunkSizeKb} KB", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = settings.chunkSizeKb.toFloat(),
                    onValueChange = { viewModel.setChunkSizeKb(it.toInt()) },
                    valueRange = 64f..4096f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Ukuran potongan data yang dikirim tiap kali baca/tulis file. Lebih besar = " +
                        "biasanya lebih cepat di jaringan stabil, tapi pakai lebih banyak RAM. " +
                        "Nilai default (512 KB) cocok untuk kebanyakan kondisi — turunkan kalau " +
                        "sering putus-putus di jaringan yang lemah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SettingsSection(title = "Keamanan (belum aktif di build ini)") {
                SettingSwitchRow(
                    label = "Enkripsi Transfer (AES-256)",
                    description = "Rencananya akan mengenkripsi isi file selama proses transfer. " +
                        "Belum diimplementasikan — mengaktifkan ini belum berefek apa pun ke transfer sebenarnya.",
                    checked = settings.encryptionEnabled,
                    onCheckedChange = { viewModel.setEncryption(it) }
                )

                SectionDivider()

                SettingSwitchRow(
                    label = "Kompresi",
                    description = "Rencananya akan mengecilkan ukuran data sebelum dikirim untuk file yang " +
                        "kompresibel. Belum diimplementasikan.",
                    checked = settings.compressionEnabled,
                    onCheckedChange = { viewModel.setCompression(it) }
                )
            }

            SettingsSection(title = "Developer") {
                SettingSwitchRow(
                    label = "Mode Developer",
                    description = "Menampilkan detail teknis tambahan (transport aktif, timing) di layar " +
                        "transfer dan log. Berguna kalau kamu mau lapor bug atau debug masalah koneksi.",
                    checked = settings.developerModeEnabled,
                    onCheckedChange = { viewModel.setDeveloperMode(it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

/** Slightly inset + softer color than the bare default, so it reads as a subtle
 *  separator inside the card rather than a stray full-bleed line. */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 1.dp
    )
}

@Composable
private fun SettingSwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            // This is the alignment fix — without it, the label/Switch pair defaults to
            // Top alignment, which visually shoves the label above the Switch's center.
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeChip(label: String, icon: ImageVector, mode: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    FilterChip(
        selected = mode == current,
        onClick = { onSelect(mode) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.height(18.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
