package dev.pcoffline.meadow.data

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** Allowed hours on one day, in minutes since midnight: 10:00–16:00 is TimeWindow(600, 960). */
@Serializable
data class TimeWindow(val from: Int, val to: Int) {
    init {
        require(from in 0 until MINUTES_IN_DAY && to in 1..MINUTES_IN_DAY && from < to) { "Bad window $from–$to" }
    }

    fun contains(minute: Int) = minute in from until to

    fun covers(other: TimeWindow) = from <= other.from && other.to <= to

    companion object {
        const val MINUTES_IN_DAY = 1440
        val ALL_DAY = TimeWindow(0, MINUTES_IN_DAY)
    }
}

/**
 * When a limited app may be used, per weekday (1 = Monday … 7 = Sunday). A day that isn't listed
 * allows all of it; a day with no windows allows none of it. Must match schedule.ts in the backend.
 */
@Serializable
data class Schedule(val days: Map<Int, List<TimeWindow>> = emptyMap()) {

    /** The allowed windows on [dayOfWeek], merged and sorted. */
    fun windowsOn(dayOfWeek: Int): List<TimeWindow> = days[dayOfWeek]?.let(::merge) ?: listOf(TimeWindow.ALL_DAY)

    fun allows(at: ZonedDateTime): Boolean = windowsOn(at.dayOfWeek.value).any { it.contains(minuteOf(at)) }

    /** Same windows every day, so the editor can show a single row. */
    val sameEveryDay: Boolean get() = (1..7).map(::windowsOn).distinct().size == 1

    /**
     * While allowed: how long until the allowed stretch ends, following it past midnight. While
     * not allowed: how long until it starts. Null when nothing changes within a week.
     */
    fun untilChange(at: ZonedDateTime): Duration? {
        var day = at.toLocalDate()
        val minute = minuteOf(at)
        if (allows(at)) {
            var window = windowsOn(day.dayOfWeek.value).first { it.contains(minute) }
            repeat(7) {
                if (window.to < TimeWindow.MINUTES_IN_DAY) return Duration.between(at, startOf(day, at).plusMinutes(window.to.toLong()))
                day = day.plusDays(1)
                window = windowsOn(day.dayOfWeek.value).firstOrNull { it.from == 0 }
                    ?: return Duration.between(at, startOf(day, at))
            }
            return null
        }
        windowsOn(day.dayOfWeek.value).firstOrNull { it.from > minute }
            ?.let { return Duration.between(at, startOf(day, at).plusMinutes(it.from.toLong())) }
        repeat(7) {
            day = day.plusDays(1)
            windowsOn(day.dayOfWeek.value).firstOrNull()
                ?.let { return Duration.between(at, startOf(day, at).plusMinutes(it.from.toLong())) }
        }
        return null
    }

    fun toFirestore(): Map<String, List<Map<String, Int>>> =
        days.entries.associate { (day, windows) -> day.toString() to merge(windows).map { mapOf("from" to it.from, "to" to it.to) } }

    companion object {
        /** Nothing changed or only fewer hours are allowed. A null schedule allows every hour. */
        fun isStricter(old: Schedule?, new: Schedule?): Boolean = (1..7).all { day ->
            val before = old?.windowsOn(day) ?: listOf(TimeWindow.ALL_DAY)
            val after = new?.windowsOn(day) ?: listOf(TimeWindow.ALL_DAY)
            after.all { w -> before.any { it.covers(w) } }
        }

        fun everyDay(vararg windows: TimeWindow) = Schedule((1..7).associateWith { windows.toList() })

        /** Reads the Firestore map written by [toFirestore]; anything malformed is skipped. */
        fun fromFirestore(raw: Any?): Schedule? {
            val map = raw as? Map<*, *> ?: return null
            val days = map.entries.mapNotNull { (k, v) ->
                val day = (k as? String)?.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
                val windows = (v as? List<*>)?.mapNotNull { w ->
                    val m = w as? Map<*, *> ?: return@mapNotNull null
                    val from = (m["from"] as? Number)?.toInt() ?: return@mapNotNull null
                    val to = (m["to"] as? Number)?.toInt() ?: return@mapNotNull null
                    runCatching { TimeWindow(from, to) }.getOrNull()
                } ?: return@mapNotNull null
                day to merge(windows)
            }.toMap()
            return Schedule(days)
        }

        internal fun merge(windows: List<TimeWindow>): List<TimeWindow> {
            val out = mutableListOf<TimeWindow>()
            for (w in windows.sortedBy { it.from }) {
                val last = out.lastOrNull()
                if (last != null && w.from <= last.to) out[out.lastIndex] = TimeWindow(last.from, maxOf(last.to, w.to)) else out += w
            }
            return out
        }

        private fun minuteOf(at: ZonedDateTime) = at.hour * 60 + at.minute

        private fun startOf(day: LocalDate, at: ZonedDateTime) = day.atStartOfDay(at.zone)
    }
}
