package dev.pcoffline.meadow.ui.limits

import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow

private const val DAY = TimeWindow.MINUTES_IN_DAY

/** Most stretches of allowed hours a day can have; matches the backend's MAX_WINDOWS. */
const val MAX_WINDOWS = 8

/** The steps a drag can move in, in minutes. The shortest stretch is one step. */
val DRAG_STEPS = listOf(5, 10, 15, 30)

/** The step a drag moves in at first. */
const val DEFAULT_DRAG_STEP = 10

/** The day after [day] (1 = Monday … 7 = Sunday). */
internal fun nextDay(day: Int) = day % 7 + 1

/** The day before [day]. */
internal fun previousDay(day: Int) = (day + 5) % 7 + 1

/**
 * Allowed hours as the week card draws them: from [from] on [day] to [to], in minutes since that
 * day's midnight. A stretch past midnight ends on the next day, so [to] can pass 1440.
 */
data class Stretch(val day: Int, val from: Int, val to: Int) {
    val pastMidnight: Boolean get() = to > DAY
}

/** How a day's hours are set: allowed all day, its own stretches, or resting all day. */
enum class DayMode { ALL_DAY, HOURS, RESTING }

/**
 * A week's allowed hours, and the edits the week card makes to them. [schedule] null allows each
 * hour. A day that isn't listed allows all of it, and a day with no windows allows none of it.
 * Stored windows stay within a day: a stretch past midnight is stored as two windows, which this
 * joins again.
 */
object WeekPlan {

    fun modeOf(schedule: Schedule?, day: Int): DayMode {
        val windows = schedule?.days?.get(day) ?: return DayMode.ALL_DAY
        return when {
            windows.isEmpty() -> DayMode.RESTING
            Schedule.merge(windows) == listOf(TimeWindow.ALL_DAY) -> DayMode.ALL_DAY
            else -> DayMode.HOURS
        }
    }

    /**
     * Each day's stretches, in order: a window that ends at midnight joins the next day's window
     * that starts at midnight, while the two together last less than a day. A day that allows all
     * of it, or none, has none.
     */
    fun stretches(schedule: Schedule?): List<Stretch> {
        if (schedule == null) return emptyList()
        return (1..7).flatMap { day -> stretchesOn(schedule, day) }
    }

    /** The stretches that start on [day]. */
    fun stretchesOn(schedule: Schedule?, day: Int): List<Stretch> {
        if (schedule == null || modeOf(schedule, day) != DayMode.HOURS) return emptyList()
        val windows = Schedule.merge(schedule.days[day].orEmpty())
        val before = schedule.days[previousDay(day)]?.let(Schedule::merge).orEmpty()
        return windows.mapNotNull { w ->
            if (w.from == 0 && before.lastOrNull()?.let { joins(it, w) } == true) return@mapNotNull null
            val after = schedule.days[nextDay(day)]?.let(Schedule::merge)?.firstOrNull()
            if (after != null && joins(w, after)) Stretch(day, w.from, DAY + after.to) else Stretch(day, w.from, w.to)
        }
    }

    /** Whether [end], which ends a day, and [start], which starts the next, read as one stretch. */
    private fun joins(end: TimeWindow, start: TimeWindow): Boolean =
        end.to == DAY && end.from > 0 && start.from == 0 && start.to < DAY && start.to <= end.from

    /**
     * [schedule] with a stretch from [from] to [to] on each of [days]. The first stretch on a day
     * that allowed all of it sets that day's hours. A stretch past midnight also allows the next
     * day's early hours, unless that day already allows all of it. Throws [TooManyStretches] when
     * a day would have more than [MAX_WINDOWS].
     */
    fun add(schedule: Schedule?, days: Collection<Int>, from: Int, to: Int): Schedule? {
        require(from in 0 until DAY && to > from && to <= from + DAY) { "Bad stretch $from–$to" }
        val out = full(schedule).toMutableMap()
        for (day in days) {
            out[day] = hoursOf(out[day]) + TimeWindow(from, minOf(to, DAY))
            if (to > DAY) {
                val next = nextDay(day)
                if (out[next] != null) out[next] = hoursOf(out[next]) + TimeWindow(0, to - DAY)
            }
        }
        return tidy(out)
    }

