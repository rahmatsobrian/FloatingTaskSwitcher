package com.rahmatsobrian.floatingtaskswitcher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.rahmatsobrian.floatingtaskswitcher.core.permission.OperatingModeManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

const val NOTIFICATION_CHANNEL_OVERLAY = "overlay_service_channel"

@HiltAndroidApp
class FloatingTaskSwitcherApp : Application() {

    @Inject lateinit var operatingModeManager: OperatingModeManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Passive detection only (Root/Shizuku availability check); never prompts the user.
        operatingModeManager.initialize()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_OVERLAY,
            getString(R.string.notification_channel_overlay),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
