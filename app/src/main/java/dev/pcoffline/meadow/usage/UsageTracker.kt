package dev.pcoffline.meadow.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Computes foreground time per app from the system usage event log. Only one app is treated as
 * being in the foreground at a time, which matches what the person actually sees on screen.
 */
class UsageTracker(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Milliseconds of foreground time per package inside [start, end). */
    fun usageBetween(start: Long, end: Long): Map<String, Long> {
        if (!hasPermission() || end <= start) return emptyMap()
        val now = System.currentTimeMillis()
        val clippedEnd = minOf(end, now)
        // Look back a little so an app that was already open at `start` is counted from `start`.
        val events = usm.queryEvents(start - LOOKBACK_MS, clippedEnd) ?: return emptyMap()
        val totals = HashMap<String, Long>()
        var current: String? = null
        var since = 0L
        var sawEvent = false

        fun close(at: Long) {
            val pkg = current ?: return
            val from = maxOf(since, start)
            val to = minOf(at, clippedEnd)
            if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            current = null
        }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            sawEvent = true
            val pkg = event.packageName ?: continue
            val t = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (current != pkg) {
                        close(t)
                        current = pkg
                        since = t
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, EVENT_ACTIVITY_STOPPED -> {
                    if (current == pkg) close(t)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                EVENT_DEVICE_SHUTDOWN -> close(t)
            }
        }
        close(clippedEnd)
        if (!sawEvent) return aggregateFallback(start, clippedEnd)
        return totals
    }

    /** The app the event log says is currently on screen, if any. */
    fun currentForegroundApp(): String? {
        if (!hasPermission()) return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60 * 60_000L, now) ?: return null
        var current: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> current = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED, EVENT_ACTIVITY_STOPPED ->
                    if (current == event.packageName) current = null
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> current = null
            }
        }
        return current
    }

    /** Older days may have aged out of the event log; daily aggregates are less precise but kept longer. */
    private fun aggregateFallback(start: Long, end: Long): Map<String, Long> =
        usm.queryAndAggregateUsageStats(start, end)
            .mapValues { (_, stats) -> stats.totalTimeInForeground }
            .filterValues { it > 0 }

    private companion object {
        const val LOOKBACK_MS = 3 * 60 * 60_000L
        // Public constants that only exist on newer API levels.
        const val EVENT_ACTIVITY_STOPPED = 23
        const val EVENT_DEVICE_SHUTDOWN = 26
    }
}
