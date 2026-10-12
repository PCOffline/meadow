package dev.pcoffline.meadow.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
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

/** Like [minutesText], but shows "<1m" for a few seconds instead of "0m". */
fun Resources.durationText(millis: Long): String {
    val minutes = (millis / 60_000L).toInt()
    return if (minutes == 0 && millis > 0) getString(R.string.duration_under_a_minute) else minutesText(minutes)
}

/** "30m a day and 3h a week", plus the allowed hours when there are any. */
fun Resources.limitText(dailyMinutes: Int, weeklyMinutes: Int, schedule: Schedule? = null): String {
    val minutes = when {
        dailyMinutes > 0 && weeklyMinutes > 0 ->
            getString(R.string.limit_daily_and_weekly, minutesText(dailyMinutes), minutesText(weeklyMinutes))
        dailyMinutes > 0 -> getString(R.string.limit_daily, minutesText(dailyMinutes))
        weeklyMinutes > 0 -> getString(R.string.limit_weekly, minutesText(weeklyMinutes))
        else -> null
    }
    val hours = schedule?.let { scheduleText(it) }
    return when {
        minutes != null && hours != null -> getString(R.string.limit_and_hours, minutes, hours)
        else -> minutes ?: hours ?: getString(R.string.limit_none)
    }
}

/** "10:00", in the phone's own clock style. */
fun clockText(minuteOfDay: Int): String =
    LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** "10:00–12:00, 18:00–22:00". */
fun Resources.windowsText(windows: List<TimeWindow>): String = when {
    windows.isEmpty() -> getString(R.string.schedule_closed)
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
            getString(R.string.request_change_limit, app, limitText(request.dailyMinutes, request.weeklyMinutes, request.schedule))
        RequestType.REMOVE_LIMIT -> getString(R.string.request_remove_limit, app)
        RequestType.DISABLE_STRICT_MODE -> getString(R.string.request_disable_strict_mode)
    }
}

@Composable
fun minutesText(minutes: Int): String = LocalContext.current.resources.minutesText(minutes)

@Composable
fun durationText(millis: Long): String = LocalContext.current.resources.durationText(millis)

@Composable
fun limitText(dailyMinutes: Int, weeklyMinutes: Int, schedule: Schedule? = null): String =
    LocalContext.current.resources.limitText(dailyMinutes, weeklyMinutes, schedule)

@Composable
fun requestSummary(request: LimitRequest): String = LocalContext.current.resources.requestSummary(request)
