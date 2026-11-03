@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.currentLocale
import dev.pcoffline.meadow.ui.daysText
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.windowsText
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Days sharing their own daily limit; [minutes] 0 is no limit on them. */
@Serializable
data class DayGroup(val days: List<Int>, val minutes: Int)

/** Groups [dayMinutes] by limit, so days set together stay together. */
fun dayGroups(dayMinutes: Map<Int, Int>): List<DayGroup> =
    dayMinutes.toSortedMap().entries.groupBy({ it.value }, { it.key }).map { (m, d) -> DayGroup(d, m) }

/** Back to one limit per day; a group with no days is dropped. */
fun List<DayGroup>.toDayMinutes(): Map<Int, Int> = flatMap { g -> g.days.map { it to g.minutes } }.toMap()

/** The days of the week, starting on [firstDay], as 1 (Monday) to 7. */
fun weekFrom(firstDay: DayOfWeek): List<Int> = (0L until 7L).map { firstDay.plus(it).value }

/**
 * A row of weekday chips, any number picked at once: the way to set several days alike. Days in
 * [taken] belong elsewhere and can't be picked.
 */
@Composable
fun DayChips(
    selected: Collection<Int>,
    firstDay: DayOfWeek,
    enabled: Boolean,
    taken: Collection<Int> = emptyList(),
    onChange: (List<Int>) -> Unit,
) {
    val locale = currentLocale()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekFrom(firstDay).forEach { day ->
            val name = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale)
            val on = day in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - day else (selected + day).sorted()) },
                label = { Text(DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)) },
                enabled = enabled && day !in taken,
                modifier = Modifier.semantics { contentDescription = name },
            )
        }
    }
}

/**
 * Days with a daily limit of their own instead of the everyday one: pick the days (several at
 * once), then their limit, or no limit at all on them.
 */
@Composable
fun DayLimitsEditor(groups: List<DayGroup>, firstDay: DayOfWeek, enabled: Boolean, onChange: (List<DayGroup>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEachIndexed { i, group ->
            HorizontalDivider()
            val taken = groups.filterIndexed { j, _ -> j != i }.flatMap { it.days }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.limit_editor_days_own), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(groups.filterIndexed { j, _ -> j != i }) }, enabled = enabled) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.limit_editor_days_remove))
                }
            }
            DayChips(group.days, firstDay, enabled, taken) { days -> onChange(groups.replace(i, group.copy(days = days))) }
            val noLimit = group.minutes == 0
            if (!noLimit) {
                MinutesStepper(group.minutes, step = 5, min = 5, max = 16 * 60, enabled, label = stringResource(R.string.limit_editor_days_own)) {
                    onChange(groups.replace(i, group.copy(minutes = it)))
                }
            }
            FilterChip(
                selected = noLimit,
                onClick = { onChange(groups.replace(i, group.copy(minutes = if (noLimit) 60 else 0))) },
                label = { Text(stringResource(R.string.limit_editor_days_no_limit)) },
                enabled = enabled,
            )
        }
        val free = (1..7) - groups.flatMap { it.days }.toSet()
        if (free.isNotEmpty()) {
            TextButton(onClick = { onChange(groups + DayGroup(emptyList(), 60)) }, enabled = enabled) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.limit_editor_days_add))
            }
        }
    }
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> = mapIndexed { j, old -> if (j == index) value else old }

/**
 * Caps within hours, each on top of the daily limit: "up to 30m between 10:00 and 16:00 on
 * weekdays". All of them apply.
 */
@Composable
fun CapsEditor(caps: List<TimeCap>, firstDay: DayOfWeek, enabled: Boolean, onChange: (List<TimeCap>) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    val res = LocalResources.current
    Column {
        caps.forEachIndexed { i, cap ->
            val title = stringResource(R.string.limit_editor_cap_row, minutesText(cap.minutes), res.windowsText(listOf(cap.window)))
            val days = res.daysText(cap.days).replaceFirstChar { it.uppercase() }
            val edit = stringResource(R.string.limit_editor_cap_edit)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clickable(enabled = enabled, onClickLabel = edit, role = Role.Button) { editing = i },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(days, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onChange(caps.filterIndexed { j, _ -> j != i }) }, enabled = enabled) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.limit_editor_cap_remove))
                }
            }
        }
        if (caps.size < TimeCap.MAX) {
            TextButton(onClick = { editing = caps.size }, enabled = enabled) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.limit_editor_cap_add))
            }
        }
    }
    editing?.let { i ->
        CapDialog(
            initial = caps.getOrNull(i),
            firstDay = firstDay,
            onDismiss = { editing = null },
        ) { cap ->
            editing = null
            onChange(if (i < caps.size) caps.replace(i, cap) else caps + cap)
        }
    }
}

/**
 * One cap: its days, its hours on a clock and its minutes. A new cap ([initial] null) starts with
 * no days and no hours; quick picks fill them in. Only a cap that makes sense saves.
 */
