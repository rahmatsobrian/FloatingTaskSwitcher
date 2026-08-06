package com.siroha.resourcetransfer.util

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, PERMISSION_DENIED, READY }

/**
 * Optional privileged-access layer for users running Shizuku (ADB- or
 * root-activated). This is the target audience for this app (custom ROM /
 * root community), so it's included by default rather than as an add-on:
 *
 *  - Resolves REAL filesystem paths for arbitrary folders, sidestepping
 *    Storage Access Framework's tree-Uri model entirely.
 *  - Can run `find` / `sha256sum` / `cp` via a privileged shell.
 *
 * [state] is exposed as a StateFlow (not a one-shot getter) because Shizuku's
 * binder connection and permission grant both happen asynchronously, outside
 * any Compose recomposition trigger — without a Flow, the UI would only ever
 * see the state that existed when the screen first launched, exactly the bug
 * where the "Aktifkan Shizuku" button doesn't disappear until the screen is
 * recreated. Listeners are registered once (guarded by [listenersRegistered])
 * and push every binder/permission change straight into [_state].
 */
@Singleton
class ShizukuHelper @Inject constructor(
    private val appLogger: AppLogger
) {
    companion object {
        private const val TAG = "ShizukuHelper"
        const val PERMISSION_REQUEST_CODE = 9001
    }

    private val _state = MutableStateFlow(computeStateNow())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile private var listenersRegistered = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appLogger.log(LogLevel.INFO, TAG, "Shizuku binder received")
        refresh()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        appLogger.log(LogLevel.WARNING, TAG, "Shizuku binder died")
        refresh()
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        appLogger.log(LogLevel.INFO, TAG, "Shizuku permission result: $grantResult")
        refresh()
    }

    /** Call once (e.g. from Application.onCreate) to start observing Shizuku's own state changes. */
    fun registerListeners() {
        if (listenersRegistered) return
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            listenersRegistered = true
        } catch (e: Throwable) {
            // Shizuku not installed — nothing to listen to, state stays NOT_INSTALLED.
            appLogger.log(LogLevel.INFO, TAG, "Shizuku not available on this device")
        }
    }

    /** Re-evaluates current status and pushes it into [state] if changed. */
    fun refresh() {
        _state.value = computeStateNow()
    }

    private fun computeStateNow(): ShizukuState {
        return try {
            if (!Shizuku.pingBinder()) {
                ShizukuState.NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                ShizukuState.PERMISSION_DENIED
            } else {
                ShizukuState.READY
            }
        } catch (e: Throwable) {
            ShizukuState.NOT_INSTALLED
        }
    }

    /**
     * Requests the Shizuku permission (shows the Shizuku app's own consent
     * dialog). [permissionResultListener] above updates [state] automatically
     * once the user responds — no manual polling needed from the caller.
     */
    fun requestPermission() {
        val current = state.value
        if (current == ShizukuState.PERMISSION_DENIED || current == ShizukuState.NOT_RUNNING) {
            try {
                if (Shizuku.isPreV11()) {
                    appLogger.log(LogLevel.WARNING, TAG, "Installed Shizuku version too old, ask user to update.")
                    return
                }
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (e: Throwable) {
                appLogger.log(LogLevel.ERROR, TAG, "Failed to request Shizuku permission", e)
            }
        }
    }

    /**
     * Runs a shell command through Shizuku's privileged process. Returns
     * stdout as a single string, or null on failure. Caller should check
     * [state] == READY first.
     */
    fun runCommand(vararg cmd: String): String? {
        if (state.value != ShizukuState.READY) {
            appLogger.log(LogLevel.WARNING, TAG, "runCommand called while Shizuku not READY: ${state.value}")
            return null
        }
        return try {
            // Shizuku.newProcess is intentionally hidden from the public API surface,
            // so it's invoked via reflection — the standard integration pattern used
            // across the Shizuku app ecosystem.
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf(*cmd), null, null) as Process

            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val errorOutput = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()

            if (errorOutput.isNotBlank()) {
                appLogger.log(LogLevel.WARNING, TAG, "Command stderr: $errorOutput")
            }
            output
        } catch (e: Throwable) {
            appLogger.log(LogLevel.ERROR, TAG, "Privileged command failed: ${cmd.joinToString(" ")}", e)
            null
        }
    }

    fun listFilesRecursive(directoryPath: String): List<String> {
        val output = runCommand("find", directoryPath, "-type", "f") ?: return emptyList()
        return output.lineSequence().filter { it.isNotBlank() }.toList()
    }

    fun sha256Of(absolutePath: String): String? {
        val output = runCommand("sha256sum", absolutePath) ?: return null
        return output.trim().substringBefore(" ").takeIf { it.length == 64 }
    }

    fun copyFile(sourcePath: String, destPath: String): Boolean {
        return runCommand("cp", "-f", sourcePath, destPath) != null
    }
}
