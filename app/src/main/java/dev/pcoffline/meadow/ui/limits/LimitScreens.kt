@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.limits

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.limitText
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.components.shareText
import dev.pcoffline.meadow.ui.requests.BeePicks
import dev.pcoffline.meadow.ui.requests.BumblebeePicker
import dev.pcoffline.meadow.ui.requests.pendingRequestFor
import dev.pcoffline.meadow.ui.requests.rememberBeePicks
import dev.pcoffline.meadow.ui.requestSummary
import dev.pcoffline.meadow.ui.ActionState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import kotlinx.serialization.json.Json
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                if (filtered.isEmpty() && query.isNotBlank()) {
                    item { EmptyState("🔍", stringResource(R.string.picker_no_match_title), stringResource(R.string.picker_no_match_body, query.trim())) }
                }
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
    /** My unanswered question about this app; only one can be open at a time. */
    val pending: LimitRequest? = null,
    val loaded: Boolean = false,
)

class LimitEditorViewModel(private val packageName: String) : ViewModel() {
    val state: StateFlow<LimitEditorState> = combine(
        AppGraph.store.limits,
        AppGraph.store.approverCount,
        pendingRequestFor(packageName),
    ) { limits, bees, pending ->
        val existing = limits.firstOrNull { it.packageName == packageName }
        LimitEditorState(
            existing = existing,
            appName = existing?.appName ?: AppGraph.catalog.label(packageName),
            budget = AppGraph.engine.budget(packageName),
            bumblebeeCount = bees,
            pending = pending,
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
internal fun LimitEditor(packageName: String, state: LimitEditorState, onDone: () -> Unit) {
    val existing = state.existing
    var dailyOn by rememberSaveable { mutableStateOf(existing == null || existing.dailyMinutes > 0) }
    var daily by rememberSaveable { mutableIntStateOf(existing?.dailyMinutes?.takeIf { it > 0 } ?: 30) }
    var weeklyOn by rememberSaveable { mutableStateOf((existing?.weeklyMinutes ?: 0) > 0) }
    var weekly by rememberSaveable { mutableIntStateOf(existing?.weeklyMinutes?.takeIf { it > 0 } ?: 180) }
    var hoursOn by rememberSaveable { mutableStateOf(existing?.schedule != null) }
    // Kept as JSON so it survives rotation.
    var scheduleJson by rememberSaveable {
        mutableStateOf(ScheduleJson.encodeToString(Schedule.serializer(), existing?.schedule ?: Schedule.everyDay(TimeWindow(0, 22 * 60))))
    }
    val schedule = ScheduleJson.decodeFromString(Schedule.serializer(), scheduleJson)
    var message by rememberSaveable { mutableStateOf("") }
    var removeNote by rememberSaveable { mutableStateOf("") }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val picks = rememberBeePicks()
    val action = rememberAction()
    val celebrate = LocalCelebrate.current
    val hasBees = state.bumblebeeCount > 0

    val proposed = AppLimit(
        packageName, state.appName, if (dailyOn) daily else 0, if (weeklyOn) weekly else 0,
        schedule = if (hoursOn) schedule else null,
    )
    val needsApproval = existing != null && hasBees && LimitsRepository.isLooser(existing, proposed)
    // The backend takes one open question per app, so another has to wait for its answer.
    val waiting = state.pending
    val anyLimitOn = dailyOn || weeklyOn || hoursOn
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
            if (dailyOn && weeklyOn && weekly < daily) {
                Text(
                    stringResource(R.string.limit_editor_weekly_below_daily),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        MeadowCard(Modifier.fillMaxWidth()) {
            ToggleRow(stringResource(R.string.limit_editor_hours), hoursOn) { hoursOn = it }
            Text(
                stringResource(R.string.limit_editor_hours_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hoursOn) {
                Spacer(Modifier.height(8.dp))
                ScheduleEditor(schedule) { scheduleJson = ScheduleJson.encodeToString(Schedule.serializer(), it) }
            }
        }

        if (waiting != null) {
            PendingCard(waiting, action)
        } else if (needsApproval) {
            MeadowCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(stringResource(R.string.limit_editor_loosens_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.limit_editor_loosens_body), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(280) },
                    label = { Text(stringResource(R.string.limit_editor_note)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                BumblebeePicker(picks, Modifier.padding(top = 8.dp))
            }
        } else if (existing == null && hasBees) {
            Text(
                stringResource(R.string.limit_editor_locked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!anyLimitOn) {
            Text(
                stringResource(R.string.limit_editor_pick_one),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ErrorNote(action.error)
        Button(
            onClick = {
                action.run(onSuccess = onDone) {
                    val outcome = AppGraph.limits.save(proposed, message, if (needsApproval) BeePicks.commit(picks.value) else null)
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
            enabled = !action.busy && anyLimitOn && proposed != existing && !(needsApproval && waiting != null),
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
            OutlinedButton(
                onClick = { confirmRemove = true },
                enabled = !action.busy && !(hasBees && waiting != null),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (hasBees) R.string.limit_editor_ask_remove else R.string.limit_editor_remove))
            }
            val context = LocalContext.current
            val res = LocalResources.current
            // Straight to Android's share sheet: picking where it goes is the only choice to make.
            TextButton(
                onClick = {
                    context.shareText(res.getString(R.string.share_limit, existing.appName, res.limitText(existing.dailyMinutes, existing.weeklyMinutes, existing.schedule)))
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.limit_editor_share))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmRemove && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.limit_remove_title, existing.appName)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (hasBees) R.string.limit_remove_body_ask else R.string.limit_remove_body_now))
                    if (hasBees) {
                        OutlinedTextField(
                            value = removeNote, onValueChange = { removeNote = it.take(280) },
                            label = { Text(stringResource(R.string.limit_editor_note)) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BumblebeePicker(picks)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    action.run(onSuccess = onDone) {
                        val outcome = AppGraph.limits.remove(existing, removeNote, if (hasBees) BeePicks.commit(picks.value) else null)
                        action.toast(if (outcome == ChangeOutcome.REQUESTED) requested else removed)
                    }
                }) { Text(stringResource(if (hasBees) R.string.limit_remove_ask else R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

private val ScheduleJson = Json { ignoreUnknownKeys = true }

/** The question already waiting about this app, and a way to take it back. */
@Composable
private fun PendingCard(request: LimitRequest, action: ActionState) {
    val withdrawn = stringResource(R.string.request_withdrawn)
    MeadowCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            stringResource(R.string.limit_editor_pending_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            stringResource(R.string.limit_editor_pending_body, requestSummary(request).replaceFirstChar { it.uppercase() }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        TextButton(
            onClick = { action.run(successMessage = withdrawn) { AppGraph.requests.cancel(request.id) } },
            enabled = !action.busy,
        ) { Text(stringResource(R.string.request_withdraw)) }
    }
}

/** The whole row switches, not just the switch: the label is the bigger, more obvious target. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun MinutesStepper(value: Int, step: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Read through the latest value, so holding a button keeps counting from where it is.
        val current by rememberUpdatedState(value)
        RepeatingIconButton(onStep = { onChange((current - step).coerceAtLeast(min)) }, enabled = value > min) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.limit_editor_less))
        }
        Text(minutesText(value), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 24.dp))
        RepeatingIconButton(onStep = { onChange((current + step).coerceAtMost(max)) }, enabled = value < max) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.limit_editor_more))
        }
    }
}

/**
 * A stepper button: a tap moves one step, and holding it keeps stepping, faster after a moment,
 * so a far-off value is a press away rather than dozens of taps.
 */
@Composable
private fun RepeatingIconButton(onStep: () -> Unit, enabled: Boolean, content: @Composable () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Set once a hold starts stepping, so letting go doesn't add the tap's step on top.
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        held = false
        delay(HOLD_START_MS)
        held = true
        var steps = 0
        while (isActive) {
            step()
            steps++
            delay(if (steps < HOLD_FAST_AFTER) HOLD_REPEAT_MS else HOLD_FAST_REPEAT_MS)
        }
    }
    FilledTonalIconButton(
        onClick = {
            if (!held) step()
            held = false
        },
        enabled = enabled,
        interactionSource = interaction,
        content = content,
    )
}

private const val HOLD_START_MS = 400L
private const val HOLD_REPEAT_MS = 120L
private const val HOLD_FAST_REPEAT_MS = 50L
private const val HOLD_FAST_AFTER = 8

@Composable
private fun PresetChips(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { minutes ->
            FilterChip(selected = minutes == selected, onClick = { onSelect(minutes) }, label = { Text(minutesText(minutes)) })
        }
    }
}
