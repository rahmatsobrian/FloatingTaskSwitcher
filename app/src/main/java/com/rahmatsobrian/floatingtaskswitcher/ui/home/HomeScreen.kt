package com.rahmatsobrian.floatingtaskswitcher.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.domain.model.OperatingMode
import com.rahmatsobrian.floatingtaskswitcher.service.OverlayService
import com.rahmatsobrian.floatingtaskswitcher.ui.components.AppTopBar

@Composable
fun HomeScreen(
    onOpenPermissionManager: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = { AppTopBar(title = "Floating Task Switcher") },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("Floating Overlay", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = modeLabel(state.operatingMode),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = state.floatingEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !Settings.canDrawOverlays(context)) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                    return@Switch
                                }
                                viewModel.setFloatingEnabled(enabled)
                                if (enabled) {
                                    OverlayService.start(context)
                                } else {
                                    OverlayService.stop(context)
                                }
                            },
                        )
                    }
                    if (!state.essentialsGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Beberapa izin penting belum diaktifkan. Buka Permission Manager untuk mengaturnya.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(onClick = onOpenPermissionManager, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Filled.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Permission Manager")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Filled.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Pengaturan")
            }
        }
    }
}

private fun modeLabel(mode: OperatingMode): String = when (mode) {
    OperatingMode.ROOT -> "Mode aktif: Root"
    OperatingMode.SHIZUKU -> "Mode aktif: Shizuku"
    OperatingMode.STANDARD -> "Mode aktif: Standard"
}
