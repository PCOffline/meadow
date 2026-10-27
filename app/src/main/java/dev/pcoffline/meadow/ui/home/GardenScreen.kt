package dev.pcoffline.meadow.ui.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.components.SkyScene
import dev.pcoffline.meadow.ui.components.SleepyZs
import dev.pcoffline.meadow.ui.components.rememberResumedNow
import dev.pcoffline.meadow.ui.components.rememberSkyMood
import dev.pcoffline.meadow.ui.currentLocale
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.emergency.EmergencyBanner
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.ticker
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pcoffline.meadow.ui.requests.AskForTimeDialog
import dev.pcoffline.meadow.ui.opensAtText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

data class GardenState(
    val budgets: List<AppBudget> = emptyList(),
    val todayTotalMs: Long = 0L,
    val appsUsed: Int = 0,
    val essentialsReady: Boolean = true,
    /** Android may put Meadow to sleep to save battery. */
    val batteryRestricted: Boolean = false,
    val loaded: Boolean = false,
)

class GardenViewModel : ViewModel() {
    val state: StateFlow<GardenState> = combine(
        AppGraph.store.limits,
        AppGraph.store.grants,
        AppGraph.store.emergency,
        ticker(30_000L),
    ) { _, _, _, _ ->
        val usage = AppGraph.catalog.userFacing(AppGraph.engine.todayUsage())
        val protection = AppGraph.protection.current()
        GardenState(
            // A steady order: sorting by time left would shuffle the rows (and replay their
            // sprouting) every time one app's remaining time passed another's.
            budgets = AppGraph.engine.budgets().sortedBy { it.limit.appName.lowercase() },
            todayTotalMs = usage.values.sum(),
            appsUsed = usage.size,
            essentialsReady = protection.essentialsReady,
            batteryRestricted = !protection.batteryUnrestricted,
            loaded = true,
        )
    }.flowOn(Dispatchers.IO).stateWhileVisible(this, GardenState())
}

@Composable
fun GardenScreen(
    profile: UserProfile,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSetup: () -> Unit,
    onPlantLimit: () -> Unit,
    onOpenLimit: (String) -> Unit,
    onOpenTroubleshoot: () -> Unit = {},
    viewModel: GardenViewModel = viewModel { GardenViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var askFor by rememberSaveable { mutableStateOf<String?>(null) }
    GardenContent(
        profile, state, onOpenSettings, onOpenProfile, onOpenSetup, onPlantLimit, onOpenLimit,
        onOpenTroubleshoot = onOpenTroubleshoot,
        onAsk = { askFor = it },
    )
    askFor?.let { AskForTimeDialog(packageName = it, onDismiss = { askFor = null }) }
}

/** The garden itself, drawn from plain state (also used for previews and screenshots). */
@Composable
fun GardenContent(
    profile: UserProfile,
    state: GardenState,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onPlantLimit: () -> Unit = {},
    onOpenLimit: (String) -> Unit = {},
    onOpenTroubleshoot: () -> Unit = {},
    onAsk: (String) -> Unit = {},
) {
    val canAsk = profile.approverIds.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        // Room at the bottom so the last flowers can scroll clear of the Plant button.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                // One item, so no gap is left when there's no emergency unlock to show.
                Column {
                    GardenHeader(profile, state, onOpenSettings, onOpenProfile)
                    EmergencyBanner(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
                }
            }

            if (!state.essentialsReady) {
                item {
                    Notice(
                        emoji = "🍂",
                        text = stringResource(R.string.garden_notice_setup),
                        action = stringResource(R.string.garden_notice_setup_action),
                        onClick = onOpenSetup,
                    )
                }
            }
            if (state.essentialsReady && state.batteryRestricted) {
                item {
                    Notice(
                        emoji = "🔋",
                        text = stringResource(R.string.garden_notice_battery),
                        action = stringResource(R.string.trouble_button),
                        onClick = onOpenTroubleshoot,
                    )
                }
            }

            item { SectionTitle(stringResource(R.string.garden_title), Modifier.padding(horizontal = 20.dp)) }

            if (state.loaded && state.budgets.isEmpty()) {
                item {
                    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = onPlantLimit) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BloomFlower(0.2f, Garden.colors.petalPink, Modifier.size(56.dp, 84.dp), sway = false)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.garden_empty_title), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            items(state.budgets.chunked(2), key = { row -> row.joinToString { it.limit.packageName } }) { row ->
                Row(Modifier.animateItem().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { budget ->
                        FlowerTile(
                            budget,
                            Modifier.weight(1f),
                            onAsk = if (canAsk && !budget.emergency) ({ onAsk(budget.limit.packageName) }) else null,
                        ) { onOpenLimit(budget.limit.packageName) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        // Planting a limit is what the garden is for, so it's the one big button, always in reach.
        ExtendedFloatingActionButton(
            onClick = onPlantLimit,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.garden_plant)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun GardenHeader(profile: UserProfile, state: GardenState, onOpenSettings: () -> Unit, onOpenProfile: () -> Unit) {
    val mood = rememberSkyMood()
    val minutes by animateIntAsState((state.todayTotalMs / 60_000L).toInt(), tween(1100), label = "todayMinutes")
    val now = rememberResumedNow()
    val locale = currentLocale()
    val today = remember(now.toLocalDate(), locale) { now.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) }
    Box(Modifier.fillMaxWidth().height(250.dp)) {
        // The sun or moon sits low on the right, beside today's total, so it never crowds your
        // picture and Settings up top.
        SkyScene(mood, Modifier.fillMaxSize(), celestial = Offset(0.8f, 0.56f))
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today, style = MaterialTheme.typography.labelLarge, color = mood.onSky)
                    Text(
                        // Someone who uses a photo goes by it, not by a flower next to their name.
                        if (profile.avatar.photoUrl != null) {
                            stringResource(R.string.garden_greeting_plain, stringResource(mood.greeting), profile.displayName)
                        } else {
                            stringResource(R.string.garden_greeting, stringResource(mood.greeting), profile.displayName, profile.avatar.emoji)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.asHeading(),
                        color = mood.onSky,
                    )
                }
                IconButton(onClick = onOpenProfile) {
                    PersonAvatar(profile.avatar, size = 34.dp, contentDescription = stringResource(R.string.profile_open))
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.action_settings), tint = mood.onSky)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.garden_screen_time_today), style = MaterialTheme.typography.labelLarge, color = mood.onSky)
            Text(
                minutesText(minutes),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp),
                fontWeight = FontWeight.SemiBold,
                color = mood.onSky,
            )
            Text(
                pluralStringResource(R.plurals.garden_across_apps, state.appsUsed, state.appsUsed),
                style = MaterialTheme.typography.bodyMedium,
                color = mood.onSky,
            )
        }
    }
}