@Composable
private fun CapDialog(initial: TimeCap?, firstDay: DayOfWeek, onDismiss: () -> Unit, onSave: (TimeCap) -> Unit) {
    var days by rememberSaveable { mutableStateOf(initial?.days.orEmpty()) }
    var from by rememberSaveable { mutableStateOf(initial?.from) }
    var to by rememberSaveable { mutableStateOf(initial?.to) }
    var minutes by rememberSaveable { mutableIntStateOf(initial?.minutes ?: 30) }
    var clock by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val start = from
    val end = to
    val problem = when {
        days.isEmpty() -> R.string.limit_editor_cap_pick_days
        start == null || end == null -> R.string.limit_editor_cap_pick_hours
        start >= end -> R.string.schedule_bad_window
        minutes >= end - start -> R.string.limit_editor_cap_too_long
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.limit_editor_cap_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cap_templates), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val weekdays = CapTemplates.weekdays(firstDay)
                    val weekends = CapTemplates.weekends(firstDay)
                    TemplateChip(R.string.cap_template_weekdays, days == weekdays) { days = weekdays }
                    TemplateChip(R.string.cap_template_weekends, days == weekends) { days = weekends }
                    CapTemplates.HOURS.forEach { t ->
                        TemplateChip(t.label, from == t.from && to == t.to) {
                            from = t.from
                            to = t.to
                        }
                    }
                }
                DayChips(days, firstDay, enabled = true) { days = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val startText = start?.let(::clockText)
                    val endText = end?.let { clockText(it % TimeWindow.MINUTES_IN_DAY) }
                    TimeField(
                        startText ?: stringResource(R.string.cap_start),
                        startText?.let { stringResource(R.string.schedule_starts, it) } ?: stringResource(R.string.cap_pick_start),
                        enabled = true,
                    ) { clock = true }
                    Text("–")
                    TimeField(
                        endText ?: stringResource(R.string.cap_end),
                        endText?.let { stringResource(R.string.schedule_ends, it) } ?: stringResource(R.string.cap_pick_end),
                        enabled = true,
                    ) { clock = false }
                }
                Text(stringResource(R.string.limit_editor_cap_minutes), style = MaterialTheme.typography.titleSmall)
                MinutesStepper(minutes, step = 5, min = 5, max = 16 * 60, enabled = true, label = stringResource(R.string.limit_editor_cap_minutes)) { minutes = it }
                problem?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (start != null && end != null) onSave(TimeCap(days, start, end, minutes)) },
                enabled = problem == null,
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    clock?.let { isStart ->
        TimeDialog(initialMinute = (if (isStart) from else to?.rem(TimeWindow.MINUTES_IN_DAY) ?: from?.let { (it + 60) % TimeWindow.MINUTES_IN_DAY }) ?: CapTemplates.CLOCK_START, onDismiss = { clock = null }) { picked ->
            clock = null
            // Midnight as an end means the end of the day.
            if (isStart) from = picked else to = if (picked == 0) TimeWindow.MINUTES_IN_DAY else picked
            true
        }
    }
}

/**
 * A length of time, picked as hours and then minutes in steps of five. Only lengths from [min]
 * to [max] can be picked, and OK gives exactly the length shown.
 */
@Composable
fun DurationDialog(title: String, minutes: Int, min: Int, max: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = DurationChoices.snap(minutes, min, max)
    var hour by rememberSaveable { mutableIntStateOf(start / 60) }
    var minute by rememberSaveable { mutableIntStateOf(start % 60) }
    val picked = hour * 60 + minute
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    minutesText(picked),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.duration_picker_hours), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DurationChoices.hours(min, max).forEach { h ->
                        FilterChip(
                            selected = h == hour,
                            onClick = {
                                hour = h
                                minute = DurationChoices.snap(h * 60 + minute, min, max) - h * 60
                            },
                            label = { Text(stringResource(R.string.duration_hours, h)) },
                        )
                    }
                }
                Text(stringResource(R.string.duration_picker_minutes), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DurationChoices.MINUTES.forEach { m ->
                        FilterChip(
                            selected = m == minute,
                            onClick = { minute = m },
                            enabled = hour * 60 + m in min..max,
                            label = { Text(stringResource(R.string.duration_minutes, m)) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** What [DurationDialog] offers: whole hours, then minutes in steps of five. */
internal object DurationChoices {
    val MINUTES: List<Int> = (0..55 step 5).toList()

    /** The hours with at least one length from [min] to [max]. */
    fun hours(min: Int, max: Int): List<Int> = (0..max / 60).filter { h -> MINUTES.any { h * 60 + it in min..max } }

    /** [minutes] moved onto a five-minute step, and into [min]..[max]. */
    fun snap(minutes: Int, min: Int, max: Int): Int = (Math.round(minutes / 5f) * 5).coerceIn(min, max)
}

@Composable
private fun TemplateChip(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

/** Quick picks for a cap: days that follow the first day of the week, and hours of the day. */
internal object CapTemplates {
    data class Hours(@StringRes val label: Int, val from: Int, val to: Int)

    val HOURS = listOf(
        Hours(R.string.cap_template_mornings, 6 * 60, 12 * 60),
        Hours(R.string.cap_template_afternoons, 12 * 60, 17 * 60),
        Hours(R.string.cap_template_evenings, 17 * 60, 22 * 60),
        Hours(R.string.cap_template_bedtime, 22 * 60, TimeWindow.MINUTES_IN_DAY),
    )

    /** Where the clock opens for a cap with no hours yet. */
    const val CLOCK_START = 9 * 60

    /** The five days from [first], as ISO day numbers in order. */
    fun weekdays(first: DayOfWeek): List<Int> = weekFrom(first).take(5).sorted()

    /** The two days after the weekdays. */
    fun weekends(first: DayOfWeek): List<Int> = weekFrom(first).drop(5).sorted()
}
