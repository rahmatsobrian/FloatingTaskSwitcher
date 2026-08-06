package com.siroha.resourcetransfer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val deviceName: String,
    val direction: String, // "SENT" or "RECEIVED"
    val totalSizeBytes: Long,
    val fileCount: Int,
    val durationMillis: Long,
    val averageSpeedBytesPerSec: Long,
    val status: String, // "COMPLETED", "FAILED", "CANCELLED"
    val transportUsed: String,
    val timestampEpochMillis: Long
)
