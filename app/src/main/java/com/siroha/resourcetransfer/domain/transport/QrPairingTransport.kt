package com.siroha.resourcetransfer.domain.transport

import android.content.Context
import com.siroha.resourcetransfer.domain.model.ConnectionState
import com.siroha.resourcetransfer.domain.model.DeviceInfo
import com.siroha.resourcetransfer.domain.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priority #5. Not a data-carrying transport by itself — QR encodes the
 * connection parameters (SSID/password, IP:port, or a device/session code)
 * for WHICHEVER underlying transport the sender chose, so the receiver can
 * pair by scanning instead of typing. Once paired, the actual bytes travel
 * over LAN/Hotspot socket. Falls back to on-screen PIN/device-code entry
 * if the camera permission is denied.
 */
@Singleton
class QrPairingTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : Transport {

    override val type = TransportType.QR_PAIRING
    private val stateFlow = MutableStateFlow(ConnectionState.IDLE)

    override suspend fun isAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }

    override fun discover(): Flow<DeviceInfo> = callbackFlow {
        // No active discovery: pairing data comes from decoding a scanned QR
        // (ZXing) which is fed in via connect(target) with target.ipAddress/deviceId set.
        awaitClose { }
    }

    override suspend fun connect(target: DeviceInfo): Result<Unit> {
        return Result.failure(NotImplementedError("Decode QR payload, then delegate socket connect to LanTransport/HotspotTransport"))
    }

    override suspend fun openChannel(): Result<TransportChannel> {
        return Result.failure(NotImplementedError("Delegates to underlying transport once paired"))
    }

    override fun observeState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() {
        stateFlow.value = ConnectionState.DISCONNECTED
    }
}
