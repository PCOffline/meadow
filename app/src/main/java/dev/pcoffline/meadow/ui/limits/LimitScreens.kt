@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.data.formatDuration
import dev.pcoffline.meadow.data.formatMinutes
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.usage.InstalledApp
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

private data class PickerRow(val app: InstalledApp, val usedTodayMs: Long, val limited: Boolean)

@Composable
fun AppPickerScreen(nav: NavController) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows by produceState<List<PickerRow>?>(null) {
        value = withContext(Dispatchers.IO) {
            val usage = AppGraph.engine.todayUsage()
            val limited = AppGraph.store.limits.value.map { it.packageName }.toSet()
            AppGraph.catalog.launchableApps()
                .map { PickerRow(it, usage[it.packageName] ?: 0L, it.packageName in limited) }
                .sortedByDescending { it.usedTodayMs }
        }
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Choose an app", onBack = { nav.popBackStack() })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val list = rows
        if (list == null) Loading() else LazyColumn(Modifier.fillMaxSize().imePadding()) {
            val filtered = list.filter { query.isBlank() || it.app.label.contains(query, ignoreCase = true) }
            items(filtered, key = { it.app.packageName }) { row ->
                ListItem(
                    headlineContent = { Text(row.app.label) },
                    supportingContent = {
                        Text(if (row.usedTodayMs > 0) "${formatDuration(row.usedTodayMs)} today" else "Not opened today")
                    },
                    leadingContent = { AppIcon(row.app.packageName) },
                    trailingContent = { if (row.limited) Pill("Planted", MaterialTheme.colorScheme.primaryContainer) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 8.dp)
                        .clickable { nav.navigate("limit/${row.app.packageName}") },
                )
            }
        }
    }
}

@Composable
fun LimitEditorScreen(packageName: String, nav: NavController) {
    val limits by AppGraph.store.limits.collectAsStateWithLifecycle()
    val approverCount by AppGraph.store.approverCount.collectAsStateWithLifecycle()
    val existing = limits.firstOrNull { it.packageName == packageName }
    val appName = remember(packageName) { existing?.appName ?: AppGraph.catalog.label(packageName) }
    val budget by produceState<AppBudget?>(null, existing) {
        value = withContext(Dispatchers.IO) { AppGraph.engine.budget(packageName) }
    }

    var daily by rememberSaveable(existing) { mutableIntStateOf(existing?.dailyMinutes ?: 30) }
    var weeklyOn by rememberSaveable(existing) { mutableStateOf((existing?.weeklyMinutes ?: 0) > 0) }
    var weekly by rememberSaveable(existing) { mutableIntStateOf(existing?.weeklyMinutes?.takeIf { it > 0 } ?: 180) }
    var dailyOn by rememberSaveable(existing) { mutableStateOf(existing == null || existing.dailyMinutes > 0) }
    var message by rememberSaveable { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    val action = rememberAction()
    val celebrate = LocalCelebrate.current

    val proposed = AppLimit(packageName, appName, if (dailyOn) daily else 0, if (weeklyOn) weekly else 0)
    val needsApproval = existing != null && approverCount > 0 && LimitsRepository.isLooser(existing, proposed)

    Column(Modifier.fillMaxSize()) {
        BackTopBar(if (existing == null) "Plant a limit" else "Tend this limit", onBack = { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(appName, style = MaterialTheme.typography.headlineSmall)
                    budget?.let {
                        Text(
                            "${formatDuration(it.usedTodayMs)} used today · ${formatDuration(it.usedWeekMs)} this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BloomFlower(1f - (budget?.usedFraction ?: 0f), Garden.colors.petalPink, Modifier.size(48.dp, 72.dp))
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                ToggleRow("Daily limit", dailyOn) { dailyOn = it }
                if (dailyOn) {
                    MinutesStepper(daily, step = 5, min = 5, max = 16 * 60) { daily = it }
                    PresetChips(listOf(15, 30, 45, 60, 90, 120), daily) { daily = it }
                }
            }
            MeadowCard(Modifier.fillMaxWidth()) {
                ToggleRow("Weekly limit", weeklyOn) { weeklyOn = it }
                if (weeklyOn) {
                    MinutesStepper(weekly, step = 30, min = 30, max = 100 * 60) { weekly = it }
                    PresetChips(listOf(120, 180, 300, 420, 600), weekly) { weekly = it }
                }
                Text(
                    "Weeks start on Monday. Whichever limit runs out first wins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (needsApproval) {
                MeadowCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("💌 This loosens your limit", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your Petal Pals will be asked first. The new limit starts once one of them says yes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = message, onValueChange = { message = it.take(280) },
                        label = { Text("A note for them (optional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            } else if (existing == null && approverCount > 0) {
                Text(
                    "Once planted, loosening or removing this limit needs a yes from a Petal Pal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ErrorNote(action.error)
            Button(
                onClick = {
                    action.run {
                        val outcome = AppGraph.limits.save(proposed, message)
                        if (outcome == ChangeOutcome.APPLIED) celebrate()
                        action.toast(
                            if (outcome == ChangeOutcome.REQUESTED) "Request sent to your Petal Pals 💌"
                            else if (existing == null) "Planted 🌱" else "Limit updated 🌿",
                        )
                        nav.popBackStack()
                    }
                },
                enabled = !action.busy && (dailyOn || weeklyOn) && proposed != existing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    when {
                        needsApproval -> "Ask my Petal Pals"
                        existing == null -> "Plant this limit"
                        else -> "Save"
                    },
                )
            }
            if (existing != null) {
                OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (approverCount > 0) "Ask to remove this limit" else "Remove this limit")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRemove && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Uproot ${existing.appName}'s limit?") },
            text = {
                Text(
                    if (approverCount > 0) "Your Petal Pals will be asked first. The limit stays until one of them agrees."
                    else "This removes the limit straight away.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    action.run {
                        val outcome = AppGraph.limits.remove(existing, message)
                        action.toast(if (outcome == ChangeOutcome.REQUESTED) "Request sent 💌" else "Limit removed")
                        nav.popBackStack()
                    }
                }) { Text(if (approverCount > 0) "Ask" else "Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MinutesStepper(value: Int, step: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) {
            Icon(Icons.Outlined.Remove, contentDescription = "Less")
        }
        Text(
            formatMinutes(value),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        FilledTonalIconButton(onClick = { onChange((value + step).coerceAtMost(max)) }) {
            Icon(Icons.Outlined.Add, contentDescription = "More")
        }
    }
}

@Composable
private fun PresetChips(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = { Text(formatMinutes(minutes)) },
            )
        }
    }
}
