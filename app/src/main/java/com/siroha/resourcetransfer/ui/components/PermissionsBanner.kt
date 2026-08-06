package com.siroha.resourcetransfer.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Shows a rationale card + "Berikan Izin" button when any of [permissions]
 * hasn't been granted yet, and nothing at all once they're all granted.
 * Re-checks on every ON_RESUME (not just once at first composition) so the
 * banner disappears immediately after the user grants permission from
 * either the in-app dialog OR the system Settings screen — the same
 * "must reflect state changes without navigating away and back" fix
 * applied earlier to the Shizuku button.
 */
@Composable
fun PermissionsBanner(
    permissions: List<String>,
    rationale: String,
    modifier: Modifier = Modifier
) {
    if (permissions.isEmpty()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var missing by remember {
        mutableStateOf(permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        })
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missing = permissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        missing = results.filterValues { granted -> !granted }.keys.toList()
    }

    if (missing.isNotEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(rationale, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { launcher.launch(missing.toTypedArray()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Berikan Izin")
                }
            }
        }
    }
}
