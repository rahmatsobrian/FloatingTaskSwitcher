package com.siroha.resourcetransfer.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.siroha.resourcetransfer.data.local.entity.TransferHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState()
    var showClearAllConfirm by remember { mutableStateOf(false) }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearAllConfirm = false }) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Batal") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali") }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Hapus Semua")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            ) {
                Text("Belum ada riwayat transfer.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Transfer yang selesai (dikirim maupun diterima) akan muncul di sini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { entry: TransferHistoryEntity ->
                    HistoryItem(entry, onDelete = { viewModel.delete(entry) })
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: TransferHistoryEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    imageVector = if (entry.direction == "SENT") Icons.Filled.Upload else Icons.Filled.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (entry.status == "COMPLETED") Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = null,
                        tint = if (entry.status == "COMPLETED") Color(0xFF2E7D32) else Color(0xFFD32F2F),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(entry.deviceName, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${if (entry.direction == "SENT") "Terkirim" else "Diterima"} • ${entry.fileCount} file • ${formatBytes(entry.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${entry.transportUsed} • ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id")).format(Date(entry.timestampEpochMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024)
}
