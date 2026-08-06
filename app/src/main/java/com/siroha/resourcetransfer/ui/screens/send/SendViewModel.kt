package com.siroha.resourcetransfer.ui.screens.send

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siroha.resourcetransfer.domain.engine.TransferEngine
import com.siroha.resourcetransfer.domain.engine.TransferSessionStatus
import com.siroha.resourcetransfer.domain.model.DeviceInfo
import com.siroha.resourcetransfer.domain.model.TransferProgress
import com.siroha.resourcetransfer.domain.transport.LanTransport
import com.siroha.resourcetransfer.service.TransferForegroundService
import com.siroha.resourcetransfer.util.AppLogger
import com.siroha.resourcetransfer.util.InstalledAppInfo
import com.siroha.resourcetransfer.util.InstalledAppsHelper
import com.siroha.resourcetransfer.util.LogLevel
import com.siroha.resourcetransfer.util.SendStagingHelper
import com.siroha.resourcetransfer.util.ShizukuHelper
import com.siroha.resourcetransfer.util.ShizukuState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class SendSourceMode { FOLDER, FILES, MEDIA, TEXT, APP }

data class SendUiState(
    val mode: SendSourceMode = SendSourceMode.FOLDER,
    val sourceFolderName: String? = null,
    val sourceFolderPath: String? = null,
    val rootLabel: String? = null,
    val stagedFileNames: List<String> = emptyList(),
    val resolvedViaShizuku: Boolean = false,
    val shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    val targetIp: String = "",
    val discoveredDevices: List<DeviceInfo> = emptyList(),
    val isScanning: Boolean = false,
    val textInput: String = "",
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val appSearchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false
) {
    val filteredApps: List<InstalledAppInfo>
        get() = if (appSearchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(appSearchQuery, ignoreCase = true) ||
                    it.packageName.contains(appSearchQuery, ignoreCase = true)
            }
        }
}

/**
 * Every source mode (Folder / Files / Media / Text / App) ends up
 * populating [sourceFolderPath] with a real on-disk directory — for
 * FOLDER this is the user's chosen folder resolved directly (no copy,
 * since folders can be many GB); for the other four modes it's a small
 * throwaway staging directory (SendStagingHelper) that content gets
 * copied into. Either way, TransferForegroundService and TransferEngine
 * downstream don't need to know which mode produced it.
 *
 * The staging directory PERSISTS across repeated picks within the same
 * mode (calling "Pilih File" twice adds to the same batch instead of
 * replacing it), and across re-selecting the same mode chip — only
 * switching to a genuinely DIFFERENT mode clears it. That's the fix for
 * the earlier bug where re-tapping "File" wiped out already-picked files.
 */
