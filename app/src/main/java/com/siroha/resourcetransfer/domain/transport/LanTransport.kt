package com.siroha.resourcetransfer.domain.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.siroha.resourcetransfer.domain.model.ConnectionState
import com.siroha.resourcetransfer.domain.model.DeviceInfo
import com.siroha.resourcetransfer.domain.model.TransportType
import com.siroha.resourcetransfer.util.AppLogger
import com.siroha.resourcetransfer.util.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priority #4 fallback, and — alongside Manual IP — the other fully
 * working transport in this scaffold. Uses NsdManager (mDNS/DNS-SD) so
 * two devices on the same Wi-Fi router can find each other without
 * anyone typing an IP address, then hands off to a plain TCP socket on
 * the SAME port [ManualIpTransport.PORT] uses.
 *
 * IMPORTANT threading note (source of an earlier crash): NsdManager's
 * registerService/discoverServices/resolveService calls internally post
 * to a Handler tied to the calling thread — calling them from a plain
 * background thread (e.g. Dispatchers.Default, which has no Looper)
 * can throw `RuntimeException: Can't create handler inside thread that
 * has not called Looper.prepare()`. Every NsdManager call in this class
 * is therefore explicitly run on Dispatchers.Main.
 */
@Singleton
class LanTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLogger: AppLogger
) : Transport {

    override val type = TransportType.WIFI_LAN
    private val stateFlow = MutableStateFlow(ConnectionState.IDLE)
    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }

    @Volatile private var activeSocket: Socket? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile private var isRegistered = false

    override suspend fun isAvailable(): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true && nsdManager != null
    }

    /** Sender side: browse for devices currently advertising via [registerService]. */
    override fun discover(): Flow<DeviceInfo> = callbackFlow {
        val mgr = nsdManager
        if (mgr == null) {
            close()
            return@callbackFlow
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                stateFlow.value = ConnectionState.DISCOVERING
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != SERVICE_TYPE && "$SERVICE_TYPE." != service.serviceType) return
                runCatching {
                    mgr.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            // Ignore — device may have gone offline between found/resolve.
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            trySend(
                                DeviceInfo(
                                    deviceId = host,
                                    name = serviceInfo.serviceName ?: host,
                                    model = "-",
                                    androidVersion = "-",
                                    transportType = TransportType.WIFI_LAN,
                                    ipAddress = host
                                )
                            )
                        }
                    })
                }.onFailure { appLogger.log(LogLevel.WARNING, TAG, "resolveService gagal", it) }
            }

            override fun onServiceLost(service: NsdServiceInfo) { /* not modeled — UI just keeps last-seen list */ }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                appLogger.log(LogLevel.WARNING, TAG, "onStartDiscoveryFailed: $errorCode")
                stateFlow.value = ConnectionState.FAILED
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        // NsdManager calls MUST run on a thread with a Looper (main thread here) —
        // see class doc. callbackFlow's producer block itself runs on whatever
        // dispatcher the collector used, so we can't assume it's already Main.
        withContext(Dispatchers.Main) {
            runCatching { mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
                .onFailure { e ->
                    appLogger.log(LogLevel.ERROR, TAG, "discoverServices gagal", e)
                    stateFlow.value = ConnectionState.FAILED
                    close()
                }
        }

        awaitClose {
            runCatching { mgr.stopServiceDiscovery(discoveryListener) }
        }
    }

    /**
     * Receiver side: advertises this device on the local network so senders
     * running [discover] can find it. Suspend + Dispatchers.Main for the
     * same Looper reason described in the class doc. Safe to call even if
     * NSD isn't available (e.g. no Wi-Fi) — failures are logged, not thrown;
     * Manual IP still works without this.
     */
    suspend fun registerService(deviceName: String, port: Int) = withContext(Dispatchers.Main) {
        val mgr = nsdManager ?: return@withContext
        if (isRegistered) return@withContext

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName.ifBlank { "ResourceTransfer" }
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                isRegistered = true
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                appLogger.log(LogLevel.WARNING, TAG, "onRegistrationFailed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                isRegistered = false
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching { mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { appLogger.log(LogLevel.WARNING, TAG, "registerService gagal, lanjut tanpa LAN discovery", it) }
    }

    suspend fun unregisterService() = withContext(Dispatchers.Main) {
        val mgr = nsdManager ?: return@withContext
        registrationListener?.let { listener ->
            runCatching { mgr.unregisterService(listener) }
        }
        registrationListener = null
        isRegistered = false
    }

    override suspend fun connect(target: DeviceInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val ip = target.ipAddress
        if (ip.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Alamat IP perangkat LAN kosong"))
        }
        stateFlow.value = ConnectionState.CONNECTING
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, ManualIpTransport.PORT), CONNECT_TIMEOUT_MS)
            activeSocket = socket
            stateFlow.value = ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            stateFlow.value = ConnectionState.FAILED
            Result.failure(e)
        }
    }

    override suspend fun openChannel(): Result<TransportChannel> {
        val socket = activeSocket ?: return Result.failure(IllegalStateException("Belum ada koneksi aktif"))
        return Result.success(SocketTransportChannel(socket))
    }

    override fun observeState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            runCatching { activeSocket?.close() }
            activeSocket = null
        }
        unregisterService()
        stateFlow.value = ConnectionState.DISCONNECTED
    }

    companion object {
        private const val TAG = "LanTransport"
        const val SERVICE_TYPE = "_resourcetransfer._tcp"
        const val CONNECT_TIMEOUT_MS = 8000
    }
}
