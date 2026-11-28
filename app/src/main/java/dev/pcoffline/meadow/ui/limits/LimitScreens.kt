@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.limits

import dev.pcoffline.meadow.ui.components.rememberIconColour
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.material.icons.outlined.Share
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
import dev.pcoffline.meadow.data.LimitCardMode
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
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.holdsBack
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.ui.components.ShareCommitment
import dev.pcoffline.meadow.ui.requests.BeePicks
import dev.pcoffline.meadow.ui.requests.BumblebeePicker
import dev.pcoffline.meadow.ui.requests.pendingRequestFor
import dev.pcoffline.meadow.ui.requests.rememberBeePicks
import dev.pcoffline.meadow.ui.requestSummary
import dev.pcoffline.meadow.ui.requests.LimitLines
import dev.pcoffline.meadow.ui.requests.LineChange
import dev.pcoffline.meadow.ui.requests.limitLines
import dev.pcoffline.meadow.ui.ActionState
import androidx.compose.ui.platform.LocalResources
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.pcoffline.meadow.data.RequestType
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import dev.pcoffline.meadow.ui.components.flora.plantFor

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
    /** Whether [pending] is known yet: the server is asked, since the phone's copy may be out of date. */
    val pendingKnown: Boolean = true,
    val loaded: Boolean = false,
)

/** What's known of my open question about an app: [request] is null when there's none. */
private class Known(val request: LimitRequest?)

class LimitEditorViewModel(private val packageName: String) : ViewModel() {
    val state: StateFlow<LimitEditorState> = combine(
        AppGraph.store.limits,
        AppGraph.store.approverCount,
        // The editor shows at once; only asking waits until it's known whether a question is open.
        pendingRequestFor(packageName).map<LimitRequest?, Known?> { Known(it) }.onStart { emit(null) },
    ) { limits, bees, known ->
        val existing = limits.firstOrNull { it.packageName == packageName }
        LimitEditorState(
            existing = existing,
            appName = existing?.appName ?: AppGraph.catalog.label(packageName),
            budget = AppGraph.engine.budget(packageName),
            bumblebeeCount = bees,
            pending = known?.request,
            pendingKnown = known != null,
            loaded = true,
        )
    }.flowOn(Dispatchers.IO).stateWhileVisible(this, LimitEditorState())
}

@Composable
fun LimitEditorScreen(
    packageName: String,
    onDone: () -> Unit,
    // A copied limit to start from, and a way to pick another app for it.
    start: AppLimit? = null,
    onChangeApp: (() -> Unit)? = null,
    viewModel: LimitEditorViewModel = viewModel(key = "limit-$packageName") { LimitEditorViewModel(packageName) },
) {
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    // A copy of a limit for an app that isn't here keeps its own name, and can't be saved until
    // another app is chosen.
    val notHere = start != null && remember(packageName) { !onThisPhone(packageName, AppGraph.catalog.launchablePackages()) }
    val state = start?.takeIf { notHere }?.let { loaded.copy(appName = it.appName) } ?: loaded
    var unsaved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(
            stringResource(if (state.existing == null) R.string.limit_editor_new else R.string.limit_editor_existing),
            onBack = onDone,
        ) { if (start == null) ShareLimitButton(state.existing, unsaved) }
        if (state.loaded) {
            LimitEditor(packageName, state, onDone, start = start, onChangeApp = onChangeApp, notHere = notHere, onUnsaved = { unsaved = it }, firstTimeGuide = true)
        } else {
            Loading()
        }
    }
}

/**
 * Shares [saved], the limit as it is saved, at any time. While the fields have [unsaved] changes,
 * it's off: what it would share isn't what they show. Save offers to share the new limit.
 */
@Composable
internal fun ShareLimitButton(saved: AppLimit?, unsaved: Boolean) {
    saved ?: return
    val res = LocalResources.current
    IconButton(onClick = { ShareCommitment.openLimit(res, saved) }, enabled = !unsaved) {
        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.limit_share))
    }
}

/**
 * Writes a limit for someone else's app, to send to them in [chatId] as a suggested limit card.
 * Starts from [start], their limit, or from nothing for an app with no limit.
 */