@HiltViewModel
class SendViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    transferEngine: TransferEngine,
    sessionStatus: TransferSessionStatus,
    private val lanTransport: LanTransport,
    private val shizukuHelper: ShizukuHelper,
    private val appLogger: AppLogger
) : ViewModel() {

    private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    private val _isScanning = MutableStateFlow(false)
    private var discoveryJob: Job? = null

    private val _mode = MutableStateFlow(SendSourceMode.FOLDER)
    private val _folderState = MutableStateFlow(Triple<String?, String?, Boolean>(null, null, false))
    private val _rootLabel = MutableStateFlow<String?>(null)
    private val _stagedFileNames = MutableStateFlow<List<String>>(emptyList())
    private val _targetIp = MutableStateFlow("")
    private val _textInput = MutableStateFlow("")
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _appSearchQuery = MutableStateFlow("")
    private val _isLoadingApps = MutableStateFlow(false)

    /** Staging dir currently in use for FILES/MEDIA/TEXT/APP modes — persists across repeated picks within the same mode. */
    private var stagingDir: File? = null

    val uiState: StateFlow<SendUiState> = combine(
        _mode, _folderState, shizukuHelper.state, sessionStatus.message, _targetIp,
        _discoveredDevices, _isScanning, _textInput, _installedApps, _isLoadingApps,
        _stagedFileNames, _appSearchQuery
    ) { values ->
        val mode = values[0] as SendSourceMode
        val folder = values[1] as Triple<*, *, *>
        val shizukuState = values[2] as ShizukuState
        val status = values[3] as Pair<*, *>
        val ip = values[4] as String
        val devices = values[5] as List<*>
        val scanning = values[6] as Boolean
        val text = values[7] as String
        val apps = values[8] as List<*>
        val loadingApps = values[9] as Boolean
        val stagedNames = values[10] as List<*>
        val appQuery = values[11] as String
        SendUiState(
            mode = mode,
            sourceFolderName = folder.first as String?,
            sourceFolderPath = folder.second as String?,
            rootLabel = _rootLabel.value,
            stagedFileNames = stagedNames.filterIsInstance<String>(),
            resolvedViaShizuku = folder.third as Boolean,
            shizukuState = shizukuState,
            targetIp = ip,
            discoveredDevices = devices.filterIsInstance<DeviceInfo>(),
            isScanning = scanning,
            textInput = text,
            installedApps = apps.filterIsInstance<InstalledAppInfo>(),
            appSearchQuery = appQuery,
            isLoadingApps = loadingApps,
            statusMessage = status.first as String?,
            statusIsError = status.second as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SendUiState())

    val progress: StateFlow<TransferProgress> = transferEngine.progress

    fun setMode(mode: SendSourceMode) {
        if (_mode.value == mode) return // re-tapping the same chip must NOT wipe out what's already picked
        _mode.value = mode
        clearSelection()
        if (mode == SendSourceMode.APP && _installedApps.value.isEmpty()) {
            loadInstalledApps()
        }
    }

    private fun clearSelection() {
        stagingDir?.let { dir -> runCatching { dir.deleteRecursively() } }
        stagingDir = null
        _folderState.value = Triple(null, null, false)
        _rootLabel.value = null
        _stagedFileNames.value = emptyList()
    }

    // -------------------- FOLDER mode (existing behaviour, unchanged) --------------------

    fun onFolderPicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            val docId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (e: Exception) {
                null
            }

            val primaryPath = docId?.let { resolvePrimaryStoragePath(it) }
            val displayName = uri.lastPathSegment?.substringAfterLast(':') ?: "Folder terpilih"

            when {
                primaryPath != null && File(primaryPath).canRead() -> {
                    _folderState.value = Triple(displayName, primaryPath, false)
                    _rootLabel.value = File(primaryPath).name
                    appLogger.log(LogLevel.INFO, "SendViewModel", "Resolved folder via direct path: $primaryPath")
                }

                primaryPath != null && shizukuHelper.state.value == ShizukuState.READY -> {
                    val readable = withContext(Dispatchers.IO) {
                        shizukuHelper.runCommand("test", "-r", primaryPath) != null
                    }
                    if (readable) {
                        _folderState.value = Triple(displayName, primaryPath, true)
                        _rootLabel.value = File(primaryPath).name
                        appLogger.log(LogLevel.INFO, "SendViewModel", "Resolved folder via Shizuku: $primaryPath")
                    } else {
                        fallbackToDisplayNameOnly(displayName)
                    }
                }

                else -> fallbackToDisplayNameOnly(displayName)
            }
        }
    }

    private fun fallbackToDisplayNameOnly(displayName: String) {
        appLogger.log(
            LogLevel.WARNING,
            "SendViewModel",
            "Could not resolve a real filesystem path for '$displayName' — falling back to SAF-only access."
        )
        _folderState.value = Triple(displayName, null, false)
        _rootLabel.value = null
    }

    private fun resolvePrimaryStoragePath(docId: String): String? {
        val parts = docId.split(":")
        if (parts.size != 2) return null
        val (type, relativePath) = parts
        if (type != "primary") return null
        val root = Environment.getExternalStorageDirectory().absolutePath
        return if (relativePath.isBlank()) root else "$root/$relativePath"
    }

    // -------------------- FILES / MEDIA modes (staged copy, accumulates) --------------------

    private fun ensureStagingDir(): File {
        return stagingDir ?: SendStagingHelper.freshStagingDir(appContext).also { stagingDir = it }
    }

    private fun refreshStagedFileList(dir: File) {
        val names = dir.listFiles()?.map { it.name }?.sorted().orEmpty()
        _stagedFileNames.value = names
        _folderState.value = Triple(
            if (names.isEmpty()) null else "${names.size} file dipilih",
            if (names.isEmpty()) null else dir.absolutePath,
            false
        )
        _rootLabel.value = null
    }

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val dir = ensureStagingDir()
            val requestedCount = uris.size
            val copiedCount = withContext(Dispatchers.IO) {
                uris.count { SendStagingHelper.copyUriInto(appContext, it, dir) != null }
            }
            refreshStagedFileList(dir)
            if (copiedCount < requestedCount) {
                appLogger.log(LogLevel.WARNING, "SendViewModel", "Hanya $copiedCount/$requestedCount file berhasil disalin ke staging")
            }
        }
    }

    fun onMediaPicked(uris: List<Uri>) = onFilesPicked(uris) // identical staging logic; kept as a separate entry point for clarity at the call site

    /** Removes one staged file by name (shown next to each item in the picked-files list). */
    fun removeStagedFile(name: String) {
        val dir = stagingDir ?: return
        File(dir, name).delete()
        refreshStagedFileList(dir)
    }

    /** "Hapus Semua" — clears every staged file for the current mode without switching modes. */
    fun clearAllStagedFiles() {
        clearSelection()
    }

    // -------------------- TEXT / Clipboard mode --------------------

    fun onTextChanged(text: String) {
        _textInput.value = text
    }

    fun pasteFromClipboard() {
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (clipText != null) {
            _textInput.value = clipText
        } else {
            appLogger.log(LogLevel.INFO, "SendViewModel", "Clipboard kosong atau bukan teks")
        }
    }

    fun confirmTextForSending() {
        val text = _textInput.value
        if (text.isBlank()) return
        viewModelScope.launch {
            val dir = ensureStagingDir()
            withContext(Dispatchers.IO) { SendStagingHelper.writeTextFile(dir, text) }
            refreshStagedFileList(dir)
        }
    }

    // -------------------- APP mode --------------------

    fun loadInstalledApps() {
        _isLoadingApps.value = true
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) { InstalledAppsHelper.listInstalledApps(appContext) }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun onAppSearchChanged(query: String) {
        _appSearchQuery.value = query
    }

    fun onAppSelected(app: InstalledAppInfo) {
        viewModelScope.launch {
            val dir = ensureStagingDir()
            val exported = withContext(Dispatchers.IO) {
                InstalledAppsHelper.exportApk(appContext, app.packageName, dir)
            }
            if (exported != null) {
                refreshStagedFileList(dir)
            } else {
                appLogger.log(LogLevel.ERROR, "SendViewModel", "Gagal mengekspor APK untuk ${app.packageName}")
            }
        }
    }

    // -------------------- LAN discovery --------------------

    fun startLanDiscovery() {
        if (discoveryJob?.isActive == true) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()
        discoveryJob = viewModelScope.launch {
            try {
                lanTransport.discover().collect { device ->
                    val current = _discoveredDevices.value
                    if (current.none { it.deviceId == device.deviceId }) {
                        _discoveredDevices.value = current + device
                    }
                }
            } catch (e: Exception) {
                appLogger.log(LogLevel.ERROR, "SendViewModel", "Pencarian perangkat gagal", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun stopLanDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _isScanning.value = false
    }

    fun onDeviceSelected(device: DeviceInfo) {
        device.ipAddress?.let { _targetIp.value = it }
        stopLanDiscovery()
    }

    fun onTargetIpChanged(ip: String) {
        _targetIp.value = ip
    }

    fun requestShizukuPermission() {
        shizukuHelper.requestPermission()
    }

    /** Starts TransferForegroundService in send mode — see class doc for why the actual work lives there now. */
    fun startDiscoveryAndWaitForReceiver() {
        val sourcePath = uiState.value.sourceFolderPath
        val ip = uiState.value.targetIp.trim()

        if (sourcePath == null) return
        if (ip.isEmpty()) return

        val intent = TransferForegroundService.buildSendIntent(appContext, sourcePath, ip, uiState.value.rootLabel)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun pause() {
        appContext.startService(
            android.content.Intent(appContext, TransferForegroundService::class.java)
                .setAction(TransferForegroundService.ACTION_PAUSE)
        )
    }

    fun resume() {
        appContext.startService(
            android.content.Intent(appContext, TransferForegroundService::class.java)
                .setAction(TransferForegroundService.ACTION_RESUME)
        )
    }

    fun cancel() {
        appContext.startService(
            android.content.Intent(appContext, TransferForegroundService::class.java)
                .setAction(TransferForegroundService.ACTION_CANCEL)
        )
    }

    override fun onCleared() {
        stopLanDiscovery()
        super.onCleared()
    }
}
