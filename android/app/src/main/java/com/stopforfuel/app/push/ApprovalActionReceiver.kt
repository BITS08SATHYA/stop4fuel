package com.stopforfuel.app.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.stopforfuel.app.data.repository.ApprovalRequestRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Receives the "Approve" action tapped on a system notification and fires the
 * approval API directly. The user's JWT is already in TokenStore so no UI is
 * needed. Rejects are routed through the app because they require a note.
 */
@AndroidEntryPoint
class ApprovalActionReceiver : BroadcastReceiver() {

    @Inject lateinit var approvalRequestRepository: ApprovalRequestRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_APPROVE) return
        val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (requestId <= 0) return

        val pending = goAsync()
        scope.launch {
            val result = approvalRequestRepository.approve(requestId, null)
            withContext(Dispatchers.Main) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notifId >= 0) nm.cancel(notifId)
                val msg = if (result.isSuccess) "Approved #$requestId" else "Failed to approve: ${result.exceptionOrNull()?.message ?: "error"}"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_APPROVE = "com.stopforfuel.app.APPROVE_REQUEST"
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        private val job: Job = SupervisorJob()
        private val scope = CoroutineScope(Dispatchers.IO + job)
    }
}
