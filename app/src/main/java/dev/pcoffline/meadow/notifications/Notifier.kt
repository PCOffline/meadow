package dev.pcoffline.meadow.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.pcoffline.meadow.MainActivity
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.formatDuration
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.BlockActivity

class Notifier(private val context: Context, private val store: LocalStore) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_GUARDIAN, "Garden keeper", NotificationManager.IMPORTANCE_MIN).apply {
                description = "The quiet notification that keeps your limits in place."
                setShowBadge(false)
            },
            NotificationChannel(CH_WARNINGS, "Time reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A heads-up before an app's time runs out."
            },
            NotificationChannel(CH_REQUESTS, "Requests from friends", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Friends asking you for more time or to change their limits."
            },
            NotificationChannel(CH_DECISIONS, "Answers to your requests", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_CHAT, "Messages", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_SOCIAL, "Invites and your circle", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_ALERTS, "Protection alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When someone you're a Petal Pal for turns off their protections."
            },
        ).forEach(nm::createNotificationChannel)
    }

    fun guardianNotification(text: String): Notification =
        NotificationCompat.Builder(context, CH_GUARDIAN)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle("Meadow")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(null, 0))
            .build()

    fun updateGuardian(text: String) = post(GUARDIAN_ID, guardianNotification(text))

    /** Gentle reminders at five minutes and one minute left, once each per day. */
    fun maybeWarn(budget: AppBudget) {
        val remaining = budget.remainingMs
        if (remaining <= 0 || remaining == Long.MAX_VALUE) return
        val threshold = when {
            remaining <= 60_000L -> 1
            remaining <= 5 * 60_000L -> 5
            else -> return
        }
        val today = Days.todayKey()
        val key = "${budget.limit.packageName}|$today|$threshold"
        val warned = store.warnedKeys
        if (key in warned) return
        store.warnedKeys = warned.filter { it.contains("|$today|") }.toSet() + key
        val period = if (budget.weeklyIsTighter) "this week" else "today"
        post(
            budget.limit.packageName.hashCode(),
            NotificationCompat.Builder(context, CH_WARNINGS)
                .setSmallIcon(R.drawable.ic_flower)
                .setContentTitle("🌷 ${formatDuration(remaining)} left on ${budget.limit.appName}")
                .setContentText("Your ${budget.limit.appName} time for $period is almost up. Time to wind down.")
                .setAutoCancel(true)
                .setTimeoutAfter(remaining + 60_000L)
                .build(),
        )
    }

    fun timeUp(budget: AppBudget) {
        val intent = Intent(context, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_PACKAGE, budget.limit.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        post(
            budget.limit.packageName.hashCode(),
            NotificationCompat.Builder(context, CH_WARNINGS)
                .setSmallIcon(R.drawable.ic_flower)
                .setContentTitle("🌙 ${budget.limit.appName} is resting now")
                .setContentText("You've used the time you committed to. Tap to ask a friend for more.")
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, budget.limit.packageName.hashCode(), intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Builds a notification from a data-only push sent by the backend. */
    fun fromPush(data: Map<String, String>) {
        val type = data["type"] ?: return
        val title = data["title"] ?: "Meadow"
        val body = data["body"] ?: ""
        val id = (data["requestId"] ?: data["chatId"] ?: data["inviteId"] ?: type + body).hashCode()
        val (channel, route) = when (type) {
            "request_new" -> CH_REQUESTS to "requests"
            "request_decided" -> CH_DECISIONS to "requests"
            "chat" -> CH_CHAT to "chat/${data["chatId"]}"
            "invite", "invite_accepted", "approver_left" -> CH_SOCIAL to "circle"
            "alert" -> CH_ALERTS to "circle"
            "emergency" -> CH_ALERTS to (data["uid"]?.let { "friend/$it" } ?: "circle")
            else -> CH_SOCIAL to null
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(route, id))
        if (type == "chat") builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
        val requestId = data["requestId"]
        if (type == "request_new" && requestId != null) {
            builder.addAction(0, "Say yes 🌸", RequestActionReceiver.intent(context, requestId, id, approve = true))
            builder.addAction(0, "Not today", RequestActionReceiver.intent(context, requestId, id, approve = false))
        }
        post(id, builder.build())
    }

    fun cancel(id: Int) = manager.cancel(id)

    private fun openApp(route: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (route != null) intent.putExtra(MainActivity.EXTRA_ROUTE, route)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (manager.areNotificationsEnabled()) runCatching { manager.notify(id, notification) }
    }

    companion object {
        const val GUARDIAN_ID = 1
        const val CH_GUARDIAN = "guardian"
        const val CH_WARNINGS = "warnings"
        const val CH_REQUESTS = "requests"
        const val CH_DECISIONS = "decisions"
        const val CH_CHAT = "chat"
        const val CH_SOCIAL = "social"
        const val CH_ALERTS = "alerts"
    }
}
