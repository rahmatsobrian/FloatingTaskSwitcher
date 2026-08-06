package com.siroha.resourcetransfer.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.siroha.resourcetransfer.util.StorageAccessHelper

/**
 * Shows a card explaining why "All files access" is needed and a button
 * that opens the dedicated Settings screen for it — MANAGE_EXTERNAL_STORAGE
 * can't be requested through a normal permission dialog. Re-checks on
 * every ON_RESUME so this disappears the moment the user flips the toggle
 * in Settings and comes back, without needing to relaunch the screen.
 *
 * Shows nothing on API < 30 (concept doesn't exist there) or once granted.
 */
@Composable
fun AllFilesAccessBanner(modifier: Modifier = Modifier) {
    if (!StorageAccessHelper.isRelevantOnThisDevice()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(StorageAccessHelper.isManageStorageGranted()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = StorageAccessHelper.isManageStorageGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        granted = StorageAccessHelper.isManageStorageGranted()
    }

    if (!granted) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.FolderOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "Tanpa izin \"Akses semua file\", folder yang kamu pilih akan terlihat kepilih tapi " +
                        "isinya 0 file saat dikirim/diterima — Android diam-diam memblokir pembacaan " +
                        "isi folder di luar penyimpanan khusus aplikasi ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = { launcher.launch(StorageAccessHelper.buildManageStorageIntent(context)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aktifkan Akses Semua File")
                }
            }
        }
    }
}
