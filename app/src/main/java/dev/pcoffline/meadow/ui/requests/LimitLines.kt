@file:OptIn(ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.requests

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.limits.DayMode
import dev.pcoffline.meadow.ui.limits.Stretch
import dev.pcoffline.meadow.ui.limits.WeekPlan
import dev.pcoffline.meadow.ui.limits.weekColors
import dev.pcoffline.meadow.ui.limits.WeekGrid
import dev.pcoffline.meadow.ui.limits.weekFrom
import dev.pcoffline.meadow.ui.limits.DEFAULT_DRAG_STEP
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.data.dailyMinutesOn
import dev.pcoffline.meadow.ui.capHoursText
import dev.pcoffline.meadow.ui.daysText
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.shortDay
import dev.pcoffline.meadow.ui.windowsText

/** What a change does to one part of a limit. */
enum class LineChange { SAME, CHANGED, ADDED, REMOVED }

/** Which part of a limit a row is about; it sets the row's icon. */
enum class LinePart { DAILY, DAILY_ON, WEEKLY, HOURS, CAP }

/**
 * One day from 00:00 to 24:00 as a bar: the [allowed] stretches and the [caps] in it, in minutes.
 * [nextDay] is the end, on the next day, of a stretch that goes past midnight.
 */
data class DayBar(val allowed: List<IntRange>, val caps: List<IntRange>, val nextDay: Int? = null)

/** A day before and after a change, to compare at a glance. */
data class Bars(val now: DayBar, val new: DayBar)

/**
 * One row of a limit: its part ([label]) and its [value]. A changed part also has the value it
 * [was]. A removed cap's [value] is the cap that goes. [description] is what a screen reader says.
 * A changed day's hours, and a new or removed cap, have [bars].
 */
data class LimitLine(
    val label: String,
    val value: String,
    val was: String?,
    val change: LineChange,
    val description: String,
    val part: LinePart = LinePart.HOURS,
    val bars: Bars? = null,
)

/**
 * The rows of [new], each with what changes from [old]. Without [old], the rows of [new] alone.
 * Each part shows: the daily limit, days with their own, the weekly limit, the allowed hours and
 * each cap. Hours that differ by day show one row for each set of days with the same change;
 * the days that don't change share one "Other days" row.
 */
fun Resources.limitLines(new: AppLimit, old: AppLimit?): List<LimitLine> {
    val was = old ?: new
    val diff = old != null
    val lines = mutableListOf<LimitLine>()

    fun line(label: String, before: String, after: String, part: LinePart, bars: Bars? = null) {
        lines += if (before == after) {
            val description = if (diff) R.string.limit_line_same_description else R.string.limit_line_description
            LimitLine(label, after, null, LineChange.SAME, getString(description, label, after), part)
        } else {
            LimitLine(label, after, before, LineChange.CHANGED, getString(R.string.limit_line_changed_description, label, before, after), part, bars)
        }
    }
    fun minutes(m: Int) = if (m > 0) minutesText(m) else getString(R.string.limit_no_limit).capitalized()

    if (new.dailyMinutes > 0 || was.dailyMinutes > 0) {
        line(getString(R.string.limit_line_daily), minutes(was.dailyMinutes), minutes(new.dailyMinutes), LinePart.DAILY)
    }
    val ownDays = (1..7).filter { it in new.dayMinutes || it in was.dayMinutes }
    for ((days, pair) in groupDays(ownDays) { was.dailyMinutesOn(it) to new.dailyMinutesOn(it) }) {
        line(getString(R.string.limit_line_daily_on, dayNames(days)), minutes(pair.first), minutes(pair.second), LinePart.DAILY_ON)
    }
    if (new.weeklyMinutes > 0 || was.weeklyMinutes > 0) {
        line(getString(R.string.limit_line_weekly), minutes(was.weeklyMinutes), minutes(new.weeklyMinutes), LinePart.WEEKLY)
    }
    if (new.schedule != null || was.schedule != null) {
        // A day's hours as the week card shows them: a stretch past midnight on the day it starts.
        fun on(limit: AppLimit, day: Int): Pair<DayMode, List<Stretch>> {
            val stretches = WeekPlan.stretchesOn(limit.schedule, day)
            val mode = WeekPlan.modeOf(limit.schedule, day)
            // A day whose only hours go on from the day before has none of its own.
            return (if (mode == DayMode.HOURS && stretches.isEmpty()) DayMode.RESTING else mode) to stretches
        }
        // Days with the same hours share a row: the key leaves out which day a stretch is on.
        fun key(day: Pair<DayMode, List<Stretch>>) = day.first to day.second.map { it.from to it.to }
        val groups = groupDays((1..7).toList()) { key(on(was, it)) to key(on(new, it)) }
        val someChange = groups.any { (_, pair) -> pair.first != pair.second }
        val sameGroups = groups.count { (_, pair) -> pair.first == pair.second }
        for ((days, pair) in groups) {
            val label = when {
                groups.size == 1 -> getString(R.string.limit_line_hours)
                pair.first == pair.second && someChange && sameGroups == 1 -> getString(R.string.limit_line_other_days)
                else -> getString(R.string.limit_line_hours_on, dayNames(days))
            }
            val day = days.first()
            line(label, hoursText(on(was, day)).capitalized(), hoursText(on(new, day)).capitalized(), LinePart.HOURS, Bars(bar(was, day), bar(new, day)))
        }
    }
    val cap = getString(R.string.limit_line_cap)
    fun capText(c: TimeCap) =
        getString(R.string.limit_cap, minutesText(c.minutes), capHoursText(c), daysText(c.days))
    for (c in new.caps) {
        val text = capText(c)
        lines += if (!diff || c in was.caps) {
            val description = if (diff) R.string.limit_line_same_description else R.string.limit_line_description
            LimitLine(cap, text, null, LineChange.SAME, getString(description, cap, text), LinePart.CAP)
        } else {
            val day = c.days.first()
            LimitLine(cap, text, null, LineChange.ADDED, getString(R.string.limit_line_new_description, text), LinePart.CAP, Bars(bar(was, day, null), bar(new, day, c)))
        }
    }
    if (diff) {
        for (c in was.caps.filter { it !in new.caps }) {
            val text = capText(c)
            val day = c.days.first()
            lines += LimitLine(cap, text, null, LineChange.REMOVED, getString(R.string.limit_line_removed_description, text), LinePart.CAP, Bars(bar(was, day, c), bar(new, day, null)))
        }
    }
    return lines
}

/** A day's hours in words: "10:00–02:00", "all day" or "not allowed". */
private fun Resources.hoursText(day: Pair<DayMode, List<Stretch>>): String = when (day.first) {
    DayMode.ALL_DAY -> windowsText(listOf(TimeWindow.ALL_DAY))
    DayMode.RESTING -> windowsText(emptyList())
    DayMode.HOURS -> day.second.joinToString(", ") { getString(R.string.schedule_window, clockText(it.from), clockText(it.to % TimeWindow.MINUTES_IN_DAY)) }
}

/** [day] of [limit] as a bar: its stretches, and [cap] (or each cap that day when not given). */
private fun bar(limit: AppLimit, day: Int, cap: TimeCap? = null, allCaps: Boolean = cap == null): DayBar {
    val stretches = when (WeekPlan.modeOf(limit.schedule, day)) {
        DayMode.ALL_DAY -> listOf(Stretch(day, 0, TimeWindow.MINUTES_IN_DAY))
        DayMode.RESTING -> emptyList()
        DayMode.HOURS -> WeekPlan.stretchesOn(limit.schedule, day)
    }
    val capsOn = (if (allCaps) limit.caps else listOfNotNull(cap)).filter { day in it.days }
    return DayBar(
        allowed = stretches.map { it.from until minOf(it.to, TimeWindow.MINUTES_IN_DAY) },
        caps = capsOn.map { it.from until minOf(it.to, TimeWindow.MINUTES_IN_DAY) },
        nextDay = stretches.lastOrNull()?.takeIf { it.pastMidnight }?.let { it.to - TimeWindow.MINUTES_IN_DAY },
    )
}

/** [days] in order, grouped by [key], each group in the order of its first day. */
private fun <K> groupDays(days: List<Int>, key: (Int) -> K): List<Pair<List<Int>, K>> =
    days.groupBy(key).map { (k, ds) -> ds to k }

private fun dayNames(days: List<Int>): String = days.joinToString(", ") { shortDay(it) }

private fun String.capitalized() = replaceFirstChar { it.uppercase() }

/**
 * The rows of a limit, as [limitLines] makes them, each with the icon of its part. A part that
 * changes has a tint (none when not [tinted]) and its old value struck through. A new or removed
 * cap says so in words, not only in colour. A changed day's hours, and a new or removed cap, have
 * Now and New bars under the row.
 */
@Composable
fun LimitLines(lines: List<LimitLine>, modifier: Modifier = Modifier, tinted: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { LimitLineRow(it, tinted) }
    }
}

