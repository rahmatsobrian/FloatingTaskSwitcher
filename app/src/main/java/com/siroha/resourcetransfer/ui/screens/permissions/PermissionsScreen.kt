package com.siroha.resourcetransfer.ui.screens.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class PermissionExplainer(val name: String, val reason: String, val icon: ImageVector)

private val explainers = listOf(
    PermissionExplainer("Lokasi (Android ≤12)", "Diperlukan sistem Android agar Wi-Fi Direct dan Bluetooth bisa memindai perangkat sekitar.", Icons.Filled.LocationOn),
    PermissionExplainer("Perangkat Sekitar (Android 13+)", "Menggantikan izin lokasi untuk memindai Wi-Fi Direct tanpa perlu akses lokasi.", Icons.Filled.Wifi),
    PermissionExplainer("Bluetooth", "Dipakai sebagai metode transfer fallback saat semua metode Wi-Fi gagal.", Icons.Filled.Bluetooth),
    PermissionExplainer("Wi-Fi", "Diperlukan untuk Wi-Fi Direct, Hotspot Lokal, dan LAN.", Icons.Filled.Wifi),
    PermissionExplainer("Penyimpanan / Akses Semua File", "Untuk membaca file yang dikirim dan menyimpan file yang diterima di folder mana pun.", Icons.Filled.Folder),
    PermissionExplainer("Notifikasi", "Menampilkan progres transfer saat aplikasi berjalan di background.", Icons.Filled.Notifications),
    PermissionExplainer("Kamera", "Hanya dipakai untuk memindai QR Pairing, opsional.", Icons.Filled.CameraAlt)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Izin Aplikasi") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Kenapa aplikasi ini minta izin-izin berikut, dan buat apa masing-masing dipakai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    explainers.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Column(modifier = Modifier.padding(start = 14.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    item.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index != explainers.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