@Composable
fun SuggestLimitScreen(chatId: String, packageName: String, appName: String, start: AppLimit?, onDone: () -> Unit) {
    val res = LocalResources.current
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.limit_suggest_title), onBack = onDone)
        LimitEditor(
            packageName,
            LimitEditorState(appName = appName, loaded = true),
            onDone,
            start = start,
            sendTo = { AppGraph.chats.sendLimit(chatId, it, LimitCardMode.SUGGESTED, res.getString(R.string.limit_card_suggested_words, it.appName)) },
        )
    }
}

@Composable
internal fun LimitEditor(
    packageName: String,
    state: LimitEditorState,
    onDone: () -> Unit,
    // Where the fields start: a copied or suggested limit. Without one, the saved limit.
    start: AppLimit? = null,
    onChangeApp: (() -> Unit)? = null,
    // Set when suggesting: Send gives the limit to this, and nothing is saved here.
    sendTo: (suspend (AppLimit) -> Unit)? = null,
    // A copy for an app that isn't on this phone: it says so, and saves nothing.
    notHere: Boolean = false,
    // Tests pass their own; the app saves through the limits repository.
    save: suspend (limit: AppLimit, message: String, approverIds: List<String>?) -> ChangeOutcome = AppGraph.limits::save,
    // Told whether the fields differ from the saved limit, so sharing the saved one can wait.
    onUnsaved: (Boolean) -> Unit = {},
    // The editor screen shows the guide to the week by itself, once on each phone.
    firstTimeGuide: Boolean = false,
) {
    val existing = state.existing
    val from = start ?: existing
    // A new limit starts with 30m a day and nothing else; kept as JSON so it survives rotation.
    var weekJson by rememberSaveable {
        mutableStateOf(
            ScheduleJson.encodeToString(
                WeekLimitSerializer,
                WeekLimitState(from?.dailyMinutes ?: 30, from?.weeklyMinutes ?: 0, from?.schedule, from?.dayMinutes.orEmpty(), from?.caps.orEmpty()),
            ),
        )
    }
    val week = ScheduleJson.decodeFromString(WeekLimitSerializer, weekJson)
    val schedule = week.schedule
    val caps = week.caps
    var step by rememberSaveable { mutableIntStateOf(AppGraph.store.dragStep(packageName) ?: DEFAULT_DRAG_STEP) }
    val guideSeen by AppGraph.store.weekGuideSeen.collectAsStateWithLifecycle()
    var guide by rememberSaveable { mutableStateOf(false) }
    // A copied or suggested limit keeps the plant of the limit here: each person chooses their own.
    var plant by rememberSaveable { mutableStateOf(if (sendTo == null) existing?.plant else null) }
    val icon = rememberIconColour(packageName)
    val weekRule by AppGraph.store.weekRule.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    var removeNote by rememberSaveable { mutableStateOf("") }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val picks = rememberBeePicks()
    val action = rememberAction()
    val celebrate = LocalCelebrate.current
    val suggesting = sendTo != null
    // A suggestion is only sent: who looks after whom matters when it's applied.
    val hasBees = state.bumblebeeCount > 0 && !suggesting

    val proposed = AppLimit(
        packageName, state.appName, week.daily, week.weekly,
        schedule = schedule,
        dayMinutes = week.dayMinutes,
        caps = caps,
        plant = if (sendTo == null) plant else null,
    )
    // What the fields made of the saved limit as they opened: changes count against that, so a
    // limit the editor puts in its own terms (merged hours, a default it fills in) isn't "unsaved".
    val opened = remember(existing) { proposed }
    val unsaved = existing != null && proposed != opened
    LaunchedEffect(unsaved) { onUnsaved(unsaved) }
    val needsApproval = existing != null && hasBees && LimitsRepository.isLooser(existing, proposed)
    val online by Connectivity.online.collectAsStateWithLifecycle()
    // Asking, and hours, days and caps with Bumblebees, go through the backend; the rest is saved offline too.
    val needsConnection = needsApproval || (existing != null && hasBees && !existing.sameUncomparable(proposed))
    val editable = !action.busy
    // The backend takes one open question per app, so another has to wait for its answer (or
    // until it's known whether there is one).
    val waiting = state.pending
    val askBlocked = waiting != null || !state.pendingKnown
    val anyLimitOn = proposed.holdsBack
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
                1f - (state.budget?.usedFraction ?: 0f), plantFor(packageName, Garden.theme, plant, icon), Modifier.size(48.dp, 72.dp),
                asleep = state.budget?.sleeping == true,
            )
        }
        if (Sites.isSite(packageName)) Hint(stringResource(R.string.limit_editor_site_browsers))
        if (notHere) Hint(stringResource(R.string.limit_apply_not_on_phone, state.appName))
        // Copying over a limit that's already here.
        if (start != null && existing != null && !suggesting) {
            Hint(stringResource(R.string.limit_copy_replaces, existing.appName))
        }
        onChangeApp?.let { TextButton(onClick = it, enabled = editable) { Text(stringResource(R.string.limit_copy_other_app)) } }
        if (!suggesting) PlantPicker(packageName, icon, plant, editable) { plant = it }

        val waitingHere = waiting != null && !suggesting
        // A change waiting for an answer shows on the card; the week stays as it is until then.
        val changeWaiting = waitingHere && waiting?.type == RequestType.CHANGE_LIMIT
        if (waitingHere) PendingCard(waiting!!, action)
        MeadowCard(Modifier.fillMaxWidth()) {
            WeekCard(
                limit = WeekLimit(schedule, caps, week.daily, week.weekly, week.dayMinutes),
                firstDay = weekRule.first,
                step = step,
                enabled = editable && !changeWaiting,
                waiting = changeWaiting,
                onChange = { weekJson = ScheduleJson.encodeToString(WeekLimitSerializer, WeekLimitState(it.daily, it.weekly, it.schedule, it.dayMinutes, it.caps)) },
                onStep = {
                    step = it
                    AppGraph.store.setDragStep(packageName, it)
                },
                onHelp = { guide = true },
            )
            if (changeWaiting) AsNowNote()
        }
        // The guide shows by itself the first time the week shows on this phone.
        if (guide || (firstTimeGuide && !guideSeen)) {
            WeekGuide(state.appName, weekRule.first) {
                guide = false
                AppGraph.store.setWeekGuideSeen()
            }
        }

        if (!waitingHere && needsApproval && !suggesting) {
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
        if (sendTo != null) {
            val sent = stringResource(R.string.limit_suggest_sent)
            Button(
                onClick = { action.run(successMessage = sent, onSuccess = onDone) { sendTo(proposed) } },
                // A chat message: Firestore queues it offline, so Send works offline too.
                enabled = editable && anyLimitOn,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.limit_suggest_send)) }
            Spacer(Modifier.height(24.dp))
            return@Column
        }
        Button(
            onClick = {
                action.run(onSuccess = onDone) {
                    val outcome = save(proposed, message, if (needsApproval) BeePicks.commit(picks.value) else null)
                    if (outcome == ChangeOutcome.APPLIED) {
                        celebrate()
                        // A commitment just made is the one most worth telling someone about: the
                        // note that says it's saved offers to share it, as saved.
                        action.toast(if (existing == null) planted else updated, shareLabel) { ShareCommitment.openLimit(res, proposed) }
                    } else {
                        action.toast(requested)
                    }
                }
            },
            enabled = editable && !notHere && anyLimitOn && proposed != existing && !(needsApproval && askBlocked) && (online || !needsConnection),
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
                enabled = !action.busy && !(hasBees && askBlocked),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (hasBees) R.string.limit_editor_ask_remove else R.string.limit_editor_remove))
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

/** What the week card edits, as the editor keeps it through rotation. */
@Serializable
private data class WeekLimitState(val daily: Int, val weekly: Int, val schedule: Schedule?, val dayMinutes: Map<Int, Int>, val caps: List<TimeCap>)

private val WeekLimitSerializer = WeekLimitState.serializer()

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
        val previous = request.previous
        if (request.type == RequestType.CHANGE_LIMIT && previous != null) {
            // Only what changes: the rest of the limit is in the cards above.
            val res = LocalResources.current
            val lines = remember(request, res) { res.limitLines(request.proposed, previous).filter { it.change != LineChange.SAME } }
            LimitLines(lines, Modifier.padding(top = 8.dp), tinted = false)
        } else {
            Text(
                stringResource(R.string.limit_editor_pending_body, requestSummary(request).replaceFirstChar { it.uppercase() }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        TextButton(
            onClick = { action.run(successMessage = withdrawn) { AppGraph.requests.cancel(request.id) } },
            enabled = !action.busy,
        ) { Text(stringResource(R.string.request_withdraw)) }
    }
}
