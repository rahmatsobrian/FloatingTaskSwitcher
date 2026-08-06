package com.siroha.resourcetransfer.domain.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Strategy
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
 * Priority #2 fallback. Uses Google Play Services Nearby Connections API
 * (P2P_STAR strategy), which internally negotiates Wi-Fi/Bluetooth on our
 * behalf and is far more tolerant of vendor ROM quirks than raw WifiP2pManager,
 * since Google maintains the compatibility shims across OEMs.
 *
 * Requires Google Play Services -> if unavailable (rare on custom AOSP builds
 * without gapps), isAvailable() returns false and TransportManager continues
 * to LOCAL_HOTSPOT.
 */
@Singleton
class NearbyConnectionsTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : Transport {

    override val type = TransportType.NEARBY_CONNECTIONS
    private val stateFlow = MutableStateFlow(ConnectionState.IDLE)
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val strategy = Strategy.P2P_STAR

    override suspend fun isAvailable(): Boolean {
        return try {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    override fun discover(): Flow<DeviceInfo> = callbackFlow {
        stateFlow.value = ConnectionState.DISCOVERING
        // Real implementation calls connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
        // and emits a DeviceInfo for each onEndpointFound callback.
        awaitClose { connectionsClient.stopDiscovery() }
    }

    override suspend fun connect(target: DeviceInfo): Result<Unit> {
        return Result.failure(NotImplementedError("Wire up connectionsClient.requestConnection + payload callback"))
    }

    override suspend fun openChannel(): Result<TransportChannel> {
        return Result.failure(NotImplementedError("Wrap Nearby PayloadTransferUpdate stream as TransportChannel"))
    }

    override fun observeState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() {
        connectionsClient.stopAllEndpoints()
        stateFlow.value = ConnectionState.DISCONNECTED
    }

    companion object {
        const val SERVICE_ID = "com.siroha.resourcetransfer.NEARBY"
    }
}
