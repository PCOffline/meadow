package dev.pcoffline.meadow.usage

import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.LocalStore
import dev.pcoffline.meadow.data.Progress
import java.time.LocalDate

/** Reads [Progress] from the usage kept on this phone. Slow on a cold history: call it off the main thread. */
class ProgressMeter(private val history: UsageHistory, private val store: LocalStore) {

    fun now(today: LocalDate = Days.today(), elapsedMs: Long = System.currentTimeMillis() - Days.startMillis(today)): Progress {
        val weekStart = store.weekRule.value.start(today)
        val thisWeek = total(history.range(weekStart, today))
        // The same stretch of last week: its finished days, and today's weekday up to this time of day.
        val lastWeek = total(history.range(weekStart.minusWeeks(1), today.minusDays(8)))
            ?.plus(history.partialDay(today.minusWeeks(1), elapsedMs).values.sum())
        val weeks = (1..Progress.WEEKS_BACK + 1).map { back ->
            val start = weekStart.minusWeeks(back.toLong())
            total(history.range(start, start.plusDays(6)))
        }
        val grants = store.grants.value
        val (kept, judged) = Progress.keptDays(
            history.range(today.minusDays(Progress.KEPT_WINDOW.toLong()), today.minusDays(1)),
            store.limits.value,
        ) { pkg, day -> grants.filter { it.packageName == pkg && it.dayKey == Days.key(day) }.sumOf { it.minutes } }
        return Progress(
            weekChange = thisWeek?.let { Progress.change(it, lastWeek) },
            calmerWeeks = Progress.calmerWeeks(weeks),
            keptDays = kept,
            limitDays = judged,
        )
    }

    /** A stretch's screen time, or null when a day in it has no record. */
    private fun total(days: List<Pair<LocalDate, Map<String, Long>?>>): Long? =
        if (days.any { it.second == null }) null else days.sumOf { (_, apps) -> apps.orEmpty().values.sum() }
}
