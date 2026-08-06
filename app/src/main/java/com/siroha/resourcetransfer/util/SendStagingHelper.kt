package com.siroha.resourcetransfer.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Every send source this app supports (folder, loose files, media picker,
 * clipboard/typed text, an installed app's APK) ultimately needs to become
 * "a real folder on disk" so it can flow through the existing
 * TransferEngine.buildManifest -> runSenderSession pipeline unchanged.
 *
 * For most of these (files, media, text, apk) that folder is a throwaway
 * staging directory under the app's cache — small/medium content gets
 * copied in once, then sent normally. The one exception is the FOLDER
 * source mode, which keeps using the direct-path (no-copy) resolution
 * from SendViewModel, since folders can be many GB and copying them
 * first would be a serious regression.
 */
object SendStagingHelper {

    /** Fresh, empty staging directory for a new compose-and-send session. Clears any leftovers from previous sessions. */
    fun freshStagingDir(context: Context): File {
        val base = File(context.cacheDir, "send_staging")
        base.mkdirs()
        base.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        val dir = File(base, System.currentTimeMillis().toString())
        dir.mkdirs()
        return dir
    }

    /** Copies one content:// Uri (from a file/media picker) into [destDir], preserving its display name where possible. */
    fun copyUriInto(context: Context, uri: Uri, destDir: File): File? {
        val displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
        val target = uniqueTarget(destDir, sanitizeFileName(displayName))
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target
        } catch (e: Exception) {
            null
        }
    }

    /** Writes typed or clipboard text as a plain .txt file so it flows through the same pipeline as any other file. */
    fun writeTextFile(destDir: File, text: String): File {
        val target = uniqueTarget(destDir, "Teks.txt")
        target.writeText(text)
        return target
    }

    private fun uniqueTarget(destDir: File, desiredName: String): File {
        var target = File(destDir, desiredName)
        var counter = 1
        while (target.exists()) {
            val dot = desiredName.lastIndexOf('.')
            val base = if (dot > 0) desiredName.substring(0, dot) else desiredName
            val ext = if (dot > 0) desiredName.substring(dot) else ""
            target = File(destDir, "$base ($counter)$ext")
            counter++
        }
        return target
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file_${System.currentTimeMillis()}" }
    }
}
