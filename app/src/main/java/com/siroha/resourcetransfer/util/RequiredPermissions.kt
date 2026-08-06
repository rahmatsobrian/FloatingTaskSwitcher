package com.siroha.resourcetransfer.util

import android.Manifest
import android.os.Build

/**
 * Central source of truth for which *runtime* (dangerous-protection-level)
 * permissions this app needs, since the right set changes across API 26–16
 * exactly like the manifest's `<uses-permission>` block does — the manifest
 * declares the ceiling of what the app is allowed to ask for, this decides
 * what to actually prompt for on the device's current OS version.
 *
 * Notably excluded: storage permissions. The app relies on SAF
 * (ActivityResultContracts.OpenDocumentTree) for folder access instead of
 * READ/WRITE_EXTERNAL_STORAGE, so those never need a runtime prompt.
 */
object RequiredPermissions {

    /** Permissions needed for Wi-Fi Direct / Nearby / LAN discovery to actually find devices. */
    fun forDiscovery(): List<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            // Android <= 12L: the platform ties Wi-Fi P2P / mDNS scan results to
            // location permission, even though this app never reads GPS location.
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return permissions
    }

    /** Permissions needed for the Bluetooth fallback transport. */
    fun forBluetooth(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyList() // BLUETOOTH/BLUETOOTH_ADMIN are normal-protection-level pre-API31, granted at install time
        }
    }

    /** Permissions needed for direct java.io.File access on API <= 32 (API 33+ uses granular media perms that don't apply to arbitrary-path folder access — MANAGE_EXTERNAL_STORAGE, requested separately, is the real gate there). */
    fun forLegacyStorage(): List<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            emptyList()
        }
    }

    /** Needed so the foreground-service transfer progress notification can actually show. */
    fun forNotifications(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    }

    /** The full set this app prompts for up front — kept to what Send/Receive actually need right away. */
    fun all(): Array<String> {
        return (forDiscovery() + forBluetooth() + forNotifications() + forLegacyStorage()).distinct().toTypedArray()
    }
}
