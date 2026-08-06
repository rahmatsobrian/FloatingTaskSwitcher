package com.siroha.resourcetransfer.domain.model

enum class TransferStatus { QUEUED, RUNNING, PAUSED, RETRYING, VERIFYING, COMPLETED, FAILED, CANCELLED }

data class TransferProgress(
    val sessionId: String,
    val status: TransferStatus,
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val activeTransport: TransportType? = null,
    val errorMessage: String? = null
) {
    val percent: Int
        get() = if (totalBytes <= 0) 0 else ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100)

    val remainingBytes: Long
        get() = (totalBytes - transferredBytes).coerceAtLeast(0)
}
