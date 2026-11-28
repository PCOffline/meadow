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
import androidx.compose.runtime.remember
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
import dev.pcoffline.meadow.ui.capHoursText
import dev.pcoffline.meadow.ui.daysText
import dev.pcoffline.meadow.ui.minutesText
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.format.TextStyle

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
