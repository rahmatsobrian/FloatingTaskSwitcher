package com.siroha.resourcetransfer.domain.transport

import android.bluetooth.BluetoothAdapter
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
 * Priority #6. Slowest method but nearly universally available — kept as
 * a near-last-resort fallback for very old/locked-down devices where every
 * Wi-Fi-based method is blocked by ROM policy. Uses classic Bluetooth RFCOMM
 * sockets (SPP profile) rather than BLE, since we need throughput for file
 * bytes, not just small packets.
 */
@Singleton
class BluetoothTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : Transport {

    override val type = TransportType.BLUETOOTH
    private val stateFlow = MutableStateFlow(ConnectionState.IDLE)
    private val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    override suspend fun isAvailable(): Boolean {
        return adapter?.isEnabled == true
    }

    override fun discover(): Flow<DeviceInfo> = callbackFlow {
        val bt = adapter ?: run { close(); return@callbackFlow }
        // Real implementation registers a BroadcastReceiver for ACTION_FOUND
        // after bt.startDiscovery(), emitting a DeviceInfo per discovered device.
        awaitClose { bt.cancelDiscovery() }
    }

    override suspend fun connect(target: DeviceInfo): Result<Unit> {
        return Result.failure(NotImplementedError("BluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)"))
    }

    override suspend fun openChannel(): Result<TransportChannel> {
        return Result.failure(NotImplementedError("Wrap BluetoothSocket streams as TransportChannel"))
    }

    override fun observeState(): Flow<ConnectionState> = stateFlow

    override suspend fun disconnect() {
        stateFlow.value = ConnectionState.DISCONNECTED
    }

    companion object {
        val SPP_UUID: java.util.UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
