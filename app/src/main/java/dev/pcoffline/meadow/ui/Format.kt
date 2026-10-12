package dev.pcoffline.meadow.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestType

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

/** "30m a day and 3h a week". */
fun Resources.limitText(dailyMinutes: Int, weeklyMinutes: Int): String = when {
    dailyMinutes > 0 && weeklyMinutes > 0 ->
        getString(R.string.limit_daily_and_weekly, minutesText(dailyMinutes), minutesText(weeklyMinutes))
    dailyMinutes > 0 -> getString(R.string.limit_daily, minutesText(dailyMinutes))
    weeklyMinutes > 0 -> getString(R.string.limit_weekly, minutesText(weeklyMinutes))
    else -> getString(R.string.limit_none)
}

/** What was asked for, as a phrase: "15 more minutes on Instagram". */
fun Resources.requestSummary(request: LimitRequest): String {
    val app = request.appName ?: getString(R.string.request_an_app)
    return when (request.type) {
        RequestType.EXTRA_TIME -> getQuantityString(R.plurals.request_extra_time, request.minutes, request.minutes, app)
        RequestType.CHANGE_LIMIT -> getString(R.string.request_change_limit, app, limitText(request.dailyMinutes, request.weeklyMinutes))
        RequestType.REMOVE_LIMIT -> getString(R.string.request_remove_limit, app)
        RequestType.DISABLE_STRICT_MODE -> getString(R.string.request_disable_strict_mode)
    }
}

@Composable
fun minutesText(minutes: Int): String = LocalContext.current.resources.minutesText(minutes)

@Composable
fun durationText(millis: Long): String = LocalContext.current.resources.durationText(millis)

@Composable
fun limitText(dailyMinutes: Int, weeklyMinutes: Int): String =
    LocalContext.current.resources.limitText(dailyMinutes, weeklyMinutes)

@Composable
fun requestSummary(request: LimitRequest): String = LocalContext.current.resources.requestSummary(request)
