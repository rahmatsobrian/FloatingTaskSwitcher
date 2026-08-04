package com.rahmatsobrian.floatingtaskswitcher.data.repository

import android.content.Context
import com.rahmatsobrian.floatingtaskswitcher.core.util.CrashLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file get() = CrashLogger.logFile(context)

    fun read(): String? = if (file.exists()) runCatching { file.readText() }.getOrNull() else null

    fun clear() {
        if (file.exists()) runCatching { file.delete() }
    }
}
