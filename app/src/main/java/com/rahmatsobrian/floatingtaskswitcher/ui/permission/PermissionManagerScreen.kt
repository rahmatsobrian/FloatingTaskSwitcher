package com.rahmatsobrian.floatingtaskswitcher.ui.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.R
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionSnapshot
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionStatus
import com.rahmatsobrian.floatingtaskswitcher.ui.components.AppTopBar

private data class PermissionRow(
    val icon: ImageVector,
    val title: String,
    val reason: String,
    val status: PermissionStatus,
    val onRequest: (() -> Unit)?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionManagerScreen(
    onBack: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val rootInFlight by viewModel.rootRequestInFlight.collectAsState()
    val guidedSetupActive by viewModel.guidedSetupActive.collectAsState()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val onRequestOverlay: () -> Unit = {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
        )
    }
    val onRequestAccessibility: () -> Unit = {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
    val onRequestUsageAccess: () -> Unit = {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
    val onRequestNotification: () -> Unit = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        context.startActivity(intent)
    }
    val onRequestBattery: () -> Unit = {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
    }

    val rows = buildPermissionRows(
        snapshot = snapshot,
        onRequestOverlay = onRequestOverlay,
        onRequestAccessibility = onRequestAccessibility,
        onRequestUsageAccess = onRequestUsageAccess,
        onRequestNotification = onRequestNotification,
        onRequestBattery = onRequestBattery,
        onRequestShizuku = viewModel::onRequestShizuku,
        onRequestRoot = viewModel::onRequestRoot,
    )
    val rootTitle = stringRes(R.string.permission_root_title)

    // "Setup Sekali Klik": only chains through the essential, non-optional permissions (overlay,
    // accessibility, usage access, notification, battery). Root and Shizuku stay manual since
    // they're optional and each has its own separate grant flow (su prompt / Shizuku app).
    val nextGuidedStep: (() -> Unit)? = when {
        snapshot.overlay != PermissionStatus.GRANTED -> onRequestOverlay
        snapshot.accessibility != PermissionStatus.GRANTED -> onRequestAccessibility
        snapshot.usageAccess != PermissionStatus.GRANTED -> onRequestUsageAccess
        snapshot.notification == PermissionStatus.NOT_GRANTED -> onRequestNotification
        snapshot.batteryOptimizationExempt != PermissionStatus.GRANTED -> onRequestBattery
        else -> null
    }

    LaunchedEffect(snapshot, guidedSetupActive) {
        if (!guidedSetupActive) return@LaunchedEffect
        val step = nextGuidedStep
        if (step != null) {
            step.invoke()
        } else {
            viewModel.stopGuidedSetup()
            Toast.makeText(context, "Semua permission utama sudah aktif", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Permission Manager") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Setup Sekali Klik",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Buka otomatis satu per satu halaman izin yang belum aktif (Overlay, Accessibility, Usage Access, Notifikasi, Baterai) - cukup aktifkan lalu tekan kembali, lanjut otomatis ke berikutnya.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (guidedSetupActive) {
                            OutlinedButton(
                                onClick = viewModel::stopGuidedSetup,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Berhenti") }
                        } else {
                            Button(
                                onClick = viewModel::startGuidedSetup,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Mulai Setup Otomatis") }
                        }
                    }
                }
            }
            items(rows) { row ->
                PermissionCard(row = row, isBusy = row.title == rootTitle && rootInFlight)
            }
        }
    }
}

@Composable
private fun PermissionCard(row: PermissionRow, isBusy: Boolean) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(text = row.title, style = MaterialTheme.typography.titleMedium)
            }
            Text(text = row.reason, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(status = row.status)
                if (row.onRequest != null && row.status != PermissionStatus.GRANTED) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp), strokeWidth = 2.dp)
                    } else {
                        Button(onClick = row.onRequest) { Text("Aktifkan") }
                    }
                }
            }
        }
    }
}

private data class StatusStyle(val icon: ImageVector, val container: Color, val content: Color)

@Composable
private fun StatusChip(status: PermissionStatus) {
    val label = when (status) {
        PermissionStatus.GRANTED -> stringRes(R.string.status_granted)
        PermissionStatus.NOT_GRANTED -> stringRes(R.string.status_not_granted)
        PermissionStatus.UNSUPPORTED -> stringRes(R.string.status_unsupported)
        PermissionStatus.AVAILABLE -> stringRes(R.string.status_available)
        PermissionStatus.CHECKING -> stringRes(R.string.status_checking)
    }
    val scheme = MaterialTheme.colorScheme
    val style = when (status) {
        PermissionStatus.GRANTED -> StatusStyle(Icons.Filled.CheckCircle, scheme.primaryContainer, scheme.onPrimaryContainer)
        PermissionStatus.NOT_GRANTED -> StatusStyle(Icons.Filled.Cancel, scheme.errorContainer, scheme.onErrorContainer)
        PermissionStatus.AVAILABLE -> StatusStyle(Icons.Filled.Info, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        PermissionStatus.UNSUPPORTED -> StatusStyle(Icons.Filled.Block, scheme.surfaceVariant, scheme.onSurfaceVariant)
        PermissionStatus.CHECKING -> StatusStyle(Icons.Filled.HourglassEmpty, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(style.container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = style.content,
            modifier = Modifier.size(16.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = style.content)
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id = id)

@Composable
private fun buildPermissionRows(
    snapshot: PermissionSnapshot,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestRoot: () -> Unit,
): List<PermissionRow> = listOf(
    PermissionRow(
        Icons.Filled.Layers,
        stringRes(R.string.permission_overlay_title),
        stringRes(R.string.permission_overlay_reason),
        snapshot.overlay,
        onRequestOverlay,
    ),
    PermissionRow(
        Icons.Filled.SettingsAccessibility,
        stringRes(R.string.permission_accessibility_title),
        stringRes(R.string.permission_accessibility_reason),
        snapshot.accessibility,
        onRequestAccessibility,
    ),
    PermissionRow(
        Icons.Filled.QueryStats,
        stringRes(R.string.permission_usage_title),
        stringRes(R.string.permission_usage_reason),
        snapshot.usageAccess,
        onRequestUsageAccess,
    ),
    PermissionRow(
        Icons.Filled.Notifications,
        stringRes(R.string.permission_notification_title),
        stringRes(R.string.permission_notification_reason),
        snapshot.notification,
        onRequestNotification,
    ),
    PermissionRow(
        Icons.Filled.BatteryChargingFull,
        stringRes(R.string.permission_battery_title),
        stringRes(R.string.permission_battery_reason),
        snapshot.batteryOptimizationExempt,
        onRequestBattery,
    ),
    PermissionRow(
        Icons.Filled.Bolt,
        stringRes(R.string.permission_shizuku_title),
        stringRes(R.string.permission_shizuku_reason),
        snapshot.shizuku,
        onRequestShizuku,
    ),
    PermissionRow(
        Icons.Filled.AdminPanelSettings,
        stringRes(R.string.permission_root_title),
        stringRes(R.string.permission_root_reason),
        snapshot.root,
        onRequestRoot,
    ),
)
