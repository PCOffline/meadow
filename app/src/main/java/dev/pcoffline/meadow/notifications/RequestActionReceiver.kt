package dev.pcoffline.meadow.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dev.pcoffline.meadow.AppGraph
import kotlinx.coroutines.launch

/** Lets approvers answer a request straight from the notification. */
class RequestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST) ?: return
        val approve = intent.getBooleanExtra(EXTRA_APPROVE, false)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        val pending = goAsync()
        AppGraph.scope.launch {
            val result = runCatching { AppGraph.requests.decide(requestId, approve) }
            AppGraph.notifier.cancel(notificationId)
            val text = result.fold(
                onSuccess = { if (approve) "Sent your yes 🌸" else "Answered gently 🍃" },
                onFailure = { it.message ?: "Couldn't answer that request." },
            )
            android.os.Handler(context.mainLooper).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
            pending.finish()
        }
    }

    companion object {
        private const val EXTRA_REQUEST = "request_id"
        private const val EXTRA_APPROVE = "approve"
        private const val EXTRA_NOTIFICATION = "notification_id"

        fun intent(context: Context, requestId: String, notificationId: Int, approve: Boolean): PendingIntent {
            val intent = Intent(context, RequestActionReceiver::class.java)
                .putExtra(EXTRA_REQUEST, requestId)
                .putExtra(EXTRA_APPROVE, approve)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
            return PendingIntent.getBroadcast(
                context,
                (requestId + approve).hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
