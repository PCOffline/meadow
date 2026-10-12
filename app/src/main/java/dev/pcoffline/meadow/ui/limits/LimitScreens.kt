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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading()) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

data class PickerRow(val packageName: String, val label: String, val usedTodayMs: Long, val limited: Boolean)

class AppPickerViewModel : ViewModel() {
    private val apps = flow {
        val usage = AppGraph.engine.todayUsage()
        emit(AppGraph.catalog.launchableApps().map { it to (usage[it.packageName] ?: 0L) })
    }.flowOn(Dispatchers.IO)

    /** Launchable apps, most used today first; null while loading. */
    val rows: StateFlow<List<PickerRow>?> = combine(apps, AppGraph.store.limits) { list, limits ->
        val limited = limits.map { it.packageName }.toSet()
        list.map { (app, used) -> PickerRow(app.packageName, app.label, used, app.packageName in limited) }
            .sortedByDescending { it.usedTodayMs }
    }.stateWhileVisible(this, null)
}

@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    viewModel: AppPickerViewModel = viewModel { AppPickerViewModel() },
) {
    var query by rememberSaveable { mutableStateOf("") }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.picker_title), onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.picker_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        )
        val list = rows
        if (list == null) {
            Loading()
        } else {
            val filtered = remember(list, query) { list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) } }
            LazyColumn(Modifier.fillMaxSize().imePadding()) {
                items(filtered, key = { it.packageName }) { row ->
                    ListItem(
                        headlineContent = { Text(row.label) },
                        supportingContent = {
                            Text(
                                if (row.usedTodayMs > 0) stringResource(R.string.picker_used_today, durationText(row.usedTodayMs))
                                else stringResource(R.string.picker_not_opened),
                            )
                        },
                        leadingContent = { AppIcon(row.packageName) },
                        trailingContent = {
                            if (row.limited) Pill(stringResource(R.string.picker_planted), MaterialTheme.colorScheme.primaryContainer)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.animateItem().clickable { onPick(row.packageName) }.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

data class LimitEditorState(
    val existing: AppLimit? = null,
    val appName: String = "",
    val budget: AppBudget? = null,
    val bumblebeeCount: Int = 0,
    val loaded: Boolean = false,
)

class LimitEditorViewModel(private val packageName: String) : ViewModel() {
    val state: StateFlow<LimitEditorState> = combine(
        AppGraph.store.limits,
        AppGraph.store.approverCount,
    ) { limits, bees ->
        val existing = limits.firstOrNull { it.packageName == packageName }
        LimitEditorState(
            existing = existing,
            appName = existing?.appName ?: AppGraph.catalog.label(packageName),
            budget = AppGraph.engine.budget(packageName),
            bumblebeeCount = bees,
            loaded = true,
        )
    }.flowOn(Dispatchers.IO).stateWhileVisible(this, LimitEditorState())
}

@Composable
fun LimitEditorScreen(
    packageName: String,
    onDone: () -> Unit,
    viewModel: LimitEditorViewModel = viewModel(key = "limit-$packageName") { LimitEditorViewModel(packageName) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            stringResource(if (state.existing == null) R.string.limit_editor_new else R.string.limit_editor_existing),
            onBack = onDone,
        )
        if (state.loaded) LimitEditor(packageName, state, onDone) else Loading()
    }
}

@Composable
private fun LimitEditor(packageName: String, state: LimitEditorState, onDone: () -> Unit) {
    val existing = state.existing
    var dailyOn by rememberSaveable { mutableStateOf(existing == null || existing.dailyMinutes > 0) }
    var daily by rememberSaveable { mutableIntStateOf(existing?.dailyMinutes?.takeIf { it > 0 } ?: 30) }
    var weeklyOn by rememberSaveable { mutableStateOf((existing?.weeklyMinutes ?: 0) > 0) }
    var weekly by rememberSaveable { mutableIntStateOf(existing?.weeklyMinutes?.takeIf { it > 0 } ?: 180) }
    var message by rememberSaveable { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    val action = rememberAction()
    val celebrate = LocalCelebrate.current
    val hasBees = state.bumblebeeCount > 0

    val proposed = AppLimit(packageName, state.appName, if (dailyOn) daily else 0, if (weeklyOn) weekly else 0)
    val needsApproval = existing != null && hasBees && LimitsRepository.isLooser(existing, proposed)
    val planted = stringResource(R.string.limit_editor_planted)
    val updated = stringResource(R.string.limit_editor_updated)
    val requested = stringResource(R.string.limit_editor_requested)
    val removed = stringResource(R.string.limit_editor_removed)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.appName, style = MaterialTheme.typography.headlineSmall)
                state.budget?.let {
                    Text(
                        stringResource(R.string.limit_editor_usage, durationText(it.usedTodayMs), durationText(it.usedWeekMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BloomFlower(1f - (state.budget?.usedFraction ?: 0f), Garden.colors.petalPink, Modifier.size(48.dp, 72.dp))
        }

        MeadowCard(Modifier.fillMaxWidth()) {
            ToggleRow(stringResource(R.string.limit_editor_daily), dailyOn) { dailyOn = it }
            if (dailyOn) {
                MinutesStepper(daily, step = 5, min = 5, max = 16 * 60) { daily = it }
                PresetChips(listOf(15, 30, 45, 60, 90, 120), daily) { daily = it }
            }
        }
        MeadowCard(Modifier.fillMaxWidth()) {
            ToggleRow(stringResource(R.string.limit_editor_weekly), weeklyOn) { weeklyOn = it }
            if (weeklyOn) {
                MinutesStepper(weekly, step = 30, min = 30, max = 100 * 60) { weekly = it }
                PresetChips(listOf(120, 180, 300, 420, 600), weekly) { weekly = it }
            }
            Text(
                stringResource(R.string.limit_editor_weekly_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (needsApproval) {
            MeadowCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(stringResource(R.string.limit_editor_loosens_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.limit_editor_loosens_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(280) },
                    label = { Text(stringResource(R.string.limit_editor_note)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        } else if (existing == null && hasBees) {
            Text(
                stringResource(R.string.limit_editor_locked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ErrorNote(action.error)
        Button(
            onClick = {
                action.run(onSuccess = onDone) {
                    val outcome = AppGraph.limits.save(proposed, message)
                    if (outcome == ChangeOutcome.APPLIED) celebrate()
                    action.toast(
                        when {
                            outcome == ChangeOutcome.REQUESTED -> requested
                            existing == null -> planted
                            else -> updated
                        },
                    )
                }
            },
            enabled = !action.busy && (dailyOn || weeklyOn) && proposed != existing,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                stringResource(
                    when {
                        needsApproval -> R.string.limit_editor_ask
                        existing == null -> R.string.limit_editor_plant
                        else -> R.string.action_save
                    },
                ),
            )
        }
        if (existing != null) {
            OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (hasBees) R.string.limit_editor_ask_remove else R.string.limit_editor_remove))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmRemove && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.limit_remove_title, existing.appName)) },
            text = { Text(stringResource(if (hasBees) R.string.limit_remove_body_ask else R.string.limit_remove_body_now)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    action.run(onSuccess = onDone) {
                        val outcome = AppGraph.limits.remove(existing, message)
                        action.toast(if (outcome == ChangeOutcome.REQUESTED) requested else removed)
                    }
                }) { Text(stringResource(if (hasBees) R.string.limit_remove_ask else R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_keep)) } },
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
        FilledTonalIconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.limit_editor_less))
        }
        Text(minutesText(value), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 24.dp))
        FilledTonalIconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.limit_editor_more))
        }
    }
}

@Composable
private fun PresetChips(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { minutes ->
            FilterChip(selected = minutes == selected, onClick = { onSelect(minutes) }, label = { Text(minutesText(minutes)) })
        }
    }
}
