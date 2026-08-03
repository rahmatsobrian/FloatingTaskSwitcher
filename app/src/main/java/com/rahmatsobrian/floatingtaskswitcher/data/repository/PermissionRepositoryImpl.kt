package com.rahmatsobrian.floatingtaskswitcher.data.repository

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.rahmatsobrian.floatingtaskswitcher.core.permission.RootController
import com.rahmatsobrian.floatingtaskswitcher.core.permission.ShizukuController
import com.rahmatsobrian.floatingtaskswitcher.core.permission.ShizukuState
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionSnapshot
import com.rahmatsobrian.floatingtaskswitcher.domain.model.PermissionStatus
import com.rahmatsobrian.floatingtaskswitcher.domain.repository.PermissionRepository
import com.rahmatsobrian.floatingtaskswitcher.service.TaskAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootController: RootController,
    private val shizukuController: ShizukuController,
) : PermissionRepository {

    override fun observeSnapshot(): Flow<PermissionSnapshot> = flow {
        emit(refreshSnapshot())
    }

    override suspend fun refreshSnapshot(): PermissionSnapshot {
        shizukuController.refreshState()
        val shizukuStatus = when (shizukuController.state.value) {
            ShizukuState.RUNNING_GRANTED -> PermissionStatus.GRANTED
            ShizukuState.RUNNING_NOT_GRANTED -> PermissionStatus.NOT_GRANTED
            ShizukuState.NOT_RUNNING -> PermissionStatus.AVAILABLE
            ShizukuState.NOT_INSTALLED -> PermissionStatus.UNSUPPORTED
        }
        return PermissionSnapshot(
            overlay = statusOf(isOverlayGranted()),
            accessibility = statusOf(isAccessibilityServiceEnabled()),
            usageAccess = statusOf(isUsageAccessGranted()),
            notification = statusOf(isNotificationGranted()),
            batteryOptimizationExempt = statusOf(isBatteryOptimizationExempt()),
            autoStart = PermissionStatus.UNSUPPORTED, // OEM-specific; no public API exists, see docs
            shizuku = shizukuStatus,
            root = if (rootController.isRootAvailable()) PermissionStatus.AVAILABLE else PermissionStatus.UNSUPPORTED,
        )
    }

    override fun isOverlayGranted(): Boolean = Settings.canDrawOverlays(context)

    override fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "${context.packageName}/${TaskAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        return splitter.any { it.equals(expectedComponent, ignoreCase = true) }
    }

    override fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun isNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompatEnabled(context)
        }
    }

    override fun isBatteryOptimizationExempt(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun NotificationManagerCompatEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.areNotificationsEnabled()
    }

    private fun statusOf(granted: Boolean): PermissionStatus =
        if (granted) PermissionStatus.GRANTED else PermissionStatus.NOT_GRANTED
}
