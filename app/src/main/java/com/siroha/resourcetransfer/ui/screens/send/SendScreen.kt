package com.siroha.resourcetransfer.ui.screens.send

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.siroha.resourcetransfer.domain.model.TransferStatus
import com.siroha.resourcetransfer.ui.components.AllFilesAccessBanner
import com.siroha.resourcetransfer.ui.components.PermissionsBanner
import com.siroha.resourcetransfer.util.RequiredPermissions
import com.siroha.resourcetransfer.util.ShizukuState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(onBack: () -> Unit, viewModel: SendViewModel = hiltViewModel()) {
    val progress by viewModel.progress.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onFolderPicked(context, uri)
        }
    }

    val filesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> -> viewModel.onFilesPicked(uris) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> -> viewModel.onMediaPicked(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kirim") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali") }
                }
            )
        },
        bottomBar = {
            // Pinned regardless of scroll position — this is what fixes having to
            // scroll past a long app/file list just to reach the send button.
            // navigationBarsPadding() is required here specifically because this is
            // a plain Column, not a NavigationBar-type composable that handles system
            // bar insets on its own — without it, edge-to-edge drawing (enableEdgeToEdge()
            // in MainActivity) lets this button render underneath the phone's gesture/nav bar.
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (progress.status) {
                    TransferStatus.RUNNING -> OutlinedButton(onClick = { viewModel.pause() }, modifier = Modifier.fillMaxWidth()) { Text("Jeda") }
                    TransferStatus.PAUSED -> OutlinedButton(onClick = { viewModel.resume() }, modifier = Modifier.fillMaxWidth()) { Text("Lanjutkan") }
                    else -> {}
                }
                Button(
                    onClick = { viewModel.startDiscoveryAndWaitForReceiver() },
                    enabled = uiState.sourceFolderPath != null && uiState.targetIp.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mulai & Tunggu Penerima")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AllFilesAccessBanner()

            PermissionsBanner(
                permissions = RequiredPermissions.forDiscovery() + RequiredPermissions.forLegacyStorage(),
                rationale = "Izin ini dibutuhkan Android supaya aplikasi bisa mencari perangkat lain di jaringan Wi-Fi dan membaca file yang kamu pilih."
            )

            // Connection section lives at the top, ABOVE the source picker (which can get long —
            // 150+ apps, many files, etc.) so searching for / picking a receiver never requires
            // scrolling past that content first.
            FilledTonalButton(
                onClick = { if (uiState.isScanning) viewModel.stopLanDiscovery() else viewModel.startLanDiscovery() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null)
                Text(if (uiState.isScanning) "  Berhenti Mencari" else "  Cari Perangkat di Wi-Fi")
            }

            uiState.discoveredDevices.forEach { device ->
                Card(
                    onClick = { viewModel.onDeviceSelected(device) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(device.ipAddress ?: "-", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = uiState.targetIp,
                onValueChange = { viewModel.onTargetIpChanged(it) },
                label = { Text("IP Penerima") },
                placeholder = { Text("contoh: 192.168.1.5, atau pilih dari daftar di atas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Mode selector — every mode ends up filling uiState.sourceFolderPath one way or another.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ModeChip("Folder", Icons.Filled.Folder, SendSourceMode.FOLDER, uiState.mode, viewModel::setMode)
                ModeChip("File", Icons.Filled.InsertDriveFile, SendSourceMode.FILES, uiState.mode, viewModel::setMode)
                ModeChip("Media", Icons.Filled.Image, SendSourceMode.MEDIA, uiState.mode, viewModel::setMode)
                ModeChip("Teks", Icons.Filled.TextFields, SendSourceMode.TEXT, uiState.mode, viewModel::setMode)
                ModeChip("Aplikasi", Icons.Filled.Apps, SendSourceMode.APP, uiState.mode, viewModel::setMode)
            }

            when (uiState.mode) {
                SendSourceMode.FOLDER -> {
                    Text("Pilih folder yang ingin dikirim.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { folderPickerLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Text("  Pilih Folder Sumber")
                    }
                }
                SendSourceMode.FILES -> {
                    Text("Pilih satu atau beberapa file (dokumen, ZIP, APK, dll).", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { filesPickerLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                        Text(if (uiState.stagedFileNames.isEmpty()) "  Pilih File" else "  Tambah File Lagi")
                    }
                }
                SendSourceMode.MEDIA -> {
                    Text("Pilih foto/video langsung dari galeri.", style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(
                        onClick = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState.stagedFileNames.isEmpty()) "Pilih Foto/Video" else "Tambah Foto/Video Lagi")
                    }
                }
                SendSourceMode.TEXT -> {
                    Text("Ketik teks, atau tempel dari clipboard.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = uiState.textInput,
                        onValueChange = { viewModel.onTextChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("Teks yang dikirim") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { viewModel.pasteFromClipboard() }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = null)
                            Text("  Tempel Clipboard")
                        }
                        Button(
                            onClick = { viewModel.confirmTextForSending() },
                            enabled = uiState.textInput.isNotBlank()
                        ) {
                            Text("Gunakan Teks Ini")
                        }
                    }
                }
                SendSourceMode.APP -> {
                    Text("Pilih aplikasi terinstal untuk dikirim (APK / APKS).", style = MaterialTheme.typography.bodyMedium)

                    OutlinedTextField(
                        value = uiState.appSearchQuery,
                        onValueChange = { viewModel.onAppSearchChanged(it) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Cari aplikasi...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.isLoadingApps) {
                        Text("Memuat daftar aplikasi...", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val filtered = uiState.filteredApps
                        Text(
                            "${filtered.size} aplikasi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(modifier = Modifier.height(320.dp)) {
                            items(filtered) { app ->
                                Card(
                                    onClick = { viewModel.onAppSelected(app) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(app.appName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${app.packageName}${if (app.hasSplits) " • split APK" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Itemized list for multi-item modes (Files/Media/Text/App), with per-item remove +
            // "Hapus Semua" — replaces the old plain "Sumber: N file dipilih" summary text, which
            // gave no way to see WHAT was picked or undo a single item.
            if (uiState.mode != SendSourceMode.FOLDER && uiState.stagedFileNames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Dipilih (${uiState.stagedFileNames.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(onClick = { viewModel.clearAllStagedFiles() }) {
                            Text("Hapus Semua")
                        }
                    }
                    uiState.stagedFileNames.forEach { name ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp)
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                                IconButton(onClick = { viewModel.removeStagedFile(name) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Hapus $name")
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.mode == SendSourceMode.FOLDER) {
                uiState.sourceFolderName?.let { name ->
                    Text("Sumber: $name", style = MaterialTheme.typography.bodyMedium)
                    if (uiState.resolvedViaShizuku) {
                        AssistChip(onClick = {}, label = { Text("Path asli via Shizuku") })
                    }
                }
            }

            if (uiState.shizukuState != ShizukuState.READY && uiState.mode == SendSourceMode.FOLDER) {
                FilledTonalButton(onClick = { viewModel.requestShizukuPermission() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Text(
                        when (uiState.shizukuState) {
                            ShizukuState.NOT_INSTALLED -> "  Shizuku belum terpasang (opsional)"
                            ShizukuState.NOT_RUNNING -> "  Shizuku belum berjalan"
                            ShizukuState.PERMISSION_DENIED -> "  Aktifkan Akses Shizuku"
                            else -> "  Shizuku"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.sourceFolderName != null && uiState.sourceFolderPath == null) {
                Text(
                    "Sumber ini belum bisa diakses langsung — coba folder di penyimpanan internal utama, " +
                        "atau aktifkan Shizuku di atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            uiState.statusMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            if (progress.status != TransferStatus.QUEUED) {
                Text(
                    "${progress.currentFileName} (${progress.currentFileIndex}/${progress.totalFiles})",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${progress.percent}% • ${formatSpeed(progress.speedBytesPerSec)} • ETA ${progress.etaSeconds}d")
                if (progress.activeTransport != null) {
                    Text("Metode aktif: ${progress.activeTransport!!.displayName}", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Bottom padding so the last scrollable item isn't hidden behind the pinned bottomBar.
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChip(
    label: String,
    icon: ImageVector,
    mode: SendSourceMode,
    current: SendSourceMode,
    onSelect: (SendSourceMode) -> Unit
) {
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

private fun formatSpeed(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    return if (kb < 1024) "%.1f KB/s".format(kb) else "%.1f MB/s".format(kb / 1024)
}
