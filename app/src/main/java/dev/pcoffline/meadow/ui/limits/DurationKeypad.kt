@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.theme.Garden

/** One more choice under the keypad, such as "No limit" or "Reset to 30m". */
@Immutable
data class KeypadChoice(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * A field that shows an amount of time and opens the keypad: "Daily limit 30m". [minutes] null
 * reads "Off". [was] shows the amount before a change, struck through: "30m → 45m".
 */
@Composable
fun AmountField(
    label: String,
    icon: ImageVector,
    minutes: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    was: Int? = null,
    onClick: () -> Unit,
) {
    val value = minutes?.let { minutesText(it) } ?: stringResource(R.string.week_off)
    val before = was?.let { minutesText(it) }
    val colors = MaterialTheme.colorScheme
    val change = stringResource(R.string.limit_editor_pick_time)
    Box(modifier.padding(top = 8.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.outline),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                .combinedClickable(enabled = enabled, role = Role.Button, onClickLabel = change, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(label, before?.let { "$it →" }, value).joinToString(" ")
                    role = Role.Button
                    if (enabled) onClick(change) { onClick(); true }
                },
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (before == null) Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(
                    buildAnnotatedString {
                        if (before != null) {
                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colors.onSurfaceVariant, fontSize = 14.sp)) { append(before) }
                            append(" → ")
                        }
                        append(value)
                    },
                    style = if (minutes == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    color = if (minutes == null) colors.onSurfaceVariant else colors.primary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = if (before == null) 8.dp else 0.dp),
                )
                if (enabled) Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            // On the card's own colour, so it cuts the field's edge as a text field's label does.
            modifier = Modifier.padding(start = 10.dp).offset(y = (-8).dp).background(Garden.colors.cardTint).padding(horizontal = 4.dp)
                .clearAndSetSemantics { },
        )
    }
}

/**
 * An amount of time typed on a keypad, as in the Google Clock timer, with [quickPicks] that fill
 * it in. Any whole minute from 1 to [max] saves. [choices] go under the keys: "No limit",
 * "Reset". [tooLong] says the most it can be.
 */
@Composable
fun DurationKeypadDialog(
    title: String,
    icon: ImageVector,
    minutes: Int,
    max: Int,
    tooLong: String,
    quickPicks: List<Int>,
    onDismiss: () -> Unit,
    choices: List<KeypadChoice> = emptyList(),
    onPick: (Int) -> Unit,
) {
    var entry by rememberSaveable(stateSaver = EntrySaver) { mutableStateOf(DurationEntry.of(minutes)) }
    val picked = entry.minutes
    val problem = when {
        picked < 1 -> stringResource(R.string.keypad_at_least)
        picked > max -> tooLong
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KeypadDisplay(entry)
                HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    quickPicks.forEach { m ->
                        FilterChip(selected = picked == m, onClick = { entry = DurationEntry.of(m) }, label = { Text(minutesText(m)) })
                    }
                }
                Keys(onKey = { entry = entry.type(it) }, onBack = { entry = entry.back() }, onClear = { entry = DurationEntry("") })
                if (choices.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        choices.forEach { c ->
                            AssistChip(onClick = c.onClick, label = { Text(c.label) }, leadingIcon = { Icon(c.icon, contentDescription = null, Modifier.size(18.dp)) })
                        }
                    }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked) }, enabled = problem == null) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "01h 30m": typed digits in the primary colour, the zeros before them faint. Read out as "1 hour 30 minutes". */
@Composable
private fun KeypadDisplay(entry: DurationEntry) {
    val colors = MaterialTheme.colorScheme
    val faint = colors.onSurfaceVariant.copy(alpha = 0.55f)
    val digits = entry.hoursText + entry.minutesText
    val spoken = minutesText(entry.minutes)
    Text(
        buildAnnotatedString {
            digits.forEachIndexed { i, c ->
                withStyle(SpanStyle(color = if (i >= 4 - entry.typed) colors.primary else faint)) { append(c) }
                if (i == 1) withStyle(SpanStyle(fontSize = 20.sp, color = if (entry.typed >= 3) colors.primary else faint)) { append("h ") }
                if (i == 3) withStyle(SpanStyle(fontSize = 20.sp, color = colors.primary)) { append("m") }
            }
        },
        style = MaterialTheme.typography.displaySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/** 1 to 9, 00, 0 and delete. A long press on delete clears all. */
@Composable
private fun Keys(onKey: (String) -> Unit, onBack: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", null))
    val delete = stringResource(R.string.keypad_delete)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val shape = RoundedCornerShape(24.dp)
                    val tint = if (key == null || key == "00") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    Surface(
                        shape = shape,
                        color = tint,
                        modifier = Modifier.weight(1f).height(48.dp).clip(shape).combinedClickable(
                            role = Role.Button,
                            onClickLabel = key ?: delete,
                            onLongClick = if (key == null) onClear else null,
                            onClick = { if (key == null) onBack() else onKey(key) },
                        ).semantics { if (key == null) contentDescription = delete },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == null) {
                                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null)
                            } else {
                                Text(key, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val EntrySaver = androidx.compose.runtime.saveable.Saver<DurationEntry, List<Any>>(
    save = { listOf(it.digits, it.fresh) },
    restore = { DurationEntry(it[0] as String, it[1] as Boolean) },
)
