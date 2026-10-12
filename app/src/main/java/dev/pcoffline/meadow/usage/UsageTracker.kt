package dev.pcoffline.meadow.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Reads the system usage log; the arithmetic lives in [ForegroundTime]. */
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
        val clippedEnd = minOf(end, System.currentTimeMillis())
        // Look back a little so an app that was already open at `start` is counted from `start`.
        val events = readEvents(start - LOOKBACK_MS, clippedEnd)
        if (events.isEmpty()) {
            // A quiet log for recent hours means nothing was used. The daily aggregates would
            // answer with whole buckets, yesterday's included, so they're only for older days.
            return if (clippedEnd > System.currentTimeMillis() - LOOKBACK_MS) emptyMap() else aggregateFallback(start, clippedEnd)
        }
        return ForegroundTime.totals(events, start, clippedEnd)
    }

    /**
     * Replays events in [from, to) on top of [current] (what was on screen at [from]) and returns
     * what is on screen at [to]. Callers poll with short windows so each check stays cheap.
     */
    fun foregroundAfter(current: String?, from: Long, to: Long): String? {
        if (!hasPermission() || to <= from) return current
        return ForegroundTime.foregroundAfter(current, readEvents(from, to))
    }

    private fun readEvents(from: Long, to: Long): List<ScreenEvent> {
        val log = usm.queryEvents(from, to) ?: return emptyList()
        val result = ArrayList<ScreenEvent>()
        val event = UsageEvents.Event()
        while (log.hasNextEvent()) {
            log.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ScreenEvent.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, EVENT_ACTIVITY_STOPPED -> ScreenEvent.Kind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN, EVENT_DEVICE_SHUTDOWN ->
                    ScreenEvent.Kind.SCREEN_OFF
                EVENT_DEVICE_STARTUP -> ScreenEvent.Kind.STARTUP
                else -> continue
            }
            result += ScreenEvent(event.timeStamp, kind, pkg, event.className)
        }
        return result
    }

    /** Older days may have aged out of the event log; daily aggregates are less precise but kept longer. */
    private fun aggregateFallback(start: Long, end: Long): Map<String, Long> =
        usm.queryAndAggregateUsageStats(start, end)
            .mapValues { (_, stats) -> stats.totalTimeInForeground }
            .filterValues { it > 0 }

    private companion object {
        const val LOOKBACK_MS = 3 * 60 * 60_000L

        // Constants added in later API levels; the values are stable.
        const val EVENT_ACTIVITY_STOPPED = 23
        const val EVENT_DEVICE_SHUTDOWN = 26
        const val EVENT_DEVICE_STARTUP = 27
    }
}
