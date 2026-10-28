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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.widthIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Sites
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.Connectivity
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.Hint
import dev.pcoffline.meadow.ui.components.OfflineNote
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.limitText
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.components.ShareCommitment
import dev.pcoffline.meadow.ui.requests.BeePicks
import dev.pcoffline.meadow.ui.requests.BumblebeePicker
import dev.pcoffline.meadow.ui.requests.pendingRequestFor
import dev.pcoffline.meadow.ui.requests.rememberBeePicks
import dev.pcoffline.meadow.ui.requestSummary
import dev.pcoffline.meadow.ui.ActionState
import androidx.compose.ui.platform.LocalResources
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.sameUncomparable
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
    val limits by AppGraph.store.limits.collectAsStateWithLifecycle()
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
            // Typing an address offers the website itself, above any apps that match.
            val site = remember(query) { query.takeIf { '.' in it }?.let(Sites::normalize) }
            if (filtered.isEmpty() && query.isNotBlank() && site == null) {
                Hint(stringResource(R.string.picker_no_match, query.trim()), Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
            LazyColumn(Modifier.fillMaxSize().imePadding()) {
                if (site != null) {
                    item(key = "site") {
                        val id = Sites.idFor(site)
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.picker_site, site)) },
                            supportingContent = { Text(stringResource(R.string.picker_site_kind)) },
                            leadingContent = { AppIcon(id) },
                            trailingContent = {
                                if (limits.any { it.packageName == id }) Pill(stringResource(R.string.picker_planted), MaterialTheme.colorScheme.primaryContainer)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.animateItem().clickable { onPick(id) }.padding(horizontal = 8.dp),
                        )
                    }
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
    var groupsJson by rememberSaveable { mutableStateOf(ScheduleJson.encodeToString(GroupsSerializer, dayGroups(existing?.dayMinutes.orEmpty()))) }
    val groups = ScheduleJson.decodeFromString(GroupsSerializer, groupsJson)
    var capsJson by rememberSaveable { mutableStateOf(ScheduleJson.encodeToString(CapsSerializer, existing?.caps.orEmpty())) }
    val caps = ScheduleJson.decodeFromString(CapsSerializer, capsJson)
    val week by AppGraph.store.weekRule.collectAsStateWithLifecycle()
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
        dayMinutes = if (dailyOn) groups.toDayMinutes() else emptyMap(),
        caps = caps,
    )
    val needsApproval = existing != null && hasBees && LimitsRepository.isLooser(existing, proposed)
    val online by Connectivity.online.collectAsStateWithLifecycle()
    // Asking, and hours, days and caps with Bumblebees, go through the backend; the rest is saved offline too.
    val needsConnection = needsApproval || (existing != null && hasBees && !existing.sameUncomparable(proposed))
    val editable = !action.busy
    // The backend takes one open question per app, so another has to wait for its answer.
    val waiting = state.pending
    val anyLimitOn = dailyOn || weeklyOn || hoursOn || caps.isNotEmpty()
    val planted = stringResource(R.string.limit_editor_planted)
    val updated = stringResource(R.string.limit_editor_updated)
    val requested = stringResource(R.string.limit_editor_requested)
    val shareLabel = stringResource(R.string.share_title)
    val res = LocalResources.current
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
            BloomFlower(
                1f - (state.budget?.usedFraction ?: 0f), Garden.colors.petalPink, Modifier.size(48.dp, 72.dp),
                asleep = state.budget?.sleeping == true,
            )
        }
        if (Sites.isSite(packageName)) Hint(stringResource(R.string.limit_editor_site_browsers))

        MeadowCard(Modifier.fillMaxWidth()) {
            ToggleRow(stringResource(R.string.limit_editor_daily), dailyOn, editable) { dailyOn = it }
            if (dailyOn) {
                val label = stringResource(R.string.limit_editor_daily)
                MinutesStepper(daily, step = 5, min = 5, max = 16 * 60, editable, label = label) { daily = it }
                PresetChips(listOf(15, 30, 45, 60, 90, 120), daily, editable) { daily = it }
                Spacer(Modifier.height(4.dp))
                DayLimitsEditor(groups, week.first, editable) { groupsJson = ScheduleJson.encodeToString(GroupsSerializer, it) }
            }
        }
        MeadowCard(Modifier.fillMaxWidth()) {
            ToggleRow(stringResource(R.string.limit_editor_weekly), weeklyOn, editable) { weeklyOn = it }
            if (weeklyOn) {
                MinutesStepper(weekly, step = 30, min = 30, max = 100 * 60, editable) { weekly = it }
                PresetChips(listOf(120, 180, 300, 420, 600), weekly, editable) { weekly = it }
            }
        }
        MeadowCard(Modifier.fillMaxWidth()) {
            ToggleRow(stringResource(R.string.limit_editor_hours), hoursOn, editable) { hoursOn = it }
            if (hoursOn) {
                Spacer(Modifier.height(8.dp))
                ScheduleEditor(schedule, firstDay = week.first, enabled = editable) { scheduleJson = ScheduleJson.encodeToString(Schedule.serializer(), it) }
            }
        }
        MeadowCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.limit_editor_caps), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
            CapsEditor(caps, week.first, editable) { capsJson = ScheduleJson.encodeToString(CapsSerializer, it) }
        }

        if (waiting != null) {
            PendingCard(waiting, action)
        } else if (needsApproval) {
            MeadowCard(Modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(stringResource(R.string.limit_editor_loosens_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(280) },
                    label = { Text(stringResource(R.string.limit_editor_note)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                BumblebeePicker(picks, Modifier.padding(top = 8.dp))
            }
        }

        if (!anyLimitOn) {
            Hint(stringResource(R.string.limit_editor_pick_one))
        } else if (existing == null && hasBees) {
            Hint(stringResource(R.string.limit_editor_locked_hint))
        }
        if (!online && needsConnection) OfflineNote()
        ErrorNote(action.error)
        Button(
            onClick = {
                action.run(onSuccess = onDone) {
                    val outcome = AppGraph.limits.save(proposed, message, if (needsApproval) BeePicks.commit(picks.value) else null)
                    if (outcome == ChangeOutcome.APPLIED) {
                        celebrate()
                        // A commitment just made is the one most worth telling someone about.
                        val shared = res.getString(R.string.share_limit, proposed.appName, res.limitText(proposed))
                        action.toast(if (existing == null) planted else updated, shareLabel) { ShareCommitment.open(shared) }
                    } else {
                        action.toast(requested)
                    }
                }
            },
            enabled = editable && anyLimitOn && proposed != existing && !(needsApproval && waiting != null) && (online || !needsConnection),
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
            // Straight to Android's share sheet: picking where it goes is the only choice to make.
            TextButton(
                onClick = { ShareCommitment.open(res.getString(R.string.share_limit, existing.appName, res.limitText(existing))) },
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
                    if (hasBees && !online) OfflineNote()
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
                TextButton(enabled = online || !hasBees, onClick = {
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
private val GroupsSerializer = ListSerializer(DayGroup.serializer())
private val CapsSerializer = ListSerializer(TimeCap.serializer())

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
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A length of time with − and + either side. With a [label], tapping the time opens a clock to
 * pick it straight away, for any length under a day.
 */
@Composable
internal fun MinutesStepper(value: Int, step: Int, min: Int, max: Int, enabled: Boolean, label: String? = null, onChange: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val clock = label != null && max < 24 * 60
    val pick = stringResource(R.string.limit_editor_pick_time)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Read through the latest value, so holding a button keeps counting from where it is.
        val current by rememberUpdatedState(value)
        RepeatingIconButton(onStep = { onChange((current - step).coerceAtLeast(min)) }, enabled = enabled && value > min) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.limit_editor_less))
        }
        // As wide as the widest value it can show, so the buttons stay put as "5h" becomes "5h 30m".
        val style = MaterialTheme.typography.headlineMedium
        val ragged = generateSequence(max) { it - step }.takeWhile { it >= min }.firstOrNull { it % 60 != 0 }
        val widest = widestLabel(listOfNotNull(min, max, ragged), style)
        Text(
            minutesText(value),
            style = style,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = if (clock && enabled) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = Modifier.padding(horizontal = 8.dp)
                .then(if (clock) Modifier.clickable(enabled = enabled, onClickLabel = pick, role = Role.Button) { picking = true } else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp).widthIn(min = widest),
        )
        RepeatingIconButton(onStep = { onChange((current + step).coerceAtMost(max)) }, enabled = enabled && value < max) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.limit_editor_more))
        }
    }
    if (picking && label != null) {
        DurationDialog(label, value, min, max, onDismiss = { picking = false }) {
            picking = false
            onChange(it)
        }
    }
}

/** The width of the widest of [minutes] as text, in [style]. */
@Composable
private fun widestLabel(minutes: List<Int>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val labels = minutes.map { minutesText(it) }
    val widest = remember(labels, style) { labels.maxOf { measurer.measure(it, style).size.width } }
    return with(LocalDensity.current) { widest.toDp() }
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
private fun PresetChips(options: List<Int>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { minutes ->
            FilterChip(selected = minutes == selected, onClick = { onSelect(minutes) }, label = { Text(minutesText(minutes)) }, enabled = enabled)
        }
    }
}
