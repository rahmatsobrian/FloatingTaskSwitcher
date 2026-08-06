package com.siroha.resourcetransfer.domain.engine

import com.siroha.resourcetransfer.domain.model.FileHeader
import com.siroha.resourcetransfer.domain.model.FileRequest
import com.siroha.resourcetransfer.domain.model.ManifestFile
import com.siroha.resourcetransfer.domain.model.TransferManifest
import com.siroha.resourcetransfer.domain.model.TransferProgress
import com.siroha.resourcetransfer.domain.model.TransferStatus
import com.siroha.resourcetransfer.domain.transport.ChannelFraming
import com.siroha.resourcetransfer.domain.transport.TransportChannel
import com.siroha.resourcetransfer.util.AppLogger
import com.siroha.resourcetransfer.util.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the actual byte-moving logic: manifest building/diffing, the
 * sender/receiver wire protocol (length-prefixed JSON control messages +
 * raw file bytes over a [TransportChannel]), checksum verification, and
 * pause/cancel signalling. Transport-agnostic on purpose — it only needs
 * an already-open [TransportChannel], so the same session logic works
 * over LAN, Manual IP, or (once implemented) any other transport.
 */
@Singleton
class TransferEngine @Inject constructor(
    private val appLogger: AppLogger
) {
    companion object {
        const val DEFAULT_CHUNK_SIZE = 512 * 1024 // 512 KB, configurable via Settings
        private const val TAG = "TransferEngine"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _progress = MutableStateFlow(
        TransferProgress(sessionId = "", status = TransferStatus.QUEUED)
    )
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    private val controlMutex = Mutex()
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false

    /** Resets pause/cancel flags before starting a fresh session. */
    fun resetControls() {
        isPaused = false
        isCancelled = false
    }

    // -------------------- Manifest --------------------

    /**
     * Scans [sourceFolder] and builds a manifest with size + SHA256 + CRC32
     * per file, so the Receiver can diff against what it already has and
     * request only the missing/corrupted files.
     *
     * @param rootLabel When sending a named folder (vs. loose files), this
     * prefixes every relative path with the folder's own name — e.g.
     * "Garena" — so the receiver reconstructs `DestFolder/Garena/...`
     * instead of dumping the folder's contents directly into the
     * destination root. Left null for loose files/media/text/apk sends,
     * which should land flat in the destination folder.
     */
    suspend fun buildManifest(
        sessionId: String,
        deviceName: String,
        sourceFolder: File,
        rootLabel: String? = null
    ): TransferManifest {
        val files = sourceFolder.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val relativePath = file.relativeTo(sourceFolder).path
                val path = if (rootLabel != null) "$rootLabel/$relativePath" else relativePath
                ManifestFile(
                    path = path,
                    size = file.length(),
                    sha256 = sha256Of(file),
                    crc32 = crc32Of(file)
                )
            }
            .toList()
        return TransferManifest(sessionId = sessionId, deviceName = deviceName, files = files)
    }

    /** Compares an incoming manifest against local files already present in [destFolder]. */
    fun diffManifest(manifest: TransferManifest, destFolder: File): List<String> {
        return manifest.files.filter { entry ->
            val local = File(destFolder, entry.path)
            !local.exists() || local.length() != entry.size || sha256Of(local) != entry.sha256
        }.map { it.path }
    }

    // -------------------- Sender session --------------------

    /**
     * Full sender-side protocol run over an already-connected [channel]:
     * scan+hash the source folder, send the manifest, wait for the
     * receiver's file request, then stream only the requested files.
     * Returns the number of files actually sent.
     */
    suspend fun runSenderSession(
        channel: TransportChannel,
        sourceFolder: File,
        deviceName: String,
        rootLabel: String? = null,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): Int {
        resetControls()
        val sessionId = System.currentTimeMillis().toString()

        _progress.value = TransferProgress(
            sessionId = sessionId,
            status = TransferStatus.RUNNING,
            currentFileName = "Membaca folder & menghitung checksum..."
        )
        val manifest = buildManifest(sessionId, deviceName, sourceFolder, rootLabel)
        appLogger.log(LogLevel.INFO, TAG, "Manifest dibuat: ${manifest.files.size} file")

        if (manifest.files.isEmpty()) {
            _progress.value = _progress.value.copy(status = TransferStatus.FAILED)
            throw IllegalStateException(
                "0 file ditemukan di folder sumber. Kemungkinan besar folder ini kosong, atau " +
                    "aplikasi belum punya izin \"Akses semua file\" untuk membaca isinya (folder " +
                    "kelihatan kepilih tapi isinya tidak terbaca)."
            )
        }

        _progress.value = _progress.value.copy(currentFileName = "Mengirim manifest, menunggu penerima...")
        ChannelFraming.writeFrame(channel, json.encodeToString(manifest).encodeToByteArray())

        val requestBytes = ChannelFraming.readFrame(channel)
        val request = json.decodeFromString(FileRequest.serializer(), requestBytes.decodeToString())
        appLogger.log(LogLevel.INFO, TAG, "Penerima meminta ${request.requestedPaths.size} file")

        val filesByPath = manifest.files.associateBy { it.path }
        val totalBytes = request.requestedPaths.sumOf { filesByPath[it]?.size ?: 0L }
        _progress.value = _progress.value.copy(
            totalFiles = request.requestedPaths.size,
            totalBytes = totalBytes,
            transferredBytes = 0L
        )

        // Manifest paths may carry a "rootLabel/" prefix (see buildManifest doc) that only
        // exists for the receiver's benefit — strip it back off to find the real on-disk file.
        val labelPrefix = rootLabel?.let { "$it/" }

        var transferredSoFar = 0L
        for ((index, path) in request.requestedPaths.withIndex()) {
            waitWhilePaused()
            if (isCancelled) {
                _progress.value = _progress.value.copy(status = TransferStatus.CANCELLED)
                return index
            }

            val onDiskRelativePath = if (labelPrefix != null && path.startsWith(labelPrefix)) {
                path.removePrefix(labelPrefix)
            } else {
                path
            }
            val file = File(sourceFolder, onDiskRelativePath)
            _progress.value = _progress.value.copy(currentFileName = file.name, currentFileIndex = index + 1)

            ChannelFraming.writeFrame(channel, json.encodeToString(FileHeader(path, file.length())).encodeToByteArray())

            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(chunkSize)
                var sentInFile = 0L
                while (sentInFile < file.length()) {
                    waitWhilePaused()
                    if (isCancelled) {
                        _progress.value = _progress.value.copy(status = TransferStatus.CANCELLED)
                        return index
                    }
                    val read = raf.read(buffer)
                    if (read <= 0) break
                    channel.write(buffer, 0, read)
                    sentInFile += read
                    updateSpeedAndEta(transferredSoFar + sentInFile, totalBytes)
                }
            }
            transferredSoFar += file.length()
        }

        _progress.value = _progress.value.copy(status = TransferStatus.COMPLETED)
        appLogger.log(LogLevel.INFO, TAG, "Pengiriman selesai: ${request.requestedPaths.size} file")
        return request.requestedPaths.size
    }

    // -------------------- Receiver session --------------------

    /**
     * Full receiver-side protocol run over an already-accepted [channel]:
     * read the sender's manifest, diff it against [destFolder], request
     * only what's missing, then receive + verify each file. Returns the
     * manifest that was received (for history logging).
     */
    suspend fun runReceiverSession(
        channel: TransportChannel,
        destFolder: File,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): TransferManifest {
        resetControls()

        _progress.value = TransferProgress(
            sessionId = "",
            status = TransferStatus.RUNNING,
            currentFileName = "Menerima manifest dari pengirim..."
        )
        val manifestBytes = ChannelFraming.readFrame(channel)
        val manifest = json.decodeFromString(TransferManifest.serializer(), manifestBytes.decodeToString())
        appLogger.log(LogLevel.INFO, TAG, "Manifest diterima dari ${manifest.deviceName}: ${manifest.files.size} file")

        destFolder.mkdirs()
        val requestedPaths = diffManifest(manifest, destFolder)
        appLogger.log(LogLevel.INFO, TAG, "${requestedPaths.size} file perlu diminta dari ${manifest.files.size} total")

        ChannelFraming.writeFrame(
            channel,
            json.encodeToString(FileRequest(manifest.sessionId, requestedPaths)).encodeToByteArray()
        )

        val filesByPath = manifest.files.associateBy { it.path }
        val totalBytes = requestedPaths.sumOf { filesByPath[it]?.size ?: 0L }
        _progress.value = _progress.value.copy(
            sessionId = manifest.sessionId,
            totalFiles = requestedPaths.size,
            totalBytes = totalBytes,
            transferredBytes = 0L
        )

        var transferredSoFar = 0L
        for (index in requestedPaths.indices) {
            waitWhilePaused()
            if (isCancelled) {
                _progress.value = _progress.value.copy(status = TransferStatus.CANCELLED)
                return manifest
            }

            val headerBytes = ChannelFraming.readFrame(channel)
            val header = json.decodeFromString(FileHeader.serializer(), headerBytes.decodeToString())
            val destFile = File(destFolder, header.path)
            destFile.parentFile?.mkdirs()

            _progress.value = _progress.value.copy(
                status = TransferStatus.RUNNING,
                currentFileName = destFile.name,
                currentFileIndex = index + 1
            )

            FileOutputStream(destFile).use { out ->
                val buffer = ByteArray(chunkSize)
                var receivedInFile = 0L
                while (receivedInFile < header.size) {
                    waitWhilePaused()
                    if (isCancelled) {
                        _progress.value = _progress.value.copy(status = TransferStatus.CANCELLED)
                        return manifest
                    }
                    val toRead = minOf(buffer.size.toLong(), header.size - receivedInFile).toInt()
                    val tempBuffer = if (toRead == buffer.size) buffer else ByteArray(toRead)
                    val read = channel.read(tempBuffer)
                    if (read <= 0) break
                    out.write(tempBuffer, 0, read)
                    receivedInFile += read
                    updateSpeedAndEta(transferredSoFar + receivedInFile, totalBytes)
                }
            }
            transferredSoFar += header.size

            val expectedEntry = filesByPath[header.path]
            if (expectedEntry != null) {
                _progress.value = _progress.value.copy(status = TransferStatus.VERIFYING)
                val ok = verifyFile(destFile, expectedEntry.sha256)
                if (!ok) {
                    appLogger.log(LogLevel.ERROR, TAG, "Checksum tidak cocok untuk ${header.path}")
                }
                _progress.value = _progress.value.copy(status = TransferStatus.RUNNING)
            }
        }

        _progress.value = _progress.value.copy(status = TransferStatus.COMPLETED)
        appLogger.log(LogLevel.INFO, TAG, "Penerimaan selesai: ${requestedPaths.size} file")
        return manifest
    }

    /** After receiving bytes, verify integrity before considering a file "done". */
    fun verifyFile(file: File, expectedSha256: String): Boolean {
        return sha256Of(file) == expectedSha256
    }

    // -------------------- Controls --------------------

    suspend fun pause() = controlMutex.withLock {
        isPaused = true
        _progress.value = _progress.value.copy(status = TransferStatus.PAUSED)
    }

    suspend fun resume() = controlMutex.withLock {
        isPaused = false
        _progress.value = _progress.value.copy(status = TransferStatus.RUNNING)
    }

    suspend fun cancel() = controlMutex.withLock {
        isCancelled = true
    }

    private suspend fun waitWhilePaused() {
        while (isPaused && !isCancelled) {
            kotlinx.coroutines.delay(200)
        }
    }

    private var lastTickBytes = 0L
    private var lastTickTime = System.currentTimeMillis()

    private fun updateSpeedAndEta(transferred: Long, total: Long) {
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - lastTickTime).coerceAtLeast(1)) / 1000.0
        val deltaBytes = transferred - lastTickBytes
        val speed = (deltaBytes / elapsedSec).toLong().coerceAtLeast(0)
        val remaining = (total - transferred).coerceAtLeast(0)
        val eta = if (speed > 0) remaining / speed else 0L

        _progress.value = _progress.value.copy(
            transferredBytes = transferred,
            totalBytes = total,
            speedBytesPerSec = speed,
            etaSeconds = eta
        )
        lastTickBytes = transferred
        lastTickTime = now
    }

    // -------------------- Hashing --------------------

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun crc32Of(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) {
                crc.update(buffer, 0, read)
            }
        }
        return crc.value
    }
}
