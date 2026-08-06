package com.siroha.resourcetransfer.ui.screens.logs

import androidx.lifecycle.ViewModel
import com.siroha.resourcetransfer.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val appLogger: AppLogger
) : ViewModel() {

    val logLines: StateFlow<List<String>> = appLogger.logLines

    fun exportLogs() {
        // Real implementation writes appLogger.logLines.value to a text file under
        // getExternalFilesDir() and shares it via FileProvider + ACTION_SEND.
    }
}
