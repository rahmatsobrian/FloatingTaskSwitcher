package com.siroha.resourcetransfer.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel { DEBUG, INFO, WARNING, ERROR, CRASH }

/**
 * In-memory ring buffer of developer/error/crash logs, surfaced on the
 * Logs screen and exportable to a text file. Kept intentionally simple
 * (no external logging SDK) since the app must stay fully offline.
 */
@Singleton
class AppLogger @Inject constructor() {

    private val maxLines = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _logLines = MutableStateFlow<List<String>>(emptyList())
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val line = "[${timeFormat.format(Date())}] [${level.name}] $tag: $message" +
            (throwable?.let { " — ${it.message}" } ?: "")

        _logLines.value = (_logLines.value + line).takeLast(maxLines)

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR, LogLevel.CRASH -> Log.e(tag, message, throwable)
        }
    }

    fun clear() {
        _logLines.value = emptyList()
    }
}
