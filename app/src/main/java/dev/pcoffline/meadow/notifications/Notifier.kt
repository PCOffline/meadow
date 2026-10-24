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
import dev.pcoffline.meadow.data.Trace
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.BlockActivity
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.navigation.DeepLinks

class Notifier(private val context: Context, private val store: LocalStore) {
    private val manager = NotificationManagerCompat.from(context)
    private val res get() = context.resources

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        fun channel(id: String, name: Int, importance: Int, description: Int? = null) =
            NotificationChannel(id, context.getString(name), importance).apply {
                description?.let { this.description = context.getString(it) }
            }
        listOf(
            channel(CH_GUARDIAN, R.string.channel_guardian, NotificationManager.IMPORTANCE_MIN, R.string.channel_guardian_description)
                .apply { setShowBadge(false) },
            channel(CH_WARNINGS, R.string.channel_warnings, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_warnings_description),
            channel(CH_REQUESTS, R.string.channel_requests, NotificationManager.IMPORTANCE_HIGH, R.string.channel_requests_description),
            channel(CH_DECISIONS, R.string.channel_decisions, NotificationManager.IMPORTANCE_HIGH, R.string.channel_decisions_description),
            channel(CH_CHAT, R.string.channel_chat, NotificationManager.IMPORTANCE_HIGH),
            channel(CH_SOCIAL, R.string.channel_social, NotificationManager.IMPORTANCE_DEFAULT, R.string.channel_social_description),
            channel(CH_ALERTS, R.string.channel_alerts, NotificationManager.IMPORTANCE_HIGH, R.string.channel_alerts_description),
            channel(CH_UPDATES, R.string.channel_updates, NotificationManager.IMPORTANCE_LOW, R.string.channel_updates_description),
        ).forEach(nm::createNotificationChannel)
    }

    fun guardianNotification(text: String): Notification =
        NotificationCompat.Builder(context, CH_GUARDIAN)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(null, 0))
            .build()

    private var guardianText: String? = null

    /** Updates the ongoing notification, only when its text actually changes. */
    fun updateGuardian(text: String) {
        if (text == guardianText) return
        guardianText = text
        post(GUARDIAN_ID, guardianNotification(text))
    }

    /** Gentle reminders at five minutes and one minute left, once each per day. */
    fun maybeWarn(budget: AppBudget) {
        val remaining = budget.remainingMs
        if (remaining <= 0 || remaining == Long.MAX_VALUE || budget.emergency) return
        val threshold = when {
            remaining <= 60_000L -> 1
            remaining <= 5 * 60_000L -> 5
            else -> return
        }
        val today = Days.todayKey()
        // Extra time from a Bumblebee changes the allowance, and earns its own reminders.
        val key = "${budget.limit.packageName}|$today|$threshold|${budget.dailyAllowedMs}/${budget.weeklyAllowedMs}"
        val warned = store.warnedKeys
        if (key in warned) return
        store.warnedKeys = warned.filter { it.contains("|$today|") }.toSet() + key
        val app = budget.limit.appName
        val body = when {
            budget.untilScheduleChangeMs != null && budget.untilScheduleChangeMs <= remaining -> R.string.notify_warning_body_hours
            budget.weeklyIsTighter -> R.string.notify_warning_body_week
            else -> R.string.notify_warning_body_today
        }
        val pkg = budget.limit.packageName
        val builder = NotificationCompat.Builder(context, CH_WARNINGS)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle(context.getString(R.string.notify_warning_title, res.durationText(remaining), app))
            .setContentText(context.getString(body, app))
            .setAutoCancel(true)
            .setTimeoutAfter(remaining + 60_000L)
        // Asking early, before the time runs out.
        if (store.approverCount.value > 0) {
            builder.addAction(0, context.getString(R.string.notify_warning_ask), openApp(DeepLinks.ask(pkg), ("ask$pkg").hashCode()))
        }
        post(pkg.hashCode(), builder.build())
    }

    /** Shown when an app can't be closed on screen (no overlay permission): tap to see the rest screen. */
    fun timeUp(budget: AppBudget) {
        val intent = Intent(context, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_PACKAGE, budget.limit.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        post(
            budget.limit.packageName.hashCode(),
            NotificationCompat.Builder(context, CH_WARNINGS)
                .setSmallIcon(R.drawable.ic_flower)
                .setContentTitle(context.getString(R.string.notify_time_up_title, budget.limit.appName))
                .setContentText(context.getString(R.string.notify_time_up_body))
                .setOnlyAlertOnce(true)
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

    /**
     * Builds a notification from a data-only push. Titles and bodies come from the backend's copy.ts.
     * Only a push addressed (in `to`) to [signedInUid] is shown: one for an account that used to be
     * signed in on this phone, or arriving while nobody is, is dropped.
     */
    fun fromPush(data: Map<String, String>, signedInUid: String?) {
        if (signedInUid == null || data["to"] != signedInUid) {
            Trace.log { "push dropped: not for the signed-in account" }
            return
        }
        val type = data["type"] ?: return
        val title = data["title"] ?: context.getString(R.string.app_name)
        val body = data["body"].orEmpty()
        val id = (data["requestId"] ?: data["chatId"] ?: data["inviteId"] ?: (type + title)).hashCode()
        val friend = data["uid"]?.let(DeepLinks::friend)
        val (channel, link) = when (type) {
            "request_new" -> CH_REQUESTS to DeepLinks.requests()
            "request_decided" -> CH_DECISIONS to DeepLinks.requests()
            "chat" -> CH_CHAT to data["chatId"]?.let(DeepLinks::chat)
            "invite", "invite_accepted", "approver_left", "approver_leaving", "approver_kept" -> CH_SOCIAL to DeepLinks.circle()
            "alert", "emergency" -> CH_ALERTS to (friend ?: DeepLinks.circle())
            else -> CH_SOCIAL to null
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(link, id))
        if (type == "chat") builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
        val requestId = data["requestId"]
        if (type == "request_new" && requestId != null) {
            builder.addAction(0, context.getString(R.string.request_say_yes), RequestActionReceiver.intent(context, requestId, id, approve = true))
            builder.addAction(0, context.getString(R.string.request_not_today), RequestActionReceiver.intent(context, requestId, id, approve = false))
        }
        post(id, builder.build())
    }

    /** A new release is out; tapping opens Settings, where it can be installed. */
    fun updateAvailable(version: String) {
        post(
            UPDATE_ID,
            NotificationCompat.Builder(context, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_flower)
                .setContentTitle(context.getString(R.string.notify_update_title, version))
                .setContentText(context.getString(R.string.notify_update_body))
                .setAutoCancel(true)
                .setContentIntent(openApp(DeepLinks.settings(), UPDATE_ID))
                .build(),
        )
    }

    /**
     * Android needs a yes before installing an update, and Meadow isn't on screen to ask for it.
     * Tapping opens Android's [confirm]ation.
     */
    fun confirmUpdate(confirm: Intent) {
        post(
            UPDATE_ID,
            NotificationCompat.Builder(context, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_flower)
                .setContentTitle(context.getString(R.string.notify_update_confirm_title))
                .setContentText(context.getString(R.string.notify_update_confirm_body))
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, UPDATE_ID, confirm,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build(),
        )
    }

    fun cancel(id: Int) = manager.cancel(id)

    private fun openApp(link: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (link != null) intent.putExtra(MainActivity.EXTRA_ROUTE, link)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    @SuppressLint("MissingPermission") // Checked via areNotificationsEnabled().
    private fun post(id: Int, notification: Notification) {
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }

    companion object {
        const val GUARDIAN_ID = 1
        const val UPDATE_ID = 2
        const val CH_GUARDIAN = "guardian"
        const val CH_WARNINGS = "warnings"
        const val CH_REQUESTS = "requests"
        const val CH_DECISIONS = "decisions"
        const val CH_CHAT = "chat"
        const val CH_SOCIAL = "social"
        const val CH_ALERTS = "alerts"
        const val CH_UPDATES = "updates"
    }
}
