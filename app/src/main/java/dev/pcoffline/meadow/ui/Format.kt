package dev.pcoffline.meadow.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** "45m", "2h", "1h 30m". */
fun Resources.minutesText(minutes: Int): String = when {
    minutes <= 0 -> getString(R.string.duration_minutes, 0)
    minutes < 60 -> getString(R.string.duration_minutes, minutes)
    minutes % 60 == 0 -> getString(R.string.duration_hours, minutes / 60)
    else -> getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

/** Time spent: like [minutesText], but "<1m" for a few seconds, and days and hours past a day. */
fun Resources.durationText(millis: Long): String {
    val minutes = (millis / 60_000L).toInt()
    val days = minutes / MINUTES_PER_DAY
    val hours = minutes % MINUTES_PER_DAY / 60
    return when {
        minutes == 0 && millis > 0 -> getString(R.string.duration_under_a_minute)
        days > 0 && hours > 0 -> getString(R.string.duration_days_hours, days, hours)
        days > 0 -> getString(R.string.duration_days, days)
        else -> minutesText(minutes)
    }
}

private const val MINUTES_PER_DAY = 24 * 60

/**
 * "30m a day and 3h a week", plus days with their own limit, the allowed hours and caps within
 * hours when there are any.
 */
fun Resources.limitText(
    dailyMinutes: Int,
    weeklyMinutes: Int,
    schedule: Schedule? = null,
    dayMinutes: Map<Int, Int> = emptyMap(),
    caps: List<TimeCap> = emptyList(),
): String {
    val minutes = when {
        dailyMinutes > 0 && weeklyMinutes > 0 ->
            getString(R.string.limit_daily_and_weekly, minutesText(dailyMinutes), minutesText(weeklyMinutes))
        dailyMinutes > 0 -> getString(R.string.limit_daily, minutesText(dailyMinutes))
        weeklyMinutes > 0 -> getString(R.string.limit_weekly, minutesText(weeklyMinutes))
        else -> null
    }
    val days = dayMinutes.toSortedMap().entries.groupBy({ it.value }, { it.key }).map { (m, d) ->
        getString(R.string.limit_on_days, if (m > 0) minutesText(m) else getString(R.string.limit_no_limit), daysText(d))
    }
    val hours = schedule?.let { scheduleText(it) }
    val capped = caps.map { getString(R.string.limit_cap, minutesText(it.minutes), windowsText(listOf(it.window)), daysText(it.days)) }
    val parts = listOfNotNull(minutes) + days + listOfNotNull(hours) + capped
    return parts.reduceOrNull { all, part -> getString(R.string.limit_and_hours, all, part) } ?: getString(R.string.limit_none)
}

fun Resources.limitText(limit: AppLimit): String =
    limitText(limit.dailyMinutes, limit.weeklyMinutes, limit.schedule, limit.dayMinutes, limit.caps)

/** "every day", "on weekdays", "at weekends" or "on Mon, Wed". */
fun Resources.daysText(days: Collection<Int>): String {
    val sorted = days.distinct().sorted()
    return when (sorted) {
        (1..7).toList() -> getString(R.string.days_every)
        (1..5).toList() -> getString(R.string.days_weekdays)
        listOf(6, 7) -> getString(R.string.days_weekends)
        else -> getString(R.string.days_some, sorted.joinToString(", ") { shortDay(it) })
    }
}

/** "Mon". */
fun shortDay(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

/** "10:00", in the phone's own clock style. */
fun clockText(minuteOfDay: Int): String =
    LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "10:00–12:00, 18:00–22:00". */
fun Resources.windowsText(windows: List<TimeWindow>): String = when {
    windows.isEmpty() -> getString(R.string.schedule_day_off)
    windows == listOf(TimeWindow.ALL_DAY) -> getString(R.string.schedule_all_day)
    else -> windows.joinToString(", ") { getString(R.string.schedule_window, clockText(it.from), clockText(it.to)) }
}

/** "only 10:00–16:00", or "set hours on each day" when days differ. */
fun Resources.scheduleText(schedule: Schedule): String =
    if (schedule.sameEveryDay) {
        val windows = schedule.windowsOn(1)
        if (windows.isEmpty()) getString(R.string.schedule_never) else getString(R.string.schedule_only, windowsText(windows))
    } else {
        getString(R.string.schedule_by_day)
    }

/** "10:00", or "Mon 10:00" when it's not today. */
fun opensAtText(inMillis: Long, now: ZonedDateTime = ZonedDateTime.now()): String {
    val at = now.plus(java.time.Duration.ofMillis(inMillis))
    val time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return if (at.toLocalDate() == now.toLocalDate()) time
    else at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + time
}

/** What was asked for, as a phrase: "15 more minutes on Instagram". */
fun Resources.requestSummary(request: LimitRequest): String {
    val app = request.appName ?: getString(R.string.request_an_app)
    return when (request.type) {
        RequestType.EXTRA_TIME -> getQuantityString(R.plurals.request_extra_time, request.minutes, request.minutes, app)
        RequestType.CHANGE_LIMIT ->
            getString(
                R.string.request_change_limit, app,
                limitText(request.dailyMinutes, request.weeklyMinutes, request.schedule, request.dayMinutes, request.caps),
            )
        RequestType.REMOVE_LIMIT -> getString(R.string.request_remove_limit, app)
        RequestType.DISABLE_STRICT_MODE -> getString(R.string.request_disable_strict_mode)
    }
}

@Composable
fun minutesText(minutes: Int): String = LocalResources.current.minutesText(minutes)

@Composable
fun durationText(millis: Long): String = LocalResources.current.durationText(millis)

@Composable
fun limitText(limit: AppLimit): String = LocalResources.current.limitText(limit)

@Composable
fun requestSummary(request: LimitRequest): String = LocalResources.current.requestSummary(request)