@Composable
private fun LimitLineRow(line: LimitLine, tinted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !tinted || line.change == LineChange.SAME -> Color.Transparent
        line.change == LineChange.REMOVED -> colors.errorContainer
        else -> colors.primaryContainer
    }
    val onTint = when {
        line.change == LineChange.SAME -> colors.onSurfaceVariant
        !tinted -> colors.onSurface
        line.change == LineChange.REMOVED -> colors.onErrorContainer
        else -> colors.onPrimaryContainer
    }
    val iconTint = when {
        line.change == LineChange.SAME -> colors.onSurfaceVariant
        line.change == LineChange.REMOVED -> colors.error
        else -> colors.primary
    }
    val body = MaterialTheme.typography.bodyMedium
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(tint)
            .clearAndSetSemantics { contentDescription = line.description }
            .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                when (line.part) {
                    LinePart.DAILY -> Icons.Outlined.Today
                    LinePart.DAILY_ON -> Icons.Outlined.CalendarToday
                    LinePart.WEEKLY -> Icons.Outlined.DateRange
                    LinePart.HOURS -> Icons.Outlined.Schedule
                    LinePart.CAP -> Icons.Outlined.Timer
                },
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
            Text(
                line.label,
                style = MaterialTheme.typography.labelLarge,
                color = onTint,
                modifier = Modifier.width(LABEL_WIDTH).padding(end = 10.dp),
            )
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                when (line.change) {
                    LineChange.SAME -> Text(line.value, style = body, color = onTint)
                    LineChange.CHANGED -> {
                        Text(line.was.orEmpty(), style = body.copy(textDecoration = TextDecoration.LineThrough), color = onTint)
                        Text("→", style = body, color = onTint, fontWeight = FontWeight.ExtraBold)
                        Text(line.value, style = body, color = onTint, fontWeight = FontWeight.ExtraBold)
                    }
                    LineChange.ADDED -> {
                        Badge(R.string.limit_line_new, colors.primary, colors.onPrimary)
                        Text(line.value, style = body, color = onTint, fontWeight = FontWeight.ExtraBold)
                    }
                    LineChange.REMOVED -> {
                        Badge(R.string.limit_line_removed, colors.error, colors.onError)
                        Text(line.value, style = body.copy(textDecoration = TextDecoration.LineThrough), color = onTint)
                    }
                }
            }
        }
        if (line.change != LineChange.SAME) line.bars?.let { NowAndNew(it, Modifier.padding(start = 28.dp, top = 6.dp)) }
    }
}

