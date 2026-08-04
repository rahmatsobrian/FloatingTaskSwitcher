package com.rahmatsobrian.floatingtaskswitcher.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "FloatingTaskSwitcher"
private const val CRASH_LOG_FILE_NAME = "last_crash.txt"

/**
 * Installs a Thread.UncaughtExceptionHandler that writes the full stack trace to a file inside
 * the app's private storage before handing off to the previous (system default) handler, so the
 * crash dialog and process death still behave normally. This exists purely so a crash can be
 * inspected and shared from inside the app afterwards - see [CrashLogRepository] - without
 * needing adb or a PC.
 */
object CrashLogger {

    fun install(context: Context) {
        val appContext = context.applicationContext
        val existing = Thread.getDefaultUncaughtExceptionHandler()
        if (existing is InstalledHandler) return
        Thread.setDefaultUncaughtExceptionHandler(InstalledHandler(appContext, existing))
    }

    private class InstalledHandler(
        private val context: Context,
        private val previousHandler: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            runCatching {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val content = buildString {
                    appendLine("Floating Task Switcher crash log")
                    appendLine("Time: $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine(Log.getStackTraceString(throwable))
                }
                File(context.filesDir, CRASH_LOG_FILE_NAME).writeText(content)
            }
            Log.e(TAG, "Uncaught exception", throwable)
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    internal fun logFile(context: Context): File = File(context.applicationContext.filesDir, CRASH_LOG_FILE_NAME)
}
