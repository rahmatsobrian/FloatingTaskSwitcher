package com.siroha.resourcetransfer.domain.transport

import com.siroha.resourcetransfer.domain.model.ConnectionState
import com.siroha.resourcetransfer.domain.model.DeviceInfo
import com.siroha.resourcetransfer.domain.model.TransportType
import kotlinx.coroutines.flow.Flow

/**
 * Contract every connection method (Wi-Fi Direct, Nearby, Hotspot, LAN,
 * QR, Bluetooth, Manual IP) must implement. TransportManager treats all
 * seven identically through this interface, which is what makes the
 * automatic fallback chain possible.
 */
interface Transport {
    val type: TransportType

    /** Cheap runtime check: does this device/ROM actually support this method right now? */
    suspend fun isAvailable(): Boolean

    /** Begin advertising/discovering nearby devices. Emits found devices as they appear. */
    fun discover(): Flow<DeviceInfo>

    /** Connect to a specific discovered (or manually entered) device. */
    suspend fun connect(target: DeviceInfo): Result<Unit>

    /** Open a raw byte channel once connected — used by TransferEngine for chunk I/O. */
    suspend fun openChannel(): Result<TransportChannel>

    fun observeState(): Flow<ConnectionState>

    suspend fun disconnect()
}

/** Minimal abstraction over a bidirectional byte stream, regardless of underlying transport. */
interface TransportChannel {
    suspend fun write(bytes: ByteArray, offset: Int, length: Int)
    suspend fun read(buffer: ByteArray): Int
    suspend fun close()
}
