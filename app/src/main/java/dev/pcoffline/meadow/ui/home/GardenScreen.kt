@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.data.formatDuration
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.SkyScene
import dev.pcoffline.meadow.ui.components.SleepyZs
import dev.pcoffline.meadow.ui.components.rememberSkyMood
import dev.pcoffline.meadow.data.formatMinutes
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.emergency.EmergencyBanner
import dev.pcoffline.meadow.ui.switchTab
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

private data class GardenState(
    val budgets: List<AppBudget> = emptyList(),
    val todayTotalMs: Long = 0L,
    val appsUsed: Int = 0,
    val protection: ProtectionState? = null,
    val loaded: Boolean = false,
)

@Composable
fun GardenScreen(profile: UserProfile, nav: NavController) {
    val limits by AppGraph.store.limits.collectAsStateWithLifecycle()
    val grants by AppGraph.store.grants.collectAsStateWithLifecycle()
    val emergency by AppGraph.store.emergency.collectAsStateWithLifecycle()
    val state by produceState(GardenState(), limits, grants, emergency) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                val usage = AppGraph.catalog.userFacing(AppGraph.engine.todayUsage())
                GardenState(
                    budgets = AppGraph.engine.budgets().sortedBy { it.remainingMs },
                    todayTotalMs = usage.values.sum(),
                    appsUsed = usage.size,
                    protection = AppGraph.protection.current(),
                    loaded = true,
                )
            }
            delay(30_000L)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { GardenHeader(profile, state, onSettings = { nav.navigate("settings") }) }
        item { EmergencyBanner(Modifier.padding(horizontal = 20.dp)) }

        if (state.protection?.essentialsReady == false) {
            item {
                Notice(
                    emoji = "🍂",
                    text = "Meadow can't guard your limits until usage access and the garden keeper are on.",
                    action = "Finish setup",
                ) { nav.navigate("setup") }
            }
        }
        if (profile.approverIds.isEmpty()) {
            item {
                Notice(
                    emoji = "💌",
                    text = "Your limits aren't locked in yet. Invite a friend as your Petal Pal — after that, " +
                        "only they can give you more time.",
                    action = "Invite a Petal Pal",
                ) { nav.switchTab("circle") }
            }
        }

        item {
            SectionTitle("Your garden", Modifier.padding(horizontal = 20.dp)) {
                TextButton(onClick = { nav.navigate("pick-app") }) { Text("+ Plant a limit") }
            }
        }

        if (state.loaded && state.budgets.isEmpty()) {
            item {
                MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = { nav.navigate("pick-app") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BloomFlower(0.2f, Garden.colors.petalPink, Modifier.size(56.dp, 84.dp), sway = false)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Plant your first limit", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Pick an app and choose how long it may bloom each day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        items(state.budgets.chunked(2), key = { row -> row.joinToString { it.limit.packageName } }) { row ->
            Row(Modifier.animateItem().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { budget ->
                    FlowerTile(budget, Modifier.weight(1f)) { nav.navigate("limit/${budget.limit.packageName}") }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GardenHeader(profile: UserProfile, state: GardenState, onSettings: () -> Unit) {
    val mood = rememberSkyMood()
    val minutes by animateIntAsState((state.todayTotalMs / 60_000L).toInt(), tween(1100), label = "todayMinutes")
    Box(Modifier.fillMaxWidth().height(250.dp)) {
        SkyScene(mood, Modifier.fillMaxSize())
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Days.today().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        style = MaterialTheme.typography.labelLarge,
                        color = mood.onSky.copy(alpha = 0.75f),
                    )
                    Text("${mood.greeting}, ${profile.displayName} ${profile.flower}", style = MaterialTheme.typography.headlineSmall, color = mood.onSky)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = mood.onSky) }
            }
            Spacer(Modifier.height(18.dp))
            Text("Screen time today", style = MaterialTheme.typography.labelLarge, color = mood.onSky.copy(alpha = 0.75f))
            Text(
                formatMinutes(minutes),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp),
                fontWeight = FontWeight.SemiBold,
                color = mood.onSky,
            )
            Text(
                if (state.appsUsed == 1) "across 1 app" else "across ${state.appsUsed} apps",
                style = MaterialTheme.typography.bodyMedium,
                color = mood.onSky.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun FlowerTile(budget: AppBudget, modifier: Modifier, onClick: () -> Unit) {
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
        val period = if (budget.weeklyIsTighter) "this week" else "today"
        Text(
            if (budget.emergency) "Paused for an emergency 🚨"
            else if (budget.exhausted) "Resting until ${if (budget.weeklyIsTighter) "next week" else "tomorrow"} 🌙"
            else "${formatDuration(budget.remainingMs)} left $period",
            style = MaterialTheme.typography.bodySmall,
            color = if (budget.exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        if (budget.bonusTodayMinutes > 0) {
            Text(
                "+${budget.bonusTodayMinutes}m gifted today 🌸",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
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
