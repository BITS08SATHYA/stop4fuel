package com.stopforfuel.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
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
        // registered next time the user logs in (see AuthRepository.login).
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
        val requestIdStr = message.data["requestId"]
        val requestId = requestIdStr?.toLongOrNull()

        // Foreground → in-app banner only; skip the system tray notification.
        if (AppForegroundState.isInForeground) {
            InAppNotificationBus.emit(InAppNotification(title = title, body = body, requestId = requestId))
            return
        }

        // Background → post a rich system notification with Approve/Reject actions.
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            requestIdStr?.let { putExtra("approval_request_id", it) }
            putExtra("open_destination", "approval_inbox")
        }
        val notifId = requestId?.toInt() ?: System.currentTimeMillis().toInt()
        val contentPending = PendingIntent.getActivity(
            this, notifId, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setContentIntent(contentPending)

        if (requestId != null) {
            builder.addAction(buildApproveAction(requestId, notifId))
            builder.addAction(buildRejectAction(requestId, notifId))
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())
    }

    private fun buildApproveAction(requestId: Long, notifId: Int): NotificationCompat.Action {
        val intent = Intent(this, ApprovalActionReceiver::class.java).apply {
            action = ApprovalActionReceiver.ACTION_APPROVE
            putExtra(ApprovalActionReceiver.EXTRA_REQUEST_ID, requestId)
            putExtra(ApprovalActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val pending = PendingIntent.getBroadcast(
            this, notifId * 10 + 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "Approve", pending).build()
    }

    private fun buildRejectAction(requestId: Long, notifId: Int): NotificationCompat.Action {
        // Reject requires a note, so route through the app rather than inline.
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("approval_request_id", requestId.toString())
            putExtra("open_destination", "approval_inbox")
            putExtra("prompt_reject", true)
        }
        val pending = PendingIntent.getActivity(
            this, notifId * 10 + 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "Reject", pending).build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Approval requests",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Cashier approval requests"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "approvals"
    }
}
