package com.siroha.resourcetransfer.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short human-readable status text ("Menghubungkan ke 192.168.1.5...",
 * "Selesai — 12 file terkirim") shown on the Send/Receive screens.
 *
 * Separated from [TransferEngine.progress] (which carries structured,
 * per-file numeric progress) because the actual transfer now runs inside
 * [com.siroha.resourcetransfer.service.TransferForegroundService] — not
 * in the ViewModel's viewModelScope — so it can survive the Activity
 * being destroyed while backgrounded. The Service updates this Singleton,
 * the ViewModel (wherever it's currently alive) just observes it; neither
 * needs to know about the other directly.
 */
@Singleton
class TransferSessionStatus @Inject constructor() {
    private val _message = MutableStateFlow<Pair<String?, Boolean>>(null to false)
    val message: StateFlow<Pair<String?, Boolean>> = _message.asStateFlow()

    fun set(text: String?, isError: Boolean = false) {
        _message.value = text to isError
    }

    fun clear() {
        _message.value = null to false
    }
}
