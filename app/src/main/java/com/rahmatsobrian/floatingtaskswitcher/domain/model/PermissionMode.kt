package com.rahmatsobrian.floatingtaskswitcher.domain.model

/**
 * The three operating modes described in the product spec. The app always
 * picks the highest-privilege mode that is actually available, and can
 * downgrade at runtime (e.g. Shizuku binder dies) without a restart.
 */
enum class OperatingMode {
    ROOT,
    SHIZUKU,
    STANDARD,
}

enum class PermissionStatus {
    GRANTED,
    NOT_GRANTED,
    UNSUPPORTED,
    AVAILABLE,
    CHECKING,
}

data class PermissionSnapshot(
    val overlay: PermissionStatus,
    val accessibility: PermissionStatus,
    val usageAccess: PermissionStatus,
    val notification: PermissionStatus,
    val batteryOptimizationExempt: PermissionStatus,
    val autoStart: PermissionStatus,
    val shizuku: PermissionStatus,
    val root: PermissionStatus,
) {
    /** Everything required for Standard Mode to function correctly. */
    val hasStandardModeEssentials: Boolean
        get() = overlay == PermissionStatus.GRANTED &&
            usageAccess == PermissionStatus.GRANTED &&
            notification != PermissionStatus.NOT_GRANTED
}
