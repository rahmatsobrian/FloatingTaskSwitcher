package com.siroha.resourcetransfer.domain.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.Socket

/**
 * Wraps a plain java.net.Socket as a [TransportChannel]. Shared by every
 * socket-based transport (LAN, Manual IP — and Hotspot once implemented,
 * since a local hotspot is just Wi-Fi with an extra join step before the
 * same TCP socket applies).
 */
class SocketTransportChannel(private val socket: Socket) : TransportChannel {

    private val input = socket.getInputStream()
    private val output = socket.getOutputStream()

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
        withContext(Dispatchers.IO) {
            output.write(bytes, offset, length)
            output.flush()
        }
    }

    override suspend fun read(buffer: ByteArray): Int {
        return withContext(Dispatchers.IO) {
            input.read(buffer)
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            runCatching { socket.close() }
        }
    }
}

/**
 * Simple length-prefixed framing on top of [TransportChannel], used to
 * exchange the JSON control messages (manifest, file request, per-file
 * header) that the send/receive protocol needs before raw file bytes
 * start flowing. Kept here (transport layer) rather than in the engine
 * since it's really part of "how bytes move on the wire", not transfer
 * business logic.
 */
object ChannelFraming {

    suspend fun writeFrame(channel: TransportChannel, payload: ByteArray) {
        val header = ByteArray(4)
        header[0] = (payload.size ushr 24).toByte()
        header[1] = (payload.size ushr 16).toByte()
        header[2] = (payload.size ushr 8).toByte()
        header[3] = payload.size.toByte()
        channel.write(header, 0, 4)
        if (payload.isNotEmpty()) {
            channel.write(payload, 0, payload.size)
        }
    }

    suspend fun readFrame(channel: TransportChannel): ByteArray {
        val header = ByteArray(4)
        readFully(channel, header)
        val length = ((header[0].toInt() and 0xFF) shl 24) or
            ((header[1].toInt() and 0xFF) shl 16) or
            ((header[2].toInt() and 0xFF) shl 8) or
            (header[3].toInt() and 0xFF)
        val payload = ByteArray(length)
        if (length > 0) readFully(channel, payload)
        return payload
    }

    /** Reads exactly [dest].size bytes, looping until full since a single
     *  socket read() call may return fewer bytes than requested. */
    suspend fun readFully(channel: TransportChannel, dest: ByteArray) {
        var filled = 0
        while (filled < dest.size) {
            val remaining = ByteArray(dest.size - filled)
            val read = channel.read(remaining)
            if (read <= 0) throw EOFException("Koneksi terputus saat membaca data")
            System.arraycopy(remaining, 0, dest, filled, read)
            filled += read
        }
    }
}
