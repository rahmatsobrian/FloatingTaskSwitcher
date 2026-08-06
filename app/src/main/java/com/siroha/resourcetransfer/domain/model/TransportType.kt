package com.siroha.resourcetransfer.domain.model

/**
 * All supported transport methods, declared in fallback priority order.
 * TransportManager iterates this list top-to-bottom, skipping any method
 * whose [TransportCapability] check fails on the current device/ROM,
 * and falling back automatically on runtime failure without losing
 * transfer progress (chunk offset is preserved across method switches).
 */
enum class TransportType(val displayName: String, val priority: Int) {
    WIFI_DIRECT("Wi-Fi Direct (P2P)", 1),
    NEARBY_CONNECTIONS("Nearby Connections", 2),
    LOCAL_HOTSPOT("Local Hotspot", 3),
    WIFI_LAN("Wi-Fi LAN", 4),
    QR_PAIRING("QR Pairing", 5),
    BLUETOOTH("Bluetooth", 6),
    MANUAL_IP("Manual IP", 7);

    companion object {
        fun fallbackOrder(): List<TransportType> = entries.sortedBy { it.priority }
    }
}

enum class ConnectionState {
    IDLE, DISCOVERING, PAIRING, CONNECTING, CONNECTED, TRANSFERRING, RECONNECTING, DISCONNECTED, FAILED
}

data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val model: String,
    val androidVersion: String,
    val batteryPercent: Int? = null,
    val signalLevel: Int? = null,
    val transportType: TransportType? = null,
    val ipAddress: String? = null
)
