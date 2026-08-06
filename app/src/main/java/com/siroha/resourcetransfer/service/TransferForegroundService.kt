package com.siroha.resourcetransfer.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.siroha.resourcetransfer.MainActivity
import com.siroha.resourcetransfer.R
import com.siroha.resourcetransfer.ResourceTransferApp
import com.siroha.resourcetransfer.data.local.dao.TransferHistoryDao
import com.siroha.resourcetransfer.data.local.entity.TransferHistoryEntity
import com.siroha.resourcetransfer.domain.engine.TransferEngine
import com.siroha.resourcetransfer.domain.engine.TransferSessionStatus
import com.siroha.resourcetransfer.domain.model.DeviceInfo
import com.siroha.resourcetransfer.domain.model.TransferStatus
import com.siroha.resourcetransfer.domain.model.TransportType
import com.siroha.resourcetransfer.domain.transport.LanTransport
import com.siroha.resourcetransfer.domain.transport.ManualIpTransport
import com.siroha.resourcetransfer.util.AppLogger
import com.siroha.resourcetransfer.util.LogLevel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Owns the actual send/receive execution — not the ViewModels. This is
 * what lets a transfer survive the Activity being destroyed while the app
 * is backgrounded: the coroutine doing socket I/O runs in [serviceScope],
 * tied to the Service's own lifecycle (which Android keeps alive via the
 * foreground-service contract) rather than viewModelScope, which is
 * cancelled the moment its ViewModel is cleared.
 *
 * ViewModels only (a) start this service with the parameters it needs via
 * Intent extras, and (b) observe [TransferEngine.progress] /
 * [TransferSessionStatus] — both Singletons — to reflect what's happening,
 * regardless of which process component is actually driving the transfer.
 */
@AndroidEntryPoint
class TransferForegroundService : Service() {

    @Inject lateinit var transferEngine: TransferEngine
    @Inject lateinit var sessionStatus: TransferSessionStatus
    @Inject lateinit var manualIpTransport: ManualIpTransport
    @Inject lateinit var lanTransport: LanTransport
    @Inject lateinit var historyDao: TransferHistoryDao
    @Inject lateinit var appLogger: AppLogger

    private val serviceJob = SupervisorJob()