@Composable
private fun FlowerTile(budget: AppBudget, modifier: Modifier, onAsk: (() -> Unit)?, onClick: () -> Unit) {
    val garden = Garden.colors
    val color = remember(budget.limit.packageName) {
        garden.petals[(budget.limit.packageName.hashCode() and 0x7fffffff) % garden.petals.size]
    }
    MeadowCard(modifier, onClick = onClick) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BloomFlower(1f - budget.usedFraction, color, Modifier.size(90.dp, 120.dp))
            AppIcon(budget.limit.packageName, Modifier.align(Alignment.TopStart), size = 28.dp)
            if (budget.exhausted) SleepyZs(Modifier.align(Alignment.TopEnd))
        }
        Text(
            budget.limit.appName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            when {
                budget.emergency -> stringResource(R.string.garden_tile_emergency)
                budget.restingByHours -> budget.untilScheduleChangeMs?.takeIf { it < Long.MAX_VALUE / 4 }
                    ?.let { stringResource(R.string.garden_tile_resting_until, opensAtText(it)) }
                    ?: stringResource(R.string.garden_tile_resting_hours)
                budget.exhausted && budget.weeklyIsTighter -> stringResource(R.string.garden_tile_resting_week)
                budget.exhausted -> stringResource(R.string.garden_tile_resting_today)
                // Only hours limit it: when it closes says more than a countdown of hours.
                budget.allowedTodayMs == null && budget.untilScheduleChangeMs != null ->
                    stringResource(R.string.garden_tile_open_until, opensAtText(budget.untilScheduleChangeMs))
                budget.weeklyIsTighter -> stringResource(R.string.garden_tile_left_week, durationText(budget.remainingMs))
                else -> stringResource(R.string.garden_tile_left_today, durationText(budget.remainingMs))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (budget.exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        if (budget.bonusTodayMinutes > 0) {
            Text(
                stringResource(R.string.garden_tile_extra, minutesText(budget.bonusTodayMinutes)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        if (onAsk != null) {
            TextButton(onClick = onAsk, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.garden_tile_ask), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Notice(emoji: String, text: String, action: String, onClick: () -> Unit) {
    MeadowCard(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        tint = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                TextButton(onClick = onClick, modifier = Modifier.padding(top = 2.dp)) { Text(action) }
            }
        }
    }
}
