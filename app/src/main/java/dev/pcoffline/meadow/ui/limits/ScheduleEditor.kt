@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.limits

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.currentLocale
import dev.pcoffline.meadow.ui.windowsText
import java.time.DayOfWeek
import java.time.format.TextStyle

/** Most windows a day can have; matches the backend. */
const val MAX_WINDOWS = 4

/** Common choices, as allowed windows each day. */
private val PRESETS = listOf(
    listOf(TimeWindow(0, 22 * 60)),
    listOf(TimeWindow(10 * 60, 16 * 60)),
    listOf(TimeWindow(9 * 60, TimeWindow.MINUTES_IN_DAY)),
)

/**
 * Edits when an app may be used: the same hours every day, or by weekday, starting on [firstDay].
 * By weekday, the hours are set for the days picked, several at once, and each day's hours are
 * listed below. A day switched off isn't allowed at all. Nothing can be changed while not [enabled].
 */
@Composable
fun ScheduleEditor(schedule: Schedule, firstDay: DayOfWeek = DayOfWeek.MONDAY, enabled: Boolean = true, onChange: (Schedule) -> Unit) {
    val week = (0L until 7L).map { firstDay.plus(it).value }
    // A schedule with no hours at all is shown day by day, where every day reads as not allowed.
    var perDay by rememberSaveable { mutableStateOf(!schedule.sameEveryDay || schedule.windowsOn(1).isEmpty()) }
    val opening = week.map(schedule::windowsOn).firstOrNull { it.isNotEmpty() } ?: PRESETS.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !perDay,
                onClick = {
                    perDay = false
                    if (!schedule.sameEveryDay || schedule.windowsOn(1).isEmpty()) onChange(Schedule.everyDay(*opening.toTypedArray()))
                },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                enabled = enabled,
            ) { Text(stringResource(R.string.schedule_every_day), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            SegmentedButton(selected = perDay, onClick = { perDay = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), enabled = enabled) {
                Text(stringResource(R.string.schedule_per_day), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (perDay) {
            // At first, the first day and every day with the same hours.
            var picked by rememberSaveable { mutableStateOf(week.filter { schedule.windowsOn(it) == schedule.windowsOn(week.first()) }) }
            PickedDaysEditor(schedule, picked, firstDay, opening, enabled, onPick = { picked = it }, onChange = onChange)
            HorizontalDivider()
            week.forEach { day -> DayHours(day, schedule.windowsOn(day), enabled) { picked = listOf(day) } }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEach { preset ->
                    FilterChip(
                        selected = schedule.windowsOn(1) == preset,
                        onClick = { onChange(Schedule.everyDay(*preset.toTypedArray())) },
                        label = { Text(presetLabel(preset)) },
                        enabled = enabled,
                    )
                }
            }
            HoursEditor(schedule.windowsOn(1), enabled) { onChange(Schedule.everyDay(*it.toTypedArray())) }
        }
    }
}

/** Every day spelled out, so changing one day leaves the others as they were. */
private fun Schedule.fullDays(): Map<Int, List<TimeWindow>> = (1..7).associateWith(::windowsOn)

@Composable
private fun presetLabel(preset: List<TimeWindow>): String {
    val w = preset.single()
    return when {
        w.from == 0 -> stringResource(R.string.schedule_preset_until, clockText(w.to))
        w.to == TimeWindow.MINUTES_IN_DAY -> stringResource(R.string.schedule_preset_from, clockText(w.from))
        else -> stringResource(R.string.schedule_window, clockText(w.from), clockText(w.to))
    }
}

/**
 * The hours of the [picked] days, set for all of them at once: a switch for whether the app may be
 * used on them at all, then whether that's all day or set hours. Switching them on gives them
 * [opening], the hours of an open day. Picked days with different hours show the first one's,
 * and a change gives them all the same.
 */
@Composable
private fun PickedDaysEditor(
    schedule: Schedule,
    picked: List<Int>,
    firstDay: DayOfWeek,
    opening: List<TimeWindow>,
    enabled: Boolean,
    onPick: (List<Int>) -> Unit,
    onChange: (Schedule) -> Unit,
) {
    Text(stringResource(R.string.schedule_pick_days), style = MaterialTheme.typography.titleSmall)
    DayChips(picked, firstDay, enabled, onChange = onPick)
    if (picked.isEmpty()) {
        Text(stringResource(R.string.schedule_pick_some), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val windows = schedule.windowsOn(picked.first())
    fun set(new: List<TimeWindow>) = onChange(Schedule(schedule.fullDays() + picked.associateWith { new }))
    if (picked.any { schedule.windowsOn(it) != windows }) {
        Text(stringResource(R.string.schedule_days_differ), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val allowed = stringResource(R.string.schedule_days_allowed)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(windows.isNotEmpty(), enabled = enabled, role = Role.Switch) { on -> set(if (on) opening else emptyList()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(allowed, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = windows.isNotEmpty(), onCheckedChange = null, enabled = enabled)
    }
    if (windows.isEmpty()) return
    val allDay = windows == listOf(TimeWindow.ALL_DAY)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = allDay,
            onClick = { set(if (allDay) opening.takeIf { it != listOf(TimeWindow.ALL_DAY) } ?: PRESETS.first() else listOf(TimeWindow.ALL_DAY)) },
            label = { Text(stringResource(R.string.schedule_all_day_chip)) },
            enabled = enabled,
        )
    }
    if (!allDay) HoursEditor(windows, enabled, ::set)
}

/** One weekday and its hours, at a glance. Tapping it picks that day alone, to set its hours. */
@Composable
private fun DayHours(day: Int, windows: List<TimeWindow>, enabled: Boolean, onPick: () -> Unit) {
    val name = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, currentLocale())
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClickLabel = stringResource(R.string.schedule_pick_day, name), role = Role.Button, onClick = onPick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            LocalResources.current.windowsText(windows).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A day's windows, and a button for one more while there's room. */
@Composable
private fun HoursEditor(windows: List<TimeWindow>, enabled: Boolean, onChange: (List<TimeWindow>) -> Unit) {
    WindowList(windows, enabled, onChange)
    if (windows.size < MAX_WINDOWS) {
        TextButton(onClick = { onChange(Schedule.merge(windows + nextWindow(windows))) }, enabled = enabled) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text(stringResource(R.string.schedule_add_window))
        }
    }
}

/**
 * Allowed windows as "start – end" fields, each opening a clock. A window can be removed while
 * there's another.
 */
@Composable
private fun WindowList(windows: List<TimeWindow>, enabled: Boolean, onChange: (List<TimeWindow>) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<Pair<Int, Boolean>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        windows.forEachIndexed { i, w ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val start = clockText(w.from)
                val end = clockText(w.to % TimeWindow.MINUTES_IN_DAY)
                TimeField(start, stringResource(R.string.schedule_starts, start), enabled) { editing = i to true }
                Text("–")
                TimeField(end, stringResource(R.string.schedule_ends, end), enabled) { editing = i to false }
                if (windows.size > 1) {
                    IconButton(onClick = { onChange(windows.filterIndexed { j, _ -> j != i }) }, enabled = enabled) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.schedule_remove_window))
                    }
                }
            }
        }
    }
    editing?.let { (i, start) ->
        val w = windows.getOrNull(i) ?: return@let
        TimeDialog(
            initialMinute = if (start) w.from else w.to % TimeWindow.MINUTES_IN_DAY,
            onDismiss = { editing = null },
        ) { picked ->
            // Midnight as an end means the end of the day.
            val minute = if (!start && picked == 0) TimeWindow.MINUTES_IN_DAY else picked
            val from = if (start) minute else w.from
            val to = if (start) w.to else minute
            if (from < to) {
                editing = null
                onChange(Schedule.merge(windows.mapIndexed { j, old -> if (j == i) TimeWindow(from, to) else old }))
                true
            } else {
                false
            }
        }
    }
}

/** A time that looks like a field to fill in, and opens a clock. */
@Composable
internal fun TimeField(time: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) { Text(time, style = MaterialTheme.typography.titleSmall) }
}

/** A sensible next window: an hour after the last one, or all day when there's none. */
private fun nextWindow(windows: List<TimeWindow>): TimeWindow {
    val last = windows.maxOfOrNull { it.to } ?: return TimeWindow.ALL_DAY
    val from = (last + 60).coerceAtMost(TimeWindow.MINUTES_IN_DAY - 60)
    return TimeWindow(from, (from + 60).coerceAtMost(TimeWindow.MINUTES_IN_DAY))
}

/** [onPick] returns false when the time doesn't fit, which keeps the dialog open. */
@Composable
internal fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Boolean) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialMinute / 60, initialMinute % 60, DateFormat.is24HourFormat(context))
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state)
                if (invalid) Text(stringResource(R.string.schedule_bad_window), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { invalid = !onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
