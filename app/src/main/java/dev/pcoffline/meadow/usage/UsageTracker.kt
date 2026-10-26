package dev.pcoffline.meadow.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Reads the system usage log; the arithmetic lives in [ForegroundTime]. */
class UsageTracker(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        // Android 16 made checkOpNoThrow the current call again and deprecated the "unsafe" name.
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Milliseconds of foreground time per package inside [start, end), or null when the phone no
     * longer keeps a record that fine (see [dailyTotals]).
     */
    fun usageBetween(start: Long, end: Long): Map<String, Long>? {
        if (!hasPermission() || end <= start) return emptyMap()
        val now = System.currentTimeMillis()
        val clippedEnd = minOf(end, now)
        // Look back a little so an app that was already open at `start` is counted from `start`.
        val events = readEvents(start - LOOKBACK_MS, clippedEnd)
        // The log covers the whole stretch when it's recent, or when it reaches back past its start:
        // then a quiet log means nothing was used.
        if (start >= now - LOG_KEPT_MS || events.firstOrNull()?.let { it.time < start } == true) {
            return ForegroundTime.totals(events, start, clippedEnd)
        }
        return dailyTotals(start, clippedEnd) ?: events.takeIf { it.isNotEmpty() }?.let { ForegroundTime.totals(it, start, clippedEnd) }
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

    /**
     * Older days may have aged out of the event log. Android keeps daily totals a while longer,
     * then only weekly, monthly and yearly ones; those would count the other days in them too, so
     * only daily totals inside [start, end) are used, and without any the answer is null.
     */
    private fun dailyTotals(start: Long, end: Long): Map<String, Long>? {
        val days = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).orEmpty()
            .filter { it.firstTimeStamp >= start - BUCKET_SLACK_MS && it.lastTimeStamp <= end + BUCKET_SLACK_MS }
        if (days.isEmpty()) return null
        val totals = HashMap<String, Long>()
        days.forEach { if (it.totalTimeInForeground > 0) totals[it.packageName] = (totals[it.packageName] ?: 0L) + it.totalTimeInForeground }
        return totals
    }

    private companion object {
        const val LOOKBACK_MS = 3 * 60 * 60_000L

        // Android keeps the event log for about a week or more; inside that, it's complete.
        const val LOG_KEPT_MS = 6 * 24 * 60 * 60_000L

        // Daily totals start and end near midnight, not on it.
        const val BUCKET_SLACK_MS = 2 * 60 * 60_000L

        // Constants added in later API levels; the values are stable.
        const val EVENT_ACTIVITY_STOPPED = 23
        const val EVENT_DEVICE_SHUTDOWN = 26
        const val EVENT_DEVICE_STARTUP = 27
    }
}
