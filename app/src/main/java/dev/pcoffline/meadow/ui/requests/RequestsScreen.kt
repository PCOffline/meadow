@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.requests

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.minutesText
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.requestSummary
import dev.pcoffline.meadow.ui.stateWhileVisible
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class RequestsViewModel(uid: String) : ViewModel() {
    /** Requests friends sent me as one of their Bumblebees; null while loading. */
    val incoming: StateFlow<List<LimitRequest>?> = AppGraph.requests.incoming(uid).stateWhileVisible(this, null)

    /** Requests I sent to my Bumblebees; null while loading. */
    val outgoing: StateFlow<List<LimitRequest>?> = AppGraph.requests.outgoing(uid).stateWhileVisible(this, null)
}

@Composable
fun RequestsScreen(
    profile: UserProfile,
    onOpenCircle: () -> Unit,
    onOpenBeeStats: () -> Unit = {},
    viewModel: RequestsViewModel = viewModel(key = "requests-${profile.uid}") { RequestsViewModel(profile.uid) },
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var asking by rememberSaveable { mutableStateOf(false) }
    val incoming by viewModel.incoming.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoing.collectAsStateWithLifecycle()
    val hasBees = profile.approverIds.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(stringResource(R.string.requests_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.asHeading().padding(start = 20.dp, top = 16.dp))
            Text(
                stringResource(R.string.requests_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            val waiting = incoming?.count { it.status == RequestStatus.PENDING } ?: 0
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(if (waiting > 0) stringResource(R.string.requests_tab_for_you_count, waiting) else stringResource(R.string.requests_tab_for_you)) },
                )
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.requests_tab_yours)) })
            }
            val list = if (tab == 0) incoming else outgoing
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Spacer(Modifier.height(4.dp)) }
                if (tab == 0 && profile.approveeIds.isNotEmpty()) {
                    item {
                        TextButton(onClick = onOpenBeeStats, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.bee_stats_open))
                        }
                    }
                }
                if (list != null && list.isEmpty()) {
                    item {
                        if (tab == 0) {
                            EmptyState("🌤️", stringResource(R.string.requests_empty_for_you_title), stringResource(R.string.requests_empty_for_you_body))
                        } else {
                            EmptyState("🌱", stringResource(R.string.requests_empty_yours_title), stringResource(R.string.requests_empty_yours_body))
                        }
                    }
                }
                items(list.orEmpty(), key = { it.id }) { request ->
                    RequestCard(request, myUid = profile.uid, modifier = Modifier.animateItem().padding(horizontal = 20.dp))
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (hasBees) asking = true else onOpenCircle() },
            icon = { Icon(Icons.Outlined.MoreTime, contentDescription = null) },
            text = { Text(stringResource(if (hasBees) R.string.requests_ask_button else R.string.requests_invite_button)) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    if (asking) AskForTimeDialog(packageName = null, onDismiss = { asking = false })
}

@Composable
fun RequestCard(request: LimitRequest, myUid: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val mine = request.requesterId == myUid
    // A Bumblebee's own copy: theirs to answer while it's waiting.
    val canDecide = !mine && request.status == RequestStatus.PENDING
    var deciding by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val action = rememberAction()
    val celebrate = LocalCelebrate.current
    val sentYes = stringResource(R.string.request_sent_yes)
    val sentNo = stringResource(R.string.request_sent_no)
    val withdrawn = stringResource(R.string.request_withdrawn)

    MeadowCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (request.packageName != null) AppIcon(request.packageName, size = 36.dp) else FlowerAvatar("💌", size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (mine) stringResource(R.string.request_you_asked) else stringResource(R.string.request_they_ask, request.requesterName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(requestSummary(request).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
            }
            StatusPill(request.status)
        }
        usageText(request)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        if (request.message.isNotBlank()) {
            Text(stringResource(R.string.quoted, request.message), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (!compact) {
            Text(
                relativeTime(request.createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val decider = request.decidedByName
        val answered = request.status == RequestStatus.APPROVED || request.status == RequestStatus.DENIED
        val outcome = when {
            !answered -> null
            mine && decider != null -> {
                val note = request.decisionNote?.takeIf { it.isNotBlank() }
                val answer = stringResource(if (request.status == RequestStatus.APPROVED) R.string.request_decided_yes else R.string.request_decided_no, decider)
                if (note != null) stringResource(R.string.request_decided_with_note, answer, note) else answer
            }
            mine -> null
            // Who else answered stays private; only that someone did.
            request.myDecision == null -> stringResource(R.string.request_answered_by_other)
            request.myDecision == "yes" -> stringResource(R.string.request_you_said_yes)
            else -> stringResource(R.string.request_you_said_no)
        }
        if (outcome != null) {
            Text(
                outcome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ErrorNote(action.error, Modifier.padding(top = 8.dp))
        if (canDecide) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { deciding = true }, enabled = !action.busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.request_say_yes))
                }
                OutlinedButton(onClick = { deciding = false }, enabled = !action.busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.request_not_today))
                }
            }
        }
        if (mine && request.status == RequestStatus.PENDING) {
            TextButton(
                onClick = { action.run(successMessage = withdrawn) { AppGraph.requests.cancel(request.id) } },
                enabled = !action.busy,
                modifier = Modifier.padding(top = 4.dp),
            ) { Text(stringResource(R.string.request_withdraw)) }
        }
    }

    deciding?.let { approve ->
        DecideDialog(request, approve, onDismiss = { deciding = null }) { note, minutes ->
            deciding = null
            action.run(successMessage = if (approve) sentYes else sentNo, onSuccess = { if (approve) celebrate() }) {
                AppGraph.requests.decide(request.id, approve, note, minutes)
            }
        }
    }
}

