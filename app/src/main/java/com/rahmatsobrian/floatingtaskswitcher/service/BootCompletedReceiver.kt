package com.rahmatsobrian.floatingtaskswitcher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.rahmatsobrian.floatingtaskswitcher.data.local.SettingsDataStore
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun settingsDataStore(): SettingsDataStore
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Only restart the overlay if the user had it enabled AND overlay permission is
        // still granted; never self-elevate permissions or start silently without consent.
        if (!Settings.canDrawOverlays(context)) return

        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(appContext, SettingsEntryPoint::class.java)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wasEnabled = entryPoint.settingsDataStore().settings.first().floatingServiceEnabled
                if (wasEnabled) {
                    OverlayService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
