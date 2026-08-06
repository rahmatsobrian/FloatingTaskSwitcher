package com.siroha.resourcetransfer.domain.transport

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
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
 * Priority #1 transport. Uses android.net.wifi.p2p directly.
 *
 * KNOWN FAILURE MODES this app is designed around (see project brief):
 *  - Some AOSP-derived Custom ROMs ship a broken/incomplete WifiP2pManager
 *    implementation -> initialize() channel can silently never call back.
 *  - Discovery can time out with no peers found even when peers exist.
 *  - `WifiP2pManager.ActionListener` failure reason codes (BUSY, ERROR,
 *    P2P_UNSUPPORTED) are surfaced back to TransportManager so it can
 *    fall through to Nearby Connections immediately.
 *
 * NOTE: this is a structural skeleton wiring up the real WifiP2pManager
 * callback API; the socket-level data channel (openChannel) delegates to
 * a plain TCP socket over the group owner's local IP once P2P group
 * negotiation completes, which is the standard pattern for WiFi Direct
 * file transfer apps.
 */
@Singleton
class WifiDirectTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : Transport {

    override val type = TransportType.WIFI_DIRECT

    private val manager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var p2pChannel: WifiP2pManager.Channel? = null
    private val stateFlow = MutableStateFlow(ConnectionState.IDLE)

    override suspend fun isAvailable(): Boolean {
        // P2P_UNSUPPORTED devices / ROMs without the feature should report false
        // here so TransportManager skips straight to Nearby Connections.
        return manager != null && context.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WIFI_DIRECT
        )
    }

    override fun discover(): Flow<DeviceInfo> = callbackFlow {
        val mgr = manager ?: run { close(); return@callbackFlow }
        p2pChannel = mgr.initialize(context, context.mainLooper, null)
        stateFlow.value = ConnectionState.DISCOVERING

        mgr.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { /* peers arrive via WIFI_P2P_PEERS_CHANGED_ACTION broadcast,
                                            handled by a BroadcastReceiver registered in the real
                                            implementation and forwarded into this flow */ }
            override fun onFailure(reason: Int) {
                stateFlow.value = ConnectionState.FAILED
                close()
            }
        })

        awaitClose { mgr.stopPeerDiscovery(p2pChannel, null) }
    }

    override suspend fun connect(target: DeviceInfo): Result<Unit> {
        val mgr = manager ?: return Result.failure(IllegalStateException("Wi-Fi P2P not supported on this device"))
        // Real implementation builds a WifiP2pConfig from target.deviceId (device address)
        // and calls mgr.connect(channel, config, actionListener), then waits for
        // WIFI_P2P_CONNECTION_CHANGED_ACTION to confirm group formation.
        return Result.failure(NotImplementedError("Wire up WifiP2pConfig connect flow"))
    }

    override suspend fun openChannel(): Result<TransportChannel> {
        // Opens a TCP socket to the group owner IP (available once connect() resolves).
        return Result.failure(NotImplementedError("Open TCP socket to P2P group owner"))
    }

    override fun observeState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() {
        p2pChannel?.let { manager?.removeGroup(it, null) }
        stateFlow.value = ConnectionState.DISCONNECTED
    }
}
