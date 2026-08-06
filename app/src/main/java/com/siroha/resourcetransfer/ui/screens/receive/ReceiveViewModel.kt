package com.siroha.resourcetransfer.ui.screens.receive

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siroha.resourcetransfer.domain.engine.TransferEngine
import com.siroha.resourcetransfer.domain.engine.TransferSessionStatus
import com.siroha.resourcetransfer.domain.model.TransferProgress
import com.siroha.resourcetransfer.domain.model.TransferStatus
import com.siroha.resourcetransfer.domain.transport.ManualIpTransport
import com.siroha.resourcetransfer.service.TransferForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ReceiveUiState(
    val destFolderName: String? = null,
    val destFolderPath: String? = null,
    val isDefaultFolder: Boolean = true,
    val localIpAddress: String? = null,
    val isListening: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false
)

/**
 * Like SendViewModel, this only starts TransferForegroundService in
 * receive mode and observes shared state — the actual ServerSocket
 * accept()/receive loop runs in the Service, so it survives this
 * ViewModel being cleared while the app is backgrounded.
 *
 * Destination folder picking is now OPTIONAL: on first load this defaults
 * to an auto-created `ResourceTransfer/` folder in shared storage (created
 * eagerly in init), so "Mulai Menunggu Pengirim" works immediately without
 * the user ever touching "Pilih Folder Tujuan" — that button remains for
 * anyone who wants a different destination.
 */
@HiltViewModel
class ReceiveViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    transferEngine: TransferEngine,
    sessionStatus: TransferSessionStatus,
    private val manualIpTransport: ManualIpTransport
) : ViewModel() {

    private val defaultFolder: File by lazy {
        File(Environment.getExternalStorageDirectory(), DEFAULT_FOLDER_NAME).apply { mkdirs() }
    }

    private val _destFolder = MutableStateFlow(Pair<String?, String?>(DEFAULT_FOLDER_NAME, null))
    private val _isDefaultFolder = MutableStateFlow(true)
    private val _isListening = MutableStateFlow(false)
    private val _localIp = MutableStateFlow(manualIpTransport.getLocalIpAddress())

    val uiState: StateFlow<ReceiveUiState> = combine(
        _destFolder, _localIp, _isListening, sessionStatus.message, _isDefaultFolder
    ) { folder, ip, listening, status, isDefault ->
        ReceiveUiState(
            destFolderName = folder.first,
            destFolderPath = folder.second ?: defaultFolder.absolutePath,
            isDefaultFolder = isDefault,
            localIpAddress = ip,
            isListening = listening,
            statusMessage = status.first,
            statusIsError = status.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReceiveUiState())

    val progress: StateFlow<TransferProgress> = transferEngine.progress

    init {
        defaultFolder // touch the lazy so the folder exists as soon as this screen opens
        // The ViewModel no longer awaits the transfer coroutine directly (it runs in
        // the Service now), so it has to watch the shared progress state to know
        // when to flip the "Mulai Menunggu Pengirim" button back on.
        viewModelScope.launch {
            transferEngine.progress.collect { p ->
                if (p.status == TransferStatus.COMPLETED || p.status == TransferStatus.FAILED || p.status == TransferStatus.CANCELLED) {
                    _isListening.value = false
                }
            }
        }
    }

    fun refreshLocalIp() {
        _localIp.value = manualIpTransport.getLocalIpAddress()
    }

    fun onFolderPicked(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            null
        }
        val primaryPath = docId?.let { resolvePrimaryStoragePath(it) }
        val displayName = uri.lastPathSegment?.substringAfterLast(':') ?: "Folder terpilih"

        if (primaryPath != null) {
            File(primaryPath).mkdirs()
            _destFolder.value = displayName to primaryPath
            _isDefaultFolder.value = false
        } else {
            _destFolder.value = displayName to null
            _isDefaultFolder.value = false
        }
    }

    /** Lets the user go back to the auto-created default folder after picking a custom one. */
    fun resetToDefaultFolder() {
        _destFolder.value = DEFAULT_FOLDER_NAME to null
        _isDefaultFolder.value = true
    }

    private fun resolvePrimaryStoragePath(docId: String): String? {
        val parts = docId.split(":")
        if (parts.size != 2) return null
        val (type, relativePath) = parts
        if (type != "primary") return null
        val root = Environment.getExternalStorageDirectory().absolutePath
        return if (relativePath.isBlank()) root else "$root/$relativePath"
    }

    /** Starts TransferForegroundService in receive mode. Always has a valid path now — default or custom. */
    fun startListening() {
        val destPath = _destFolder.value.second ?: defaultFolder.absolutePath
        _isListening.value = true
        val intent = TransferForegroundService.buildReceiveIntent(appContext, destPath)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun cancelListening() {
        manualIpTransport.cancelListening()
        _isListening.value = false
        appContext.startService(
            android.content.Intent(appContext, TransferForegroundService::class.java)
                .setAction(TransferForegroundService.ACTION_CANCEL)
        )
    }

    companion object {
        const val DEFAULT_FOLDER_NAME = "ResourceTransfer"
    }
}
