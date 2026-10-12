@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.text.format.DateFormat
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.clockText
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Most windows a day can have; matches the backend. */
const val MAX_WINDOWS = 4

/** Common choices, as allowed windows each day. */
private val PRESETS = listOf(
    listOf(TimeWindow(0, 22 * 60)),
    listOf(TimeWindow(10 * 60, 16 * 60)),
    listOf(TimeWindow(9 * 60, TimeWindow.MINUTES_IN_DAY)),
)

/**
 * Edits when an app may be used. The same hours every day, or different ones per weekday; a
 * day with no hours keeps the app resting all day.
 */
@Composable
fun ScheduleEditor(schedule: Schedule, onChange: (Schedule) -> Unit) {
    var perDay by rememberSaveable { mutableStateOf(!schedule.sameEveryDay) }
    var day by rememberSaveable { mutableIntStateOf(1) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !perDay, onClick = {
                perDay = false
                onChange(Schedule.everyDay(*schedule.windowsOn(day).toTypedArray()))
            }, label = { Text(stringResource(R.string.schedule_every_day)) })
            FilterChip(selected = perDay, onClick = { perDay = true }, label = { Text(stringResource(R.string.schedule_per_day)) })
        }
        if (perDay) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    FilterChip(
                        selected = d == day,
                        onClick = { day = d },
                        label = { Text(DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
            WindowsEditor(schedule.windowsOn(day)) { onChange(Schedule(schedule.fullDays() + (day to it))) }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PRESETS.forEach { preset ->
                    FilterChip(
                        selected = schedule.sameEveryDay && schedule.windowsOn(1) == preset,
                        onClick = { onChange(Schedule.everyDay(*preset.toTypedArray())) },
                        label = { Text(presetLabel(preset)) },
                    )
                }
            }
            WindowsEditor(schedule.windowsOn(1)) { onChange(Schedule.everyDay(*it.toTypedArray())) }
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

@Composable
private fun WindowsEditor(windows: List<TimeWindow>, onChange: (List<TimeWindow>) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<Pair<Int, Boolean>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (windows.isEmpty()) {
            Text(stringResource(R.string.schedule_closed), style = MaterialTheme.typography.bodyMedium)
        }
        windows.forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { editing = i to true }) { Text(clockText(w.from)) }
                Text("–")
                TextButton(onClick = { editing = i to false }) { Text(if (w.to == TimeWindow.MINUTES_IN_DAY) clockText(0) else clockText(w.to)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onChange(windows.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.schedule_remove_window))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (windows.size < MAX_WINDOWS) {
                OutlinedButton(onClick = { onChange(Schedule.merge(windows + nextWindow(windows))) }) {
                    Text(stringResource(R.string.schedule_add_window))
                }
            }
            if (windows.isNotEmpty()) {
                TextButton(onClick = { onChange(emptyList()) }) { Text(stringResource(R.string.schedule_close_day)) }
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
            if (from < to && from < TimeWindow.MINUTES_IN_DAY) {
                editing = null
                onChange(Schedule.merge(windows.mapIndexed { j, old -> if (j == i) TimeWindow(from, to) else old }))
                true
            } else {
                false
            }
        }
    }
}

/** A sensible next window: an hour after the last one, or all day when there's none. */
private fun nextWindow(windows: List<TimeWindow>): TimeWindow {
    val last = windows.maxOfOrNull { it.to } ?: return TimeWindow.ALL_DAY
    val from = (last + 60).coerceAtMost(TimeWindow.MINUTES_IN_DAY - 60)
    return TimeWindow(from, (from + 60).coerceAtMost(TimeWindow.MINUTES_IN_DAY))
}

/** [onPick] returns false when the time doesn't fit, which keeps the dialog open. */
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onPick: (Int) -> Boolean) {
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
