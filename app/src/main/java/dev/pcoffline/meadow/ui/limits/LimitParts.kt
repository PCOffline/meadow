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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
            initial = caps.getOrNull(i) ?: TimeCap((1..5).toList(), 10 * 60, 16 * 60, 30),
            firstDay = firstDay,
            onDismiss = { editing = null },
        ) { cap ->
            editing = null
            onChange(if (i < caps.size) caps.replace(i, cap) else caps + cap)
        }
    }
}

/** One cap: its days, its hours on a clock and its minutes. Only a cap that makes sense saves. */
@Composable
private fun CapDialog(initial: TimeCap, firstDay: DayOfWeek, onDismiss: () -> Unit, onSave: (TimeCap) -> Unit) {
    var days by rememberSaveable { mutableStateOf(initial.days) }
    var from by rememberSaveable { mutableIntStateOf(initial.from) }
    var to by rememberSaveable { mutableIntStateOf(initial.to) }
    var minutes by rememberSaveable { mutableIntStateOf(initial.minutes) }
    var clock by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val problem = when {
        days.isEmpty() -> R.string.limit_editor_cap_pick_days
        from >= to -> R.string.schedule_bad_window
        minutes >= to - from -> R.string.limit_editor_cap_too_long
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.limit_editor_cap_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DayChips(days, firstDay, enabled = true) { days = it }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val start = clockText(from)
                    val end = clockText(to % TimeWindow.MINUTES_IN_DAY)
                    TimeField(start, stringResource(R.string.schedule_starts, start), enabled = true) { clock = true }
                    Text("–")
                    TimeField(end, stringResource(R.string.schedule_ends, end), enabled = true) { clock = false }
                }
                Text(stringResource(R.string.limit_editor_cap_minutes), style = MaterialTheme.typography.titleSmall)
                MinutesStepper(minutes, step = 5, min = 5, max = 16 * 60, enabled = true, label = stringResource(R.string.limit_editor_cap_minutes)) { minutes = it }
                problem?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(TimeCap(days, from, to, minutes)) }, enabled = problem == null) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    clock?.let { start ->
        TimeDialog(initialMinute = if (start) from else to % TimeWindow.MINUTES_IN_DAY, onDismiss = { clock = null }) { picked ->
            clock = null
            // Midnight as an end means the end of the day.
            if (start) from = picked else to = if (picked == 0) TimeWindow.MINUTES_IN_DAY else picked
            true
        }
    }
}

/**
 * A length of time on a clock face: the hour hand counts hours, the minute hand minutes. Kept
 * between [min] and [max].
 */
@Composable
fun DurationDialog(title: String, minutes: Int, min: Int, max: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
    val picked = state.hour * 60 + state.minute
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePicker(state)
                Text(minutesText(picked.coerceIn(min, max)), style = MaterialTheme.typography.titleMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked.coerceIn(min, max)) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
