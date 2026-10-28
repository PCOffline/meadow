package dev.pcoffline.meadow.data

import kotlinx.serialization.Serializable

/**
 * At most [minutes] of use between [from] and [to] (minutes since midnight) on each of [days]
 * (1 = Monday), on top of the daily limit: "up to 30 minutes between 10:00 and 16:00 on
 * weekdays". Must match Cap in the backend's schedule.ts.
 */
@Serializable
data class TimeCap(val days: List<Int>, val from: Int, val to: Int, val minutes: Int) {
    init {
        require(days.isNotEmpty() && days.all { it in 1..7 }) { "Bad days $days" }
        require(from in 0 until TimeWindow.MINUTES_IN_DAY && to in 1..TimeWindow.MINUTES_IN_DAY && from < to) { "Bad window $from–$to" }
        require(minutes in 1 until to - from) { "Bad cap $minutes in $from–$to" }
    }

    val window: TimeWindow get() = TimeWindow(from, to)

    fun toFirestore(): Map<String, Any> = mapOf("days" to days.distinct().sorted(), "from" to from, "to" to to, "minutes" to minutes)

    companion object {
        /** Most caps one limit can have; matches the backend and the rules. */
        const val MAX = 6

        /** Reads the list written by [toFirestore]; anything malformed is skipped. */
        fun listFromFirestore(raw: Any?): List<TimeCap> = (raw as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val days = (m["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.distinct()?.sorted() ?: return@mapNotNull null
            val from = (m["from"] as? Number)?.toInt() ?: return@mapNotNull null
            val to = (m["to"] as? Number)?.toInt() ?: return@mapNotNull null
            val minutes = (m["minutes"] as? Number)?.toInt() ?: return@mapNotNull null
            runCatching { TimeCap(days, from, to, minutes) }.getOrNull()
        }.take(MAX)
    }
}

/** Days with their own daily limit, as stored: "6" to 120 is two hours on Saturdays. */
object DayMinutes {
    fun toFirestore(days: Map<Int, Int>): Map<String, Int> = days.toSortedMap().mapKeys { it.key.toString() }

    /** Anything malformed is skipped. */
    fun fromFirestore(raw: Any?): Map<Int, Int> = (raw as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
        val day = (k as? String)?.toIntOrNull()?.takeIf { it in 1..7 } ?: return@mapNotNull null
        val minutes = (v as? Number)?.toInt()?.takeIf { it in 0..TimeWindow.MINUTES_IN_DAY } ?: return@mapNotNull null
        day to minutes
    }.toMap()
}

/** The daily limit on [dayOfWeek] (1 = Monday): that day's own, or the everyday one. 0 is none. */
fun AppLimit.dailyMinutesOn(dayOfWeek: Int): Int = dayMinutes[dayOfWeek] ?: dailyMinutes

/** Whether anything at all is held back. A day with "no limit" of its own holds nothing back. */
val AppLimit.holdsBack: Boolean
    get() = dailyMinutes > 0 || weeklyMinutes > 0 || schedule != null || dayMinutes.values.any { it > 0 } || caps.isNotEmpty()

/** What only the backend can compare, so with Bumblebees a change to any of it goes through it. */
fun AppLimit.sameUncomparable(other: AppLimit): Boolean =
    schedule == other.schedule && dayMinutes == other.dayMinutes && caps == other.caps

object LimitRules {
    /** 0 means no limit, so anything is at least as strict as 0. */
    private fun stricter(old: Int, new: Int) = old == 0 || (new in 1..old)

    /** Whether [new] still holds back no less than [cap] did on [day]. */
    private fun capKept(cap: TimeCap, day: Int, new: AppLimit): Boolean {
        val daily = new.dailyMinutesOn(day)
        if (daily in 1..cap.minutes) return true
        return new.caps.any { day in it.days && it.from <= cap.from && cap.to <= it.to && it.minutes <= cap.minutes }
    }

    /** Whether [new] lets anything through that [old] held back. Must match isLimitStricter in the backend. */
    fun isLooser(old: AppLimit, new: AppLimit): Boolean =
        (1..7).any { !stricter(old.dailyMinutesOn(it), new.dailyMinutesOn(it)) } ||
            !stricter(old.weeklyMinutes, new.weeklyMinutes) ||
            !Schedule.isStricter(old.schedule, new.schedule) ||
            old.caps.any { cap -> cap.days.any { !capKept(cap, it, new) } }
}
