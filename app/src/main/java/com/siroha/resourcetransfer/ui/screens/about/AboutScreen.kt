package com.siroha.resourcetransfer.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onNavigatePermissions: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tentang") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Resource Transfer", style = MaterialTheme.typography.titleLarge)
                Text("Versi 1.0.0", style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "Aplikasi transfer file peer-to-peer. Dibuat murni sebagai alat transfer file " +
                    "umum — tidak menggunakan aset, kode, atau API dari game apa pun.",
                style = MaterialTheme.typography.bodyLarge
            )

            InfoSection(title = "Metode koneksi") {
                InfoLine("Wi-Fi LAN & Manual IP", "Aktif — perangkat harus di jaringan Wi-Fi yang sama.")
                InfoLine("Wi-Fi Direct, Nearby Connections, Hotspot Lokal, QR Pairing, Bluetooth", "Belum aktif di versi ini.")
            }

            InfoSection(title = "Sumber yang bisa dikirim") {
                InfoLine("Folder", "Nama folder ikut terbawa ke penerima.")
                InfoLine("File", "Satu atau banyak file sekaligus, tipe apa pun.")
                InfoLine("Media", "Foto/video langsung dari galeri.")
                InfoLine("Teks", "Ketik langsung atau tempel dari clipboard.")
                InfoLine("Aplikasi", "Ekspor APK/APKS aplikasi yang sudah terinstal.")
            }

            InfoSection(title = "Privasi") {
                InfoLine("100% offline", "Tidak ada data yang dikirim ke server mana pun — semua transfer langsung antar perangkat.")
                InfoLine("Verifikasi integritas", "Setiap file diverifikasi SHA-256 setelah diterima.")
            }

            Card(
                onClick = onNavigatePermissions,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Lihat penjelasan semua izin aplikasi", style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun InfoLine(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