    /** [schedule] without [stretch], both halves of it past midnight. */
    fun remove(schedule: Schedule?, stretch: Stretch): Schedule? {
        val out = full(schedule).toMutableMap()
        out[stretch.day] = out[stretch.day]?.let(Schedule::merge)?.filterNot { it.from == stretch.from && it.to == minOf(stretch.to, DAY) }
        if (stretch.pastMidnight) {
            val next = nextDay(stretch.day)
            out[next] = out[next]?.let(Schedule::merge)?.filterNot { it.from == 0 && it.to == stretch.to - DAY }
        }
        // A day left with no stretch allows all of it again, as a day where nothing was drawn.
        for (day in listOf(stretch.day, nextDay(stretch.day))) {
            if (out[day]?.isEmpty() == true && schedule?.days?.get(day)?.isNotEmpty() == true) out[day] = null
        }
        return tidy(out)
    }

    /** [schedule] with [old] drawn again as [days] from [from] to [to]. Throws [TooManyStretches] as [add]. */
    fun replace(schedule: Schedule?, old: Stretch, days: Collection<Int>, from: Int, to: Int): Schedule? =
        add(remove(schedule, old), days, from, to)

    /** [schedule] with [day] allowed all day, resting all day, or with [hours]. */
    fun setMode(schedule: Schedule?, day: Int, mode: DayMode, hours: List<TimeWindow> = emptyList()): Schedule? {
        val out = full(schedule).toMutableMap()
        out[day] = when (mode) {
            DayMode.ALL_DAY -> null
            DayMode.RESTING -> emptyList()
            DayMode.HOURS -> hours.ifEmpty { null }
        }
        return tidy(out)
    }

    /** [schedule] with [stretch] also on each of [days]. Throws [TooManyStretches] as [add]. */
    fun duplicate(schedule: Schedule?, stretch: Stretch, days: Collection<Int>): Schedule? =
        add(schedule, days - stretch.day, stretch.from, stretch.to)

    /** [schedule] with each of [days] set as [day] is: allowed all day, resting, or the same hours. */
    fun duplicateDay(schedule: Schedule?, day: Int, days: Collection<Int>): Schedule? {
        val out = full(schedule).toMutableMap()
        for (other in days - day) out[other] = out[day]
        return tidy(out)
    }

    /** Each listed day, or null for a day that allows all of it. */
    private fun full(schedule: Schedule?): Map<Int, List<TimeWindow>?> = (1..7).associateWith { schedule?.days?.get(it) }

    /** A day's windows to draw on: a day that allowed all of it starts with none. */
    private fun hoursOf(windows: List<TimeWindow>?): List<TimeWindow> =
        windows?.let(Schedule::merge)?.takeUnless { it == listOf(TimeWindow.ALL_DAY) }.orEmpty()

    /** Days merged; null when no day is listed, as a limit with no hours. Throws [TooManyStretches]. */
    private fun tidy(days: Map<Int, List<TimeWindow>?>): Schedule? {
        val listed = days.filterValues { it != null }.mapValues { (_, w) -> Schedule.merge(w!!) }
        if (listed.values.any { it.size > MAX_WINDOWS }) throw TooManyStretches()
        return if (listed.isEmpty()) null else Schedule(listed)
    }

    /** [minute] moved onto the nearest [step], from 0 to [max]. */
    fun snap(minute: Int, step: Int, max: Int = 2 * DAY): Int = ((minute + step / 2) / step * step).coerceIn(0, max)

    /** How many hours the grid shows at once for [step]: all day for the larger steps. */
    fun hoursOnScreen(step: Int): Int = when {
        step <= 5 -> 6
        step <= 10 -> 12
        else -> 24
    }

    /**
     * A cap's parts as the week card draws them on [day]: its own hours, and the early hours of a
     * cap past midnight from the day before.
     */
    fun capPartsOn(caps: List<TimeCap>, day: Int): List<Pair<TimeCap, IntRange>> = caps.flatMap { cap ->
        buildList {
            if (day in cap.days) add(cap to (cap.from until minOf(cap.to, DAY)))
            if (cap.pastMidnight && previousDay(day) in cap.days) add(cap to (0 until cap.to - DAY))
        }
    }
}

/** A day would have more than [MAX_WINDOWS] stretches. */
class TooManyStretches : IllegalStateException("A day can have up to $MAX_WINDOWS stretches.")
