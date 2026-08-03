package com.rahmatsobrian.floatingtaskswitcher.ui.permission

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahmatsobrian.floatingtaskswitcher.R
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionSnapshot
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionStatus

private data class PermissionRow(
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
    val context = LocalContext.current

    val rows = buildPermissionRows(
        snapshot = snapshot,
        onRequestOverlay = {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
            )
        },
        onRequestAccessibility = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        onRequestUsageAccess = {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        },
        onRequestNotification = {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
            context.startActivity(intent)
        },
        onRequestBattery = {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
            )
        },
        onRequestShizuku = viewModel::onRequestShizuku,
        onRequestRoot = viewModel::onRequestRoot,
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Permission Manager") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val rootTitle = stringRes(R.string.permission_root_title)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                Text(text = row.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
            Text(text = row.reason, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.padding(top = 4.dp),
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

@Composable
private fun StatusChip(status: PermissionStatus) {
    val label = when (status) {
        PermissionStatus.GRANTED -> stringRes(R.string.status_granted)
        PermissionStatus.NOT_GRANTED -> stringRes(R.string.status_not_granted)
        PermissionStatus.UNSUPPORTED -> stringRes(R.string.status_unsupported)
        PermissionStatus.AVAILABLE -> stringRes(R.string.status_available)
        PermissionStatus.CHECKING -> stringRes(R.string.status_checking)
    }
    AssistChip(onClick = {}, label = { Text(label) })
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
        stringRes(R.string.permission_overlay_title),
        stringRes(R.string.permission_overlay_reason),
        snapshot.overlay,
        onRequestOverlay,
    ),
    PermissionRow(
        stringRes(R.string.permission_accessibility_title),
        stringRes(R.string.permission_accessibility_reason),
        snapshot.accessibility,
        onRequestAccessibility,
    ),
    PermissionRow(
        stringRes(R.string.permission_usage_title),
        stringRes(R.string.permission_usage_reason),
        snapshot.usageAccess,
        onRequestUsageAccess,
    ),
    PermissionRow(
        stringRes(R.string.permission_notification_title),
        stringRes(R.string.permission_notification_reason),
        snapshot.notification,
        onRequestNotification,
    ),
    PermissionRow(
        stringRes(R.string.permission_battery_title),
        stringRes(R.string.permission_battery_reason),
        snapshot.batteryOptimizationExempt,
        onRequestBattery,
    ),
    PermissionRow(
        stringRes(R.string.permission_shizuku_title),
        stringRes(R.string.permission_shizuku_reason),
        snapshot.shizuku,
        onRequestShizuku,
    ),
    PermissionRow(
        stringRes(R.string.permission_root_title),
        stringRes(R.string.permission_root_reason),
        snapshot.root,
        onRequestRoot,
    ),
)
