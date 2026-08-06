package com.siroha.resourcetransfer.domain.transport

import android.content.Context
import com.siroha.resourcetransfer.domain.model.ConnectionState
import com.siroha.resourcetransfer.domain.model.DeviceInfo
import com.siroha.resourcetransfer.domain.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Priority #7 — and, for now, the ONE fully working transport in this
 * scaffold. User types the receiver's IP address directly (shown on the
 * Receive screen). No discovery, no permissions beyond basic network
 * access — this is what keeps a transfer possible even when every
 * automatic discovery method fails, and it's also the simplest path to
 * verify the send/receive protocol end-to-end.
 *
 * @Singleton: this instance is shared between TransportManager's
 * multibinding map AND any ViewModel that injects ManualIpTransport
 * directly for its receiver-only listenForSender() API (which isn't part
 * of the generic Transport interface) — without this scope they'd be two
 * separate objects holding two different sockets.
 */
@Singleton
class ManualIpTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : Transport {

    override val type = TransportType.MANUAL_IP
    private val stateFlow = MutableStateFlow(ConnectionState.IDLE)

    @Volatile private var activeSocket: Socket? = null
    @Volatile private var serverSocket: ServerSocket? = null

    override suspend fun isAvailable(): Boolean = true // always available as last resort

    override fun discover(): Flow<DeviceInfo> = callbackFlow {
        // Nothing to discover; user supplies the IP directly via UI.
        awaitClose { }
    }

    /** Sender side: connect out to the receiver's IP. */
    override suspend fun connect(target: DeviceInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val ip = target.ipAddress
        if (ip.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Alamat IP tujuan kosong"))
        }
        stateFlow.value = ConnectionState.CONNECTING
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, PORT), CONNECT_TIMEOUT_MS)
            activeSocket = socket
            stateFlow.value = ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            stateFlow.value = ConnectionState.FAILED
            Result.failure(e)
        }
    }

    /**
     * Receiver side: block until a sender connects. Deliberately not part
     * of the [Transport] interface — "listen for an inbound connection" is
     * a distinct role from "discover + connect outward" that the other six
     * transports model, so ViewModels needing this call ManualIpTransport
     * directly rather than through TransportManager.
     */
    suspend fun listenForSender(): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        stateFlow.value = ConnectionState.DISCOVERING
        try {
            val server = ServerSocket(PORT)
            serverSocket = server
            val socket = server.accept()
            activeSocket = socket
            stateFlow.value = ConnectionState.CONNECTED
            Result.success(
                DeviceInfo(
                    deviceId = socket.inetAddress.hostAddress ?: "unknown",
                    name = socket.inetAddress.hostAddress ?: "Pengirim",
                    model = "-",
                    androidVersion = "-",
                    transportType = TransportType.MANUAL_IP,
                    ipAddress = socket.inetAddress.hostAddress
                )
            )
        } catch (e: Exception) {
            stateFlow.value = ConnectionState.FAILED
            Result.failure(e)
        } finally {
            runCatching { serverSocket?.close() }
            serverSocket = null
        }
    }

    /** Stops an in-progress listenForSender() wait — e.g. if the user cancels from the UI. */
    fun cancelListening() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    override suspend fun openChannel(): Result<TransportChannel> {
        val socket = activeSocket ?: return Result.failure(IllegalStateException("Belum ada koneksi aktif"))
        return Result.success(SocketTransportChannel(socket))
    }

    override fun observeState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { activeSocket?.close() }
        runCatching { serverSocket?.close() }
        activeSocket = null
        serverSocket = null
        stateFlow.value = ConnectionState.DISCONNECTED
    }

    /** Best-effort local IPv4 address, shown on the Receive screen so the
     *  other device's user knows what to type on the Send screen. */
    fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filterNot { it.isLoopbackAddress }
                .filter { it.hostAddress?.contains(':') == false } // IPv4 only
                .firstOrNull()
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val PORT = 8988
        const val CONNECT_TIMEOUT_MS = 8000
    }
}
