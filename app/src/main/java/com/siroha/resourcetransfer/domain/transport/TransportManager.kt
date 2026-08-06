package com.siroha.resourcetransfer.domain.transport

import com.siroha.resourcetransfer.domain.model.ConnectionState
import com.siroha.resourcetransfer.domain.model.DeviceInfo
import com.siroha.resourcetransfer.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the "try best method first, fall back automatically"
 * requirement. This is the piece that makes the app more resilient than
 * MLBB's built-in Resource Transfer on Custom ROMs where Wi-Fi Direct is
 * flaky: if WIFI_DIRECT fails (discovery timeout, connect error, or the
 * ROM simply not implementing P2P correctly) we move to the next method
 * in [TransportType.fallbackOrder] WITHOUT losing the transfer's chunk
 * offset — TransferEngine keeps state independent of which Transport
 * carries the bytes.
 */
@Singleton
class TransportManager @Inject constructor(
    private val transports: Map<TransportType, @JvmSuppressWildcards Transport>
) {
    private val _state = MutableStateFlow(ConnectionState.IDLE)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _activeTransport = MutableStateFlow<TransportType?>(null)
    val activeTransport: StateFlow<TransportType?> = _activeTransport.asStateFlow()

    /**
     * Attempts each transport in priority order until one successfully
     * connects to [target]. Returns the Transport that succeeded so the
     * caller (TransferEngine) can open a channel on it.
     */
    suspend fun connectWithFallback(target: DeviceInfo): Result<Transport> {
        _state.value = ConnectionState.CONNECTING
        val order = target.transportType?.let { listOf(it) } ?: TransportType.fallbackOrder()

        var lastError: Throwable? = null
        for (transportType in order) {
            val transport = transports[transportType] ?: continue
            if (!transport.isAvailable()) continue

            val result = transport.connect(target)
            if (result.isSuccess) {
                _activeTransport.value = transportType
                _state.value = ConnectionState.CONNECTED
                return Result.success(transport)
            } else {
                lastError = result.exceptionOrNull()
                // fall through to next method in priority order
            }
        }
        _state.value = ConnectionState.FAILED
        return Result.failure(lastError ?: IllegalStateException("No transport method succeeded"))
    }

    /**
     * Called by TransferEngine mid-transfer when the active channel drops.
     * Re-runs the fallback chain starting from the method AFTER the one
     * that just failed, so we don't retry a method that just proved broken.
     */
    suspend fun reconnectSkipping(failed: TransportType, target: DeviceInfo): Result<Transport> {
        _state.value = ConnectionState.RECONNECTING
        val remaining = TransportType.fallbackOrder().filter { it.priority > failed.priority }
        for (transportType in remaining) {
            val transport = transports[transportType] ?: continue
            if (!transport.isAvailable()) continue
            val result = transport.connect(target)
            if (result.isSuccess) {
                _activeTransport.value = transportType
                _state.value = ConnectionState.CONNECTED
                return Result.success(transport)
            }
        }
        _state.value = ConnectionState.FAILED
        return Result.failure(IllegalStateException("All remaining transports exhausted"))
    }

    suspend fun disconnectAll() {
        transports.values.forEach { it.disconnect() }
        _activeTransport.value = null
        _state.value = ConnectionState.DISCONNECTED
    }
}
