package dev.pcoffline.meadow.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object Days {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now(zone)

    fun key(date: LocalDate): String = date.toString()

    fun todayKey(): String = key(today())

    fun parse(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    fun startMillis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endMillis(date: LocalDate): Long = startMillis(date.plusDays(1))

    fun weekStart(date: LocalDate, first: DayOfWeek = DayOfWeek.MONDAY): LocalDate = date.with(TemporalAdjusters.previousOrSame(first))

    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

/**
 * The day weeks start on. Growth and the calendars follow [first] at once. Weekly limits keep the
 * week they're in until it ends ([limitsFrom]), so moving the start can't begin a fresh week, and a
 * fresh weekly allowance, early. From then the new weeks may reach back over days the old one
 * counted: a few days counted twice is stricter, never looser.
 */
data class WeekRule(
    val first: DayOfWeek = DayOfWeek.MONDAY,
    val previous: DayOfWeek = first,
    val limitsFrom: LocalDate? = null,
) {
    /** The start of the week [date] is in, as shown. */
    fun start(date: LocalDate): LocalDate = Days.weekStart(date, first)

    /** The start of the week [date] counts toward for weekly limits. */
    fun limitStart(date: LocalDate): LocalDate =
        if (limitsFrom == null || !date.isBefore(limitsFrom)) start(date) else Days.weekStart(date, previous)

    /** Whether weekly limits still follow the old start on [today], and until when. */
    fun pendingOn(today: LocalDate): LocalDate? = limitsFrom?.takeIf { today.isBefore(it) }

    /** Weeks starting on [day] from [today] on: at once for display, from the next week for limits. */
    fun changedTo(day: DayOfWeek, today: LocalDate): WeekRule {
        val week = limitStart(today)
        val inForce = week.dayOfWeek
        return if (day == inForce) WeekRule(day) else WeekRule(first = day, previous = inForce, limitsFrom = week.plusWeeks(1))
    }

    companion object {
        /** The days a week can start on. */
        val CHOICES = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)
    }
}
