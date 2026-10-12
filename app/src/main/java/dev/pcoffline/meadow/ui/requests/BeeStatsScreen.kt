package dev.pcoffline.meadow.ui.requests

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.BeeStats
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.ShareDialog
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class BeeStatsViewModel(uid: String) : ViewModel() {
    /** Worked out from this Bumblebee's own copies of the requests; null while loading. */
    val stats: StateFlow<BeeStats?> = AppGraph.requests.incoming(uid, limit = 500)
        .map { BeeStats.from(it) }
        .stateWhileVisible(this, null)
}

@Composable
fun BeeStatsScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    viewModel: BeeStatsViewModel = viewModel(key = "bee-stats-${profile.uid}") { BeeStatsViewModel(profile.uid) },
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    BeeStatsContent(stats, onBack)
}

/** A Bumblebee's own answering habits, drawn from plain state (also used for tests). */
@Composable
fun BeeStatsContent(stats: BeeStats?, onBack: () -> Unit = {}) {
    var sharing by rememberSaveable { mutableStateOf(false) }
    val res = LocalContext.current.resources
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.bee_stats_title), onBack = onBack, actions = {
            if (stats != null && stats.answered > 0) {
                IconButton(onClick = { sharing = true }) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share_title))
                }
            }
        })
        when {
            stats == null -> Loading()
            stats.asked == 0 -> EmptyState("🐝", stringResource(R.string.bee_stats_empty_title), stringResource(R.string.bee_stats_empty_body))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.bee_stats_private),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MeadowCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bee_stats_leniency), style = MaterialTheme.typography.titleMedium)
                    val leaning = stats.leaning
                    if (stats.leniency == null || leaning == null) {
                        Text(
                            stringResource(R.string.bee_stats_leniency_unknown, BeeStats.MIN_ANSWERS),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(leaningText(res, leaning), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(R.string.bee_stats_leniency_score, stats.leniency),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        GrowthBar(stats.leniency / 100f, color = Garden.colors.leaf, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            stringResource(R.string.bee_stats_leniency_how),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                MeadowCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.bee_stats_overall), style = MaterialTheme.typography.titleMedium)
                    StatLine(stringResource(R.string.bee_stats_asked), stats.asked.toString())
                    StatLine(stringResource(R.string.bee_stats_yes), stats.yes.toString())
                    StatLine(stringResource(R.string.bee_stats_no), stats.no.toString())
                    StatLine(stringResource(R.string.bee_stats_rate), percent(stats.approvalRate))
                    StatLine(stringResource(R.string.bee_stats_minutes), minutesText(stats.minutesGranted))
                    if (stats.missed > 0) StatLine(stringResource(R.string.bee_stats_missed), stats.missed.toString())
                    if (stats.waiting > 0) StatLine(stringResource(R.string.bee_stats_waiting), stats.waiting.toString())
                }
                Text(stringResource(R.string.bee_stats_by_person), style = MaterialTheme.typography.titleMedium)
                stats.people.forEach { person ->
                    MeadowCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlowerAvatar(person.flower, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(person.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.bee_stats_person_line, person.asked, person.yes, person.no),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(percent(person.approvalRate), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (sharing && stats != null) ShareDialog(shareText(res, stats), onDismiss = { sharing = false })
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun percent(rate: Float?): String =
    rate?.let { stringResource(R.string.bee_stats_percent, (it * 100).roundToInt()) } ?: stringResource(R.string.bee_stats_none)

private fun leaningText(res: Resources, leaning: BeeStats.Leaning): String = res.getString(
    when (leaning) {
        BeeStats.Leaning.VERY_STRICT -> R.string.bee_leaning_very_strict
        BeeStats.Leaning.STRICT -> R.string.bee_leaning_strict
        BeeStats.Leaning.BALANCED -> R.string.bee_leaning_balanced
        BeeStats.Leaning.GENTLE -> R.string.bee_leaning_gentle
        BeeStats.Leaning.VERY_GENTLE -> R.string.bee_leaning_very_gentle
    },
)

/** Only totals: who asked what stays out of anything shared. */
private fun shareText(res: Resources, stats: BeeStats): String {
    val rate = stats.approvalRate?.let { (it * 100).roundToInt() } ?: 0
    val leaning = stats.leaning?.let { leaningText(res, it) }
    return if (leaning != null) res.getString(R.string.bee_stats_share_with_leaning, stats.answered, rate, leaning)
    else res.getString(R.string.bee_stats_share, stats.answered, rate)
}
