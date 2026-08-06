package com.siroha.resourcetransfer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.siroha.resourcetransfer.util.ShizukuHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt graph root.
 *
 * Deliberately does NOT initialize any analytics/crash-reporting SDK that
 * requires network access — this app is fully offline by design.
 */
@HiltAndroidApp
class ResourceTransferApp : Application() {

    @Inject lateinit var shizukuHelper: ShizukuHelper

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Registered once here (not per-screen) so ShizukuHelper.state stays
        // accurate app-wide regardless of which screen is currently visible.
        shizukuHelper.registerListeners()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val transferChannel = NotificationChannel(
                CHANNEL_TRANSFER,
                "File Transfer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing send/receive progress"
                setShowBadge(false)
            }
            manager.createNotificationChannel(transferChannel)
        }
    }

    companion object {
        const val CHANNEL_TRANSFER = "channel_transfer"
    }
}
