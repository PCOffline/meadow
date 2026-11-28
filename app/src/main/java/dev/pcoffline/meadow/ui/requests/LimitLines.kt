@file:OptIn(ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.requests

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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

/**
 * One row of a limit: its part ([label]) and its [value]. A changed part also has the value it
 * [was]. A removed cap's [value] is the cap that goes. [description] is what a screen reader says.
 */
data class LimitLine(val label: String, val value: String, val was: String?, val change: LineChange, val description: String)

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

    fun line(label: String, before: String, after: String) {
        lines += if (before == after) {
            val description = if (diff) R.string.limit_line_same_description else R.string.limit_line_description
            LimitLine(label, after, null, LineChange.SAME, getString(description, label, after))
        } else {
            LimitLine(label, after, before, LineChange.CHANGED, getString(R.string.limit_line_changed_description, label, before, after))
        }
    }
    fun minutes(m: Int) = if (m > 0) minutesText(m) else getString(R.string.limit_no_limit).capitalized()

    if (new.dailyMinutes > 0 || was.dailyMinutes > 0) {
        line(getString(R.string.limit_line_daily), minutes(was.dailyMinutes), minutes(new.dailyMinutes))
    }
    val ownDays = (1..7).filter { it in new.dayMinutes || it in was.dayMinutes }
    for ((days, pair) in groupDays(ownDays) { was.dailyMinutesOn(it) to new.dailyMinutesOn(it) }) {
        line(getString(R.string.limit_line_daily_on, dayNames(days)), minutes(pair.first), minutes(pair.second))
    }
    if (new.weeklyMinutes > 0 || was.weeklyMinutes > 0) {
        line(getString(R.string.limit_line_weekly), minutes(was.weeklyMinutes), minutes(new.weeklyMinutes))
    }
    if (new.schedule != null || was.schedule != null) {
        fun on(limit: AppLimit, day: Int) = limit.schedule?.windowsOn(day) ?: listOf(TimeWindow.ALL_DAY)
        val groups = groupDays((1..7).toList()) { on(was, it) to on(new, it) }
        val someChange = groups.any { (_, pair) -> pair.first != pair.second }
        val sameGroups = groups.count { (_, pair) -> pair.first == pair.second }
        for ((days, pair) in groups) {
            val label = when {
                groups.size == 1 -> getString(R.string.limit_line_hours)
                pair.first == pair.second && someChange && sameGroups == 1 -> getString(R.string.limit_line_other_days)
                else -> getString(R.string.limit_line_hours_on, dayNames(days))
            }
            line(label, windowsText(pair.first).capitalized(), windowsText(pair.second).capitalized())
        }
    }
    val cap = getString(R.string.limit_line_cap)
    fun capText(c: TimeCap) =
        getString(R.string.limit_cap, minutesText(c.minutes), capHoursText(c), daysText(c.days))
    for (c in new.caps) {
        val text = capText(c)
        lines += if (!diff || c in was.caps) {
            val description = if (diff) R.string.limit_line_same_description else R.string.limit_line_description
            LimitLine(cap, text, null, LineChange.SAME, getString(description, cap, text))
        } else {
            LimitLine(cap, text, null, LineChange.ADDED, getString(R.string.limit_line_new_description, text))
        }
    }
    if (diff) {
        for (c in was.caps.filter { it !in new.caps }) {
            val text = capText(c)
            lines += LimitLine(cap, text, null, LineChange.REMOVED, getString(R.string.limit_line_removed_description, text))
        }
    }
    return lines
}

/** [days] in order, grouped by [key], each group in the order of its first day. */
private fun <K> groupDays(days: List<Int>, key: (Int) -> K): List<Pair<List<Int>, K>> =
    days.groupBy(key).map { (k, ds) -> ds to k }

private fun dayNames(days: List<Int>): String = days.joinToString(", ") { shortDay(it) }

private fun String.capitalized() = replaceFirstChar { it.uppercase() }

/**
 * The rows of a limit, as [limitLines] makes them. A part that changes has a tint and its old
 * value struck through. A new or removed cap says so in words, not only in colour.
 */
@Composable
fun LimitLines(lines: List<LimitLine>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { LimitLineRow(it) }
    }
}

@Composable
private fun LimitLineRow(line: LimitLine) {
    val colors = MaterialTheme.colorScheme
    val tint = when (line.change) {
        LineChange.SAME -> Color.Transparent
        LineChange.REMOVED -> colors.errorContainer
        else -> colors.primaryContainer
    }
    val onTint = when (line.change) {
        LineChange.SAME -> colors.onSurfaceVariant
        LineChange.REMOVED -> colors.onErrorContainer
        else -> colors.onPrimaryContainer
    }
    val body = MaterialTheme.typography.bodyMedium
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(tint)
            .clearAndSetSemantics { contentDescription = line.description }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
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
