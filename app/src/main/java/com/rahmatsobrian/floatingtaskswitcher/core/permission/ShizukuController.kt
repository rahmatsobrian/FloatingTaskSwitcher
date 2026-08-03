package com.rahmatsobrian.floatingtaskswitcher.core.permission

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, RUNNING_NOT_GRANTED, RUNNING_GRANTED }

private const val SHIZUKU_REQUEST_CODE = 9100

/**
 * Wraps the official Shizuku API (rikka.shizuku:api). All calls go through
 * Shizuku's own binder proxy — no reflection into hidden system APIs, no
 * root escalation. If Shizuku is absent or its binder dies, [state] falls
 * back to a non-granted value and callers should fall back to Standard Mode.
 */
@Singleton
class ShizukuController @Inject constructor() {

    private val _state = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshState() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener { _state.value = ShizukuState.NOT_RUNNING }
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            _state.value = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ShizukuState.RUNNING_GRANTED
            } else {
                ShizukuState.RUNNING_NOT_GRANTED
            }
        }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshState()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun refreshState() {
        _state.value = when {
            !isBinderAlive() -> ShizukuState.NOT_RUNNING
            isPermissionGranted() -> ShizukuState.RUNNING_GRANTED
            else -> ShizukuState.RUNNING_NOT_GRANTED
        }
    }

    fun requestPermission() {
        if (!isBinderAlive()) return
        if (Shizuku.isPreV11()) return // Unsupported ancient Shizuku versions
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }

    private fun isBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    private fun isPermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }
}