/** How the day stood when they asked: "Used 45m of 1h today". */
@Composable
private fun usageText(request: LimitRequest): String? {
    val used = request.usedMinutes ?: return null
    val allowed = request.allowedMinutes?.takeIf { it > 0 }
    return if (allowed != null) stringResource(R.string.request_usage, minutesText(used), minutesText(allowed))
    else stringResource(R.string.request_usage_no_allowance, minutesText(used))
}

@Composable
private fun StatusPill(status: RequestStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        RequestStatus.PENDING -> Pill(stringResource(R.string.request_status_waiting), scheme.tertiaryContainer)
        RequestStatus.APPROVED -> Pill(stringResource(R.string.request_status_yes), scheme.primaryContainer)
        RequestStatus.DENIED -> Pill(stringResource(R.string.request_status_no), scheme.secondaryContainer)
        RequestStatus.CANCELLED -> Pill(stringResource(R.string.request_status_withdrawn), scheme.surfaceVariant)
        RequestStatus.EXPIRED -> Pill(stringResource(R.string.request_status_expired), scheme.surfaceVariant)
    }
}

@Composable
private fun DecideDialog(request: LimitRequest, approve: Boolean, onDismiss: () -> Unit, onConfirm: (String, Int?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(request.minutes) }
    val extraTime = request.type == RequestType.EXTRA_TIME
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (approve) stringResource(R.string.request_decide_yes_title, request.requesterName)
                else stringResource(R.string.request_decide_no_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(requestSummary(request).replaceFirstChar { it.uppercase() })
                usageText(request)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (approve && extraTime) {
                    Text(stringResource(R.string.request_decide_less), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf(5, 10, 15, 30, 60).filter { it < request.minutes } + request.minutes).forEach {
                            FilterChip(selected = minutes == it, onClick = { minutes = it }, label = { Text(minutesText(it)) })
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text(stringResource(R.string.request_decide_note)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note, if (approve && extraTime) minutes else null) }) {
                Text(stringResource(if (approve) R.string.request_say_yes else R.string.action_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) } },
    )
}

/** Lets someone ask their Bumblebees for extra minutes on a limited app. */
@Composable
fun AskForTimeDialog(packageName: String?, onDismiss: () -> Unit) {
    val limits by AppGraph.store.limits.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(packageName ?: limits.firstOrNull()?.packageName) }
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    var message by rememberSaveable { mutableStateOf("") }
    val picks = rememberBeePicks()
    val action = rememberAction()
    // Limits may arrive after the dialog opens, so fall back to the first one once they do.
    val limit: AppLimit? = limits.firstOrNull { it.packageName == selected } ?: limits.firstOrNull().takeIf { packageName == null }
    val asked = stringResource(R.string.ask_sent)
    // One open question per app: say so now rather than after they've written it.
    val pendingFlow = remember(limit?.packageName) { limit?.packageName?.let(::pendingRequestFor) ?: flowOf(null) }
    val waiting by pendingFlow.collectAsStateWithLifecycle(null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ask_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (packageName == null) {
                    if (limits.isEmpty()) Text(stringResource(R.string.ask_no_limits))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        limits.forEach {
                            FilterChip(selected = limit?.packageName == it.packageName, onClick = { selected = it.packageName }, label = { Text(it.appName) })
                        }
                    }
                }
                if (waiting != null && limit != null) {
                    Text(
                        stringResource(R.string.ask_pending, limit.appName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(stringResource(R.string.ask_how_much), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach {
                        FilterChip(selected = minutes == it, onClick = { minutes = it }, label = { Text(minutesText(it)) })
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(280) },
                    label = { Text(stringResource(R.string.ask_why)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                BumblebeePicker(picks)
                ErrorNote(action.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = limit != null && waiting == null && !action.busy,
                onClick = {
                    val l = limit ?: return@TextButton
                    action.run(successMessage = asked, onSuccess = onDismiss) {
                        AppGraph.requests.create(
                            type = RequestType.EXTRA_TIME,
                            message = message,
                            packageName = l.packageName,
                            appName = l.appName,
                            minutes = minutes,
                            approverIds = BeePicks.commit(picks.value),
                        )
                    }
                },
            ) { Text(stringResource(if (action.busy) R.string.ask_sending else R.string.action_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
