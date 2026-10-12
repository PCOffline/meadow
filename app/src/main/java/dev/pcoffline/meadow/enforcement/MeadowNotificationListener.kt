package dev.pcoffline.meadow.enforcement

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.pcoffline.meadow.AppGraph

/**
 * Keeps resting apps quiet: their notifications are hidden as they arrive and their media is
 * paused, so a resting app can't be used from the shade, a media player or a floating window.
 */
class MeadowNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        sweep()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || !resting(sbn.packageName)) return
        quiet(sbn)
        pauseMedia(this, setOf(sbn.packageName))
    }

    /** Hides what every resting app is showing right now. */
    fun sweep(only: String? = null) {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val restingNow = HashMap<String, Boolean>()
        for (sbn in active) {
            val pkg = sbn.packageName
            if (pkg == packageName || (only != null && pkg != only)) continue
            if (restingNow.getOrPut(pkg) { pkg == only || resting(pkg) }) quiet(sbn)
        }
        pauseMedia(this, if (only != null) setOf(only) else restingNow.filterValues { it }.keys)
    }

    private fun quiet(sbn: StatusBarNotification) {
        runCatching { cancelNotification(sbn.key) }.onFailure { Log.w(TAG, "Could not hide a notification", it) }
    }

    private fun resting(pkg: String): Boolean = AppGraph.engine.budget(pkg)?.exhausted == true

    companion object {
        private const val TAG = "MeadowListener"

        @Volatile private var instance: MeadowNotificationListener? = null

        /** Called when an app starts resting: hides its notifications and stops its media. */
        fun rest(context: Context, pkg: String) {
            instance?.sweep(only = pkg) ?: pauseMedia(context, setOf(pkg))
        }

        /** Hides notifications of apps that started resting since they were posted. */
        fun sweepAll() {
            instance?.sweep()
        }

        /** Pauses media sessions of [packages]; needs notification access to see other apps' sessions. */
        fun pauseMedia(context: Context, packages: Set<String>) {
            if (packages.isEmpty()) return
            runCatching {
                val sessions = context.getSystemService(MediaSessionManager::class.java)
                    .getActiveSessions(ComponentName(context, MeadowNotificationListener::class.java))
                sessions.filter { it.packageName in packages && it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    .forEach { it.transportControls.pause() }
            }
        }

        /**
         * Ends a resting app's process once it is in the background, so nothing it left behind
         * (a floating window, a player) keeps running. Android only lets this stop background apps.
         */
        fun stopInBackground(context: Context, pkg: String) {
            runCatching { context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg) }
                .onFailure { Log.w(TAG, "Could not stop $pkg", it) }
        }
    }
}
