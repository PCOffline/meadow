package dev.pcoffline.meadow.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Progress
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.theme.Garden

/**
 * How someone is doing over time. [showWeekChange] is off where the week's comparison is already
 * on screen (Growth's summary). Shows nothing until there's something to show.
 */
@Composable
fun ProgressCard(progress: Progress, modifier: Modifier = Modifier, showWeekChange: Boolean = true) {
    val change = progress.weekChange.takeIf { showWeekChange }
    if (change == null && progress.calmerWeeks == 0 && progress.limitDays == 0) return
    MeadowCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.titleMedium)
            if (change != null) WeekChangePill(change, stringResource(R.string.stats_so_far_week))
            if (progress.calmerWeeks > 0) {
                Text(
                    pluralStringResource(R.plurals.progress_calmer_weeks, progress.calmerWeeks, progress.calmerWeeks),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (progress.limitDays > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.progress_kept, progress.keptDays, progress.limitDays),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    GrowthBar(progress.keptDays / progress.limitDays.toFloat(), height = 6.dp, color = Garden.colors.growth)
                }
            }
        }
    }
}

/** "12% less than [previous]" in green, or "more" in red: less screen time is the good news. */
@Composable
internal fun WeekChangePill(change: Int, previous: String) {
    if (change <= 0) {
        Pill(stringResource(R.string.stats_less_than, -change, previous), Garden.colors.better, textColor = Garden.colors.onBetter)
    } else {
        Pill(
            stringResource(R.string.stats_more_than, change, previous),
            MaterialTheme.colorScheme.errorContainer,
            textColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