    /**
     * Last line of defense: anything that slips past the try/catch blocks
     * in startSendJob/startReceiveJob (an unexpected exception from a
     * transport implementation, a serialization error, etc.) lands here
     * instead of crashing the whole app process. Referencing appLogger/
     * sessionStatus inside the lambda (not at property-init time) is safe
     * — Hilt injects those in onCreate(), which always runs before any
     * coroutine launched from onStartCommand() can actually fail.
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        runCatching { appLogger.log(LogLevel.ERROR, TAG, "Uncaught error in transfer service", throwable) }
        runCatching { sessionStatus.set("Terjadi kesalahan tak terduga: ${throwable.message}", isError = true) }
        stopSelf()
    }
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)
    private var transferJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(progressPercent = 0, statusText = "Menyiapkan transfer...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        observeProgress()

        when (intent?.action) {
            ACTION_START_SEND -> {
                val sourcePath = intent.getStringExtra(EXTRA_SOURCE_PATH)
                val targetIp = intent.getStringExtra(EXTRA_TARGET_IP)
                val rootLabel = intent.getStringExtra(EXTRA_ROOT_LABEL)
                if (sourcePath != null && targetIp != null) {
                    startSendJob(sourcePath, targetIp, rootLabel)
                }
            }
            ACTION_START_RECEIVE -> {
                val destPath = intent.getStringExtra(EXTRA_DEST_PATH)
                if (destPath != null) {
                    startReceiveJob(destPath)
                }
            }
            ACTION_PAUSE -> serviceScope.launch { transferEngine.pause() }
            ACTION_RESUME -> serviceScope.launch { transferEngine.resume() }
            ACTION_CANCEL -> {
                serviceScope.launch {
                    transferEngine.cancel()
                    runCatching { manualIpTransport.disconnect() }
                    runCatching { lanTransport.unregisterService() }
                    manualIpTransport.cancelListening()
                }
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startSendJob(sourcePath: String, targetIp: String, rootLabel: String?) {
        transferJob?.cancel()
        transferJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            sessionStatus.set("Menghubungkan ke $targetIp...")
            try {
                val connectResult = manualIpTransport.connect(
                    DeviceInfo(
                        deviceId = targetIp,
                        name = targetIp,
                        model = "-",
                        androidVersion = "-",
                        transportType = TransportType.MANUAL_IP,
                        ipAddress = targetIp
                    )
                )
                connectResult.exceptionOrNull()?.let { throw it }

                val channel = manualIpTransport.openChannel().getOrThrow()
                val sourceFolder = File(sourcePath)
                val fileCount = transferEngine.runSenderSession(
                    channel = channel,
                    sourceFolder = sourceFolder,
                    deviceName = Build.MODEL,
                    rootLabel = rootLabel
                )
                channel.close()
                manualIpTransport.disconnect()

                val finalProgress = transferEngine.progress.value
                historyDao.insert(
                    TransferHistoryEntity(
                        sessionId = finalProgress.sessionId,
                        deviceName = targetIp,
                        direction = "SENT",
                        totalSizeBytes = finalProgress.totalBytes,
                        fileCount = fileCount,
                        durationMillis = System.currentTimeMillis() - startTime,
                        averageSpeedBytesPerSec = finalProgress.speedBytesPerSec,
                        status = if (finalProgress.status == TransferStatus.COMPLETED) "COMPLETED" else finalProgress.status.name,
                        transportUsed = TransportType.MANUAL_IP.displayName,
                        timestampEpochMillis = System.currentTimeMillis()
                    )
                )
                sessionStatus.set("Selesai — $fileCount file terkirim ke $targetIp.")
            } catch (e: Exception) {
                appLogger.log(LogLevel.ERROR, TAG, "Pengiriman gagal", e)
                sessionStatus.set("Gagal mengirim: ${e.message}", isError = true)
                runCatching { manualIpTransport.disconnect() }
            } finally {
                stopSelf()
            }
        }
    }

    private fun startReceiveJob(destPath: String) {
        transferJob?.cancel()
        transferJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            sessionStatus.set("Menunggu pengirim...")
            try {
                // Best-effort — LAN auto-discovery is a nice-to-have on top of
                // Manual IP, which still works even if this fails or is skipped.
                runCatching { lanTransport.registerService(Build.MODEL, ManualIpTransport.PORT) }
                    .onFailure { appLogger.log(LogLevel.WARNING, TAG, "registerService gagal, lanjut tanpa LAN discovery", it) }

                val acceptResult = manualIpTransport.listenForSender()
                val sender = acceptResult.getOrThrow()
                appLogger.log(LogLevel.INFO, TAG, "Pengirim terhubung: ${sender.ipAddress}")
                sessionStatus.set("Terhubung dengan ${sender.ipAddress}, menerima data...")

                val channel = manualIpTransport.openChannel().getOrThrow()
                val destFolder = File(destPath)
                val manifest = transferEngine.runReceiverSession(channel = channel, destFolder = destFolder)
                channel.close()
                manualIpTransport.disconnect()
                runCatching { lanTransport.unregisterService() }

                val finalProgress = transferEngine.progress.value
                historyDao.insert(
                    TransferHistoryEntity(
                        sessionId = manifest.sessionId,
                        deviceName = sender.ipAddress ?: manifest.deviceName,
                        direction = "RECEIVED",
                        totalSizeBytes = finalProgress.totalBytes,
                        fileCount = finalProgress.totalFiles,
                        durationMillis = System.currentTimeMillis() - startTime,
                        averageSpeedBytesPerSec = finalProgress.speedBytesPerSec,
                        status = if (finalProgress.status == TransferStatus.COMPLETED) "COMPLETED" else finalProgress.status.name,
                        transportUsed = TransportType.MANUAL_IP.displayName,
                        timestampEpochMillis = System.currentTimeMillis()
                    )
                )
                sessionStatus.set("Selesai — ${finalProgress.totalFiles} file diterima dari ${sender.ipAddress}.")
            } catch (e: Exception) {
                appLogger.log(LogLevel.ERROR, TAG, "Penerimaan gagal", e)
                sessionStatus.set("Gagal menerima: ${e.message}", isError = true)
                runCatching { manualIpTransport.disconnect() }
                runCatching { lanTransport.unregisterService() }
            } finally {
                stopSelf()
            }
        }
    }

    private fun observeProgress() {
        serviceScope.launch {
            transferEngine.progress.collect { progress ->
                val notificationManager = getSystemService(NotificationManager::class.java)
                val text = when (progress.status) {
                    TransferStatus.RUNNING -> "${progress.currentFileName} - ${progress.percent}%"
                    TransferStatus.PAUSED -> "Dijeda - ${progress.percent}%"
                    TransferStatus.RETRYING -> "Menyambungkan ulang..."
                    TransferStatus.VERIFYING -> "Memverifikasi file..."
                    TransferStatus.COMPLETED -> "Transfer selesai"
                    TransferStatus.FAILED -> "Transfer gagal"
                    TransferStatus.CANCELLED -> "Transfer dibatalkan"
                    else -> "Menunggu..."
                }
                notificationManager?.notify(NOTIFICATION_ID, buildNotification(progress.percent, text))
            }
        }
    }

    private fun buildNotification(progressPercent: Int, statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = servicePendingIntent(ACTION_PAUSE)
        val resumeIntent = servicePendingIntent(ACTION_RESUME)
        val cancelIntent = servicePendingIntent(ACTION_CANCEL)

        return NotificationCompat.Builder(this, ResourceTransferApp.CHANNEL_TRANSFER)
            .setContentTitle("Resource Transfer")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_transfer_notification)
            .setContentIntent(openAppIntent)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .addAction(0, "Jeda", pauseIntent)
            .addAction(0, "Lanjut", resumeIntent)
            .addAction(0, "Batal", cancelIntent)
            .build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TransferForegroundService::class.java).setAction(action)
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TransferService"
        const val NOTIFICATION_ID = 42

        const val ACTION_START_SEND = "com.siroha.resourcetransfer.action.START_SEND"
        const val ACTION_START_RECEIVE = "com.siroha.resourcetransfer.action.START_RECEIVE"
        const val ACTION_PAUSE = "com.siroha.resourcetransfer.action.PAUSE"
        const val ACTION_RESUME = "com.siroha.resourcetransfer.action.RESUME"
        const val ACTION_CANCEL = "com.siroha.resourcetransfer.action.CANCEL"

        const val EXTRA_SOURCE_PATH = "extra_source_path"
        const val EXTRA_TARGET_IP = "extra_target_ip"
        const val EXTRA_DEST_PATH = "extra_dest_path"
        const val EXTRA_ROOT_LABEL = "extra_root_label"

        fun buildSendIntent(context: Context, sourcePath: String, targetIp: String, rootLabel: String? = null): Intent =
            Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START_SEND
                putExtra(EXTRA_SOURCE_PATH, sourcePath)
                putExtra(EXTRA_TARGET_IP, targetIp)
                rootLabel?.let { putExtra(EXTRA_ROOT_LABEL, it) }
            }

        fun buildReceiveIntent(context: Context, destPath: String): Intent =
            Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_START_RECEIVE
                putExtra(EXTRA_DEST_PATH, destPath)
            }
    }
}
