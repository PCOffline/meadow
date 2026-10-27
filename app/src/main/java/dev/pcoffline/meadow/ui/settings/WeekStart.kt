@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.WeekRule
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.currentLocale
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Saturday, Sunday or Monday, side by side. */
@Composable
internal fun WeekStartPicker(selected: DayOfWeek, modifier: Modifier = Modifier, onSelect: (DayOfWeek) -> Unit) {
    val locale = currentLocale()
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        WeekRule.CHOICES.forEachIndexed { i, day ->
            SegmentedButton(
                selected = day == selected,
                onClick = { onSelect(day) },
                shape = SegmentedButtonDefaults.itemShape(i, WeekRule.CHOICES.size),
            ) { Text(day.getDisplayName(TextStyle.SHORT, locale), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** The day weeks start on, for Growth and weekly limits. */
@Composable
internal fun WeekStartCard() {
    val rule by AppGraph.store.weekRule.collectAsStateWithLifecycle()
    val locale = currentLocale()
    MeadowCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_week_start), style = MaterialTheme.typography.titleMedium)
            WeekStartPicker(rule.first) { AppGraph.store.setWeekStart(it) }
            // Only while a change is waiting for the current week to end: otherwise a weekly limit
            // would seem to ignore the choice just made.
            rule.pendingOn(Days.today())?.let { from ->
                val date = from.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale))
                Text(
                    stringResource(R.string.settings_week_start_pending, date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
