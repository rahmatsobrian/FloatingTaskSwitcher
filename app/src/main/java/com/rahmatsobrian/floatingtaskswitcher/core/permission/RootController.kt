package com.rahmatsobrian.floatingtaskswitcher.core.permission

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RootProvider { MAGISK, KERNEL_SU, APATCH, UNKNOWN, NONE }

data class RootState(
    val isRootAvailable: Boolean,
    val isRootGranted: Boolean,
    val provider: RootProvider,
)

/**
 * Thin wrapper around libsu. libsu talks to whatever `su` binary is on the
 * PATH, which is exactly what Magisk, KernelSU and APatch all provide, so no
 * provider-specific binder/API integration is required — only the detection
 * of *which* provider is present is provider-specific and is done here by
 * probing well-known files/props rather than any hidden API.
 */
@Singleton
class RootController @Inject constructor() {

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10),
        )
    }

    suspend fun detectProvider(): RootProvider = withContext(Dispatchers.IO) {
        when {
            File("/data/adb/magisk").exists() || File("/sbin/magisk").exists() -> RootProvider.MAGISK
            File("/data/adb/ksu").exists() || getSystemProperty("ro.kernelsu.version").isNotBlank() -> RootProvider.KERNEL_SU
            File("/data/adb/ap").exists() || File("/data/adb/apd").exists() -> RootProvider.APATCH
            else -> RootProvider.UNKNOWN
        }
    }

    /** Returns whether an `su` binary exists on the device at all, without prompting. */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        Shell.isAppGrantedRoot() != false || probeSuBinaryExists()
    }

    /**
     * Actually requests root (prompts Magisk/KernelSU/APatch's grant dialog
     * the first time). Only call this when the user opts in from the
     * Permission Manager screen — never automatically at app start.
     */
    suspend fun requestRoot(): RootState = withContext(Dispatchers.IO) {
        val granted = try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
        RootState(
            isRootAvailable = granted || probeSuBinaryExists(),
            isRootGranted = granted,
            provider = detectProvider(),
        )
    }

    suspend fun runCommand(command: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val result = Shell.cmd(command).exec()
            if (!result.isSuccess) error("Command failed with exit code ${result.code}")
            result.out
        }
    }

    private fun probeSuBinaryExists(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/data/adb/ksud")
        return paths.any { File(it).exists() }
    }

    private fun getSystemProperty(key: String): String = runCatching {
        val process = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText().trim()
    }.getOrDefault("")
}
