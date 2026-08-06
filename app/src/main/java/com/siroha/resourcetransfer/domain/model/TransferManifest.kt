package com.siroha.resourcetransfer.domain.model

import kotlinx.serialization.Serializable

/**
 * Manifest exchanged between Sender and Receiver before any bytes move.
 * Receiver diffs this against what it already has locally and only
 * requests the files it's missing/corrupted (mirrors the "resource
 * transfer" delta behaviour, without touching any MLBB code/assets).
 */
@Serializable
data class TransferManifest(
    val version: Int = 1,
    val sessionId: String,
    val deviceName: String,
    val files: List<ManifestFile>
)

@Serializable
data class ManifestFile(
    val path: String,
    val size: Long,
    val sha256: String,
    val crc32: Long
)

/** Sent from Receiver -> Sender: which files (by path) it actually needs. */
@Serializable
data class FileRequest(
    val sessionId: String,
    val requestedPaths: List<String>
)

/** Sent from Sender -> Receiver right before streaming a file's raw bytes. */
@Serializable
data class FileHeader(
    val path: String,
    val size: Long
)
