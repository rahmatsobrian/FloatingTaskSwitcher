package com.siroha.resourcetransfer.ui.screens.receive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.siroha.resourcetransfer.domain.model.TransferStatus
import com.siroha.resourcetransfer.ui.components.AllFilesAccessBanner
import com.siroha.resourcetransfer.ui.components.PermissionsBanner
import com.siroha.resourcetransfer.util.RequiredPermissions

/**
 * Receiver flow: pick destination folder -> show this device's local IP
 * so the sender knows what to type -> listen for an inbound connection ->
 * diff the incoming manifest against what's already here -> request only
 * missing files -> receive + verify SHA256 as each file arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(onBack: () -> Unit, viewModel: ReceiveViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onFolderPicked(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terima") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        bottomBar = {
            // Pinned + navigationBarsPadding() so this never overlaps the phone's
            // gesture/navigation bar under edge-to-edge drawing — same fix as SendScreen.
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isListening) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Spacer(modifier = Modifier.height(0.dp))
                        Text("  Menunggu koneksi masuk...", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.cancelListening() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Batalkan")
                    }
                } else {
                    Button(
                        onClick = { viewModel.startListening() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mulai Menunggu Pengirim")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AllFilesAccessBanner()

            PermissionsBanner(
                permissions = RequiredPermissions.forDiscovery() + RequiredPermissions.forNotifications() + RequiredPermissions.forLegacyStorage(),
                rationale = "Izin ini dibutuhkan supaya perangkat ini bisa terlihat oleh pengirim di jaringan Wi-Fi, progres transfer muncul di notifikasi, dan file yang diterima bisa ditulis ke folder tujuan."
            )

            Text(
                "File yang diterima otomatis tersimpan di folder \"ResourceTransfer\" — " +
                    "atau pilih folder lain kalau mau.",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                if (uiState.isDefaultFolder) "Tujuan: ${uiState.destFolderName} (default)" else "Tujuan: ${uiState.destFolderName}",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { folderPickerLauncher.launch(null) }) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Text("  Pilih Folder Lain")
                }
                if (!uiState.isDefaultFolder) {
                    OutlinedButton(onClick = { viewModel.resetToDefaultFolder() }) {
                        Text("Pakai Default")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("IP Perangkat Ini", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            uiState.localIpAddress ?: "Tidak terhubung ke Wi-Fi",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.localIpAddress != null) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("IP", uiState.localIpAddress))
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Salin IP")
                            }
                        }
                    }
                    Text(
                        "Ketik alamat ini di layar Kirim pada perangkat pengirim.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (progress.totalBytes > 0 && progress.status != TransferStatus.COMPLETED) {
                LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${progress.currentFileName} (${progress.currentFileIndex}/${progress.totalFiles}) • ${progress.percent}%")
            }

            uiState.statusMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            // Bottom padding so the last scrollable item isn't hidden behind the pinned bottomBar.
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
