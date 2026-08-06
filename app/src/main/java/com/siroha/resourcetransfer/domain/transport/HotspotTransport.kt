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
 * Priority #3 fallback. Sender spins up a Local-only Hotspot
 * (WifiManager.LocalOnlyHotspotReservation on API 26+), Receiver joins
 * manually or via a QR-encoded SSID/password, then a plain TCP socket
 * carries the transfer. Useful when both Wi-Fi Direct and Nearby fail
 * due to vendor P2P stack issues, since LocalOnlyHotspot uses standard
 * AP-mode Wi-Fi which is far more uniformly implemented across ROMs.
 */
@Singleton
class HotspotTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : Transport {

    override val type = TransportType.LOCAL_HOTSPOT
    private val stateFlow = MutableStateFlow(ConnectionState.IDLE)

    override suspend fun isAvailable(): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        return wifiManager != null
    }

    override fun discover(): Flow<DeviceInfo> = callbackFlow {
        // Hotspot mode has no "discovery" in the P2P sense: Sender starts the AP and
        // shows its SSID/password/QR; Receiver "discovers" it by scanning normal Wi-Fi
        // networks. This flow surfaces the sender's own hotspot as a pairing target.
        awaitClose { }
    }

    override suspend fun connect(target: DeviceInfo): Result<Unit> {
        return Result.failure(NotImplementedError("Start LocalOnlyHotspot (sender) / WifiNetworkSuggestion join (receiver)"))
    }

    override suspend fun openChannel(): Result<TransportChannel> {
        return Result.failure(NotImplementedError("Open TCP socket once both devices share the hotspot subnet"))
    }

    override fun observeState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() {
        stateFlow.value = ConnectionState.DISCONNECTED
    }
}
