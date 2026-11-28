@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.clockText
import dev.pcoffline.meadow.ui.currentLocale
import java.time.DayOfWeek
import java.time.format.TextStyle

private const val DAY = TimeWindow.MINUTES_IN_DAY

/** The times the lists offer, in minutes since midnight. */
private const val LIST_STEP = 5

/**
 * What Fine-tune edits: hours from [from] to [to] on [days], or a cap of [minutes] (null for hours).
 * [to] past 1440 ends on the next day.
 */
data class FineTune(val days: List<Int>, val from: Int, val to: Int, val minutes: Int? = null)

/**
 * Exact start and end times from a list, the days they apply to, and a cap's amount. The way in
 * for a screen reader, and for times a drag can't reach. [onSave] gets the result; it saves only
 * when the hours end after they start, last a day at most, and are longer than a cap's amount.
 */
@Composable
fun FineTuneSheet(start: FineTune, firstDay: DayOfWeek, onDismiss: () -> Unit, onSave: (FineTune) -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var days by rememberSaveable { mutableStateOf(start.days) }
    var startDay by rememberSaveable { mutableIntStateOf(start.days.firstOrNull() ?: firstDay.value) }
    var from by rememberSaveable { mutableIntStateOf(start.from) }
    var nextDay by rememberSaveable { mutableStateOf(start.to > DAY) }
    var to by rememberSaveable { mutableIntStateOf(start.to % DAY) }
    var minutes by rememberSaveable { mutableStateOf(start.minutes) }
    var keypad by rememberSaveable { mutableStateOf(false) }
    val cap = minutes != null
    val end = if (nextDay) to + DAY else if (to == 0) DAY else to
    val problem = when {
        days.isEmpty() -> R.string.fine_tune_pick_day
        end <= from || end - from > DAY -> R.string.schedule_bad_window
        cap && minutes!! >= end - from -> R.string.limit_editor_cap_too_long
        else -> null
    }
    val locale = currentLocale()
    fun name(day: Int) = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (cap) Icons.Outlined.Timer else Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(if (cap) R.string.limit_editor_cap_title else R.string.fine_tune_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(stringResource(R.string.fine_tune_starts), name(startDay), weekFrom(firstDay).map { it to name(it) }, Modifier.weight(1f)) {
                    days = (days - startDay + it).distinct().sorted()
                    startDay = it
                }
                Picker(stringResource(R.string.fine_tune_at), clockText(from), times(), Modifier.weight(1.2f)) { from = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = nextDay(startDay)
                Picker(
                    stringResource(R.string.fine_tune_ends),
                    name(if (nextDay) next else startDay),
                    listOf(startDay to name(startDay), next to name(next)),
                    Modifier.weight(1f),
                ) { nextDay = it != startDay }
                Picker(stringResource(R.string.fine_tune_at), clockText(to), times(), Modifier.weight(1.2f)) { to = it }
            }
            Text(stringResource(if (cap) R.string.fine_tune_cap_days else R.string.fine_tune_days), style = MaterialTheme.typography.titleSmall)
            DayChips(days, firstDay, enabled = true) { days = it }
            minutes?.let { m ->
                AmountField(stringResource(R.string.limit_editor_cap_minutes), Icons.Outlined.HourglassTop, m, enabled = true) { keypad = true }
            }
            problem?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(FineTune(days, from, end, minutes)) }, enabled = problem == null) { Text(stringResource(R.string.action_ok)) }
            }
        }
    }
    if (keypad) {
        DurationKeypadDialog(
            title = stringResource(R.string.limit_editor_cap_minutes),
            icon = Icons.Outlined.HourglassTop,
            minutes = minutes ?: 30,
            max = DAY - 1,
            tooLong = stringResource(R.string.limit_editor_cap_too_long),
            quickPicks = listOf(15, 30, 45, 60, 90, 120),
            onDismiss = { keypad = false },
        ) {
            minutes = it
            keypad = false
        }
    }
}

/** Each time of the day in steps of [LIST_STEP], as the phone writes it. */
@Composable
private fun times(): List<Pair<Int, String>> = (0 until DAY step LIST_STEP).map { it to clockText(it) }

/** A field that opens a list to pick from. The list opens at the choice now. */
@Composable
private fun Picker(label: String, value: String, options: List<Pair<Int, String>>, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        val chosen = options.indexOfFirst { it.second == value }.coerceAtLeast(0)
        // Each item is 48dp tall: open with the choice now in the middle.
        val scroll = rememberScrollState(initial = with(androidx.compose.ui.platform.LocalDensity.current) { ((chosen - 3).coerceAtLeast(0) * 48).dp.roundToPx() })
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }, scrollState = scroll) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text, style = if (text == value) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onPick(key)
                        open = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
