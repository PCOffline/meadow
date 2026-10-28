package dev.pcoffline.meadow.data

import java.time.LocalDate

/**
 * How someone is doing over time, rather than raw minutes: what Growth shows beside the totals,
 * and what Bumblebees see when it's shared.
 */
data class Progress(
    /** This week so far against the same stretch of last week, in percent; null without a fair comparison. */
    val weekChange: Int?,
    /** Finished weeks in a row, up to last week, each with less screen time than the one before. */
    val calmerWeeks: Int,
    /** Of the last [limitDays] finished days, how many kept every daily limit. */
    val keptDays: Int,
    /** Finished days that could be judged (recorded, with daily limits), up to [KEPT_WINDOW]. */
    val limitDays: Int,
) {
    /** Whether there's anything to show yet. */
    val isEmpty: Boolean get() = weekChange == null && calmerWeeks == 0 && limitDays == 0

    companion object {
        /** How many finished days "limits kept" looks back over. */
        const val KEPT_WINDOW = 7

        /** How many finished weeks "calmer weeks" looks back over at most. */
        const val WEEKS_BACK = 12

        /** [now] against [before] in percent, or null when there's nothing to compare with. */
        fun change(now: Long, before: Long?): Int? =
            before?.takeIf { it > 0 }?.let { ((now - it) * 100 / it).toInt() }

        /**
         * Weeks in a row with less screen time than the week before, from [weekTotals] (last week
         * first, then the one before, and so on). A week with days missing ends the run, since it
         * can't be compared fairly.
         */
        fun calmerWeeks(weekTotals: List<Long?>): Int {
            var run = 0
            for (i in 0 until weekTotals.lastIndex) {
                val week = weekTotals[i] ?: break
                val before = weekTotals[i + 1] ?: break
                if (week >= before) break
                run++
            }
            return run
        }

        /**
         * Of [days] (finished days, with each app's usage in ms, null where nothing was recorded),
         * how many kept every daily limit in [limits], counting [bonus] extra minutes a Bumblebee
         * gave that day; and how many could be judged.
         */
        fun keptDays(
            days: List<Pair<LocalDate, Map<String, Long>?>>,
            limits: List<AppLimit>,
            bonus: (packageName: String, day: LocalDate) -> Int,
        ): Pair<Int, Int> {
            val daily = limits.filter { it.dailyMinutes > 0 || it.dayMinutes.values.any { m -> m > 0 } }
            if (daily.isEmpty()) return 0 to 0
            val recorded = days.mapNotNull { (day, apps) -> apps?.let { day to it } }
            val kept = recorded.count { (day, apps) ->
                daily.all { limit ->
                    // Each day by its own limit, if it has one.
                    val minutes = limit.dailyMinutesOn(day.dayOfWeek.value)
                    minutes == 0 || (apps[limit.packageName] ?: 0L) <= (minutes + bonus(limit.packageName, day)) * 60_000L
                }
            }
            return kept to recorded.size
        }
    }
}