/** Two bars of the same day from 00 to 24: now, faded, above the new one. */
@Composable
private fun NowAndNew(bars: Bars, modifier: Modifier = Modifier) {
    val colors = weekColors()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(R.string.limit_line_now to bars.now, R.string.limit_line_new_bar to bars.new).forEachIndexed { i, (label, bar) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == 0) muted else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(36.dp),
                )
                Canvas(Modifier.weight(1f).height(12.dp).alpha(if (i == 0) 0.6f else 1f)) {
                    val radius = CornerRadius(4.dp.toPx())
                    fun x(minute: Int) = minute / TimeWindow.MINUTES_IN_DAY.toFloat() * size.width
                    drawRoundRect(colors.restingA, size = size, cornerRadius = radius)
                    bar.allowed.forEach { drawRect(colors.allowed, Offset(x(it.first), 0f), Size(x(it.last + 1) - x(it.first), size.height)) }
                    bar.caps.forEach { drawRect(colors.cap, Offset(x(it.first), 2.dp.toPx()), Size(x(it.last + 1) - x(it.first), size.height - 4.dp.toPx())) }
                }
            }
        }
        Row(Modifier.padding(start = 36.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 6, 12, 18, 24).forEach { Text("%02d".format(it), style = MaterialTheme.typography.labelSmall, color = muted) }
        }
        bars.new.nextDay?.let {
            Text("→ " + clockText(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun Badge(@StringRes text: Int, background: Color, content: Color) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelSmall,
        color = content,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(MaterialTheme.shapes.extraLarge).background(background).padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/** Wide enough for "Other days" and "Hours, Sat" at the usual text size; longer labels wrap. */
private val LABEL_WIDTH = 96.dp

/** "The week if approved": the whole limit as asked, read only, with no marks for the change. */
@Composable
fun WeekIfApproved(limit: AppLimit, modifier: Modifier = Modifier) {
    val week by dev.pcoffline.meadow.AppGraph.store.weekRule.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.request_week_if_approved),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        // Each changed row says the change in words, so the picture is left out for screen readers.
        Box(Modifier.padding(top = 4.dp).clearAndSetSemantics { }) {
            WeekGrid(
                schedule = limit.schedule,
                caps = limit.caps,
                days = weekFrom(week.first),
                step = DEFAULT_DRAG_STEP,
                hoursShown = 24f,
                scroll = rememberScrollState(),
                colors = weekColors(),
                picked = null,
                redrawing = null,
                enabled = false,
                onZoom = {},
                onTap = { _, _ -> },
                onDraw = { _, _, _ -> },
                onResize = { _, _ -> },
                dayDescription = { "" },
                dayActions = { emptyList() },
                height = 140.dp,
            )
        }
    }
}
