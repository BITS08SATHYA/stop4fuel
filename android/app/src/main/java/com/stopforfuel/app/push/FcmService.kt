package com.stopforfuel.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.stopforfuel.app.MainActivity
import com.stopforfuel.app.R
import com.stopforfuel.app.data.local.TokenStore
import com.stopforfuel.app.data.repository.DeviceTokenRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FcmService : FirebaseMessagingService() {

    @Inject lateinit var deviceTokenRepository: DeviceTokenRepository
    @Inject lateinit var tokenStore: TokenStore

    override fun onNewToken(token: String) {
        // Only send to backend if user is logged in. Otherwise the token will be
        // registered next time the user logs in (see AuthViewModel).
        if (tokenStore.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { deviceTokenRepository.register(token) }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ensureChannel()

        val title = message.notification?.title ?: message.data["title"] ?: "Approval request"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        val requestId = message.data["requestId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            requestId?.let { putExtra("approval_request_id", it) }
            putExtra("open_destination", "approval_inbox")
        }
        val pending = PendingIntent.getActivity(
            this,
            requestId?.toIntOrNull() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((requestId?.toIntOrNull() ?: System.currentTimeMillis().toInt()), notif)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Approval requests", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "Cashier approval requests" }
                )
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "approvals"
    }
}
