package com.siroha.resourcetransfer.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * `MANAGE_EXTERNAL_STORAGE` ("All files access") is NOT a normal dangerous
 * permission — it can't be requested via ActivityResultContracts.RequestPermission.
 * The user has to be sent to a dedicated Settings screen to toggle it on.
 *
 * This is the permission that actually matters for this app on API 29+:
 * without it, `java.io.File` access to folders outside the app's own
 * sandbox (like a user-picked "Garena" or "MT2" folder) silently returns
 * empty directory listings even when `canRead()` on the folder itself
 * reports true — which is exactly what produced the "0 file terkirim /
 * diterima" bug despite the folder clearly containing files.
 */
object StorageAccessHelper {

    fun isManageStorageGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Pre-API 30: legacy storage model, READ/WRITE_EXTERNAL_STORAGE (RequiredPermissions.forLegacyStorage) is the relevant gate instead.
        }
    }

    /** Whether this device even has the "All files access" concept (API 30+). */
    fun isRelevantOnThisDevice(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun buildManageStorageIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            }.getOrElse {
                // Some OEM ROMs don't implement the per-app variant — fall back to the general list.
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
    }
}
