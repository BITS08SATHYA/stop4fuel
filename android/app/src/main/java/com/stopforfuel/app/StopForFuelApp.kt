package com.stopforfuel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.stopforfuel.app.push.AppForegroundState
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StopForFuelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppForegroundState.attach(this)
        createNotificationChannel()
    }

    /**
     * Create the notification channel up front so FCM can display background
     * notifications (auto-rendered from the `notification` payload) even before
     * the app has handled its first message in [com.stopforfuel.app.push.FcmService].
     * On Android 8+, a missing channel means background notifications are silently dropped.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Approval requests",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Cashier approval requests"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        /** Matches `default_notification_channel_id` in the manifest and FcmService. */
        const val NOTIFICATION_CHANNEL_ID = "approvals"
    }
}
