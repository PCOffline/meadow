@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.requests

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.data.formatMinutes
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.switchTab

@Composable
fun RequestsScreen(profile: UserProfile, nav: NavController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf(false) }
    val incoming by remember(profile.uid) { AppGraph.requests.incoming(profile.uid) }.collectAsStateWithLifecycle(null)
    val outgoing by remember(profile.uid) { AppGraph.requests.outgoing(profile.uid) }.collectAsStateWithLifecycle(null)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text("Requests", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
            Text(
                "Asking for more time, and answering friends",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            val waiting = incoming?.count { it.status == RequestStatus.PENDING } ?: 0
            PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (waiting > 0) "For you ($waiting)" else "For you") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Yours") })
            }
            val list = if (tab == 0) incoming else outgoing
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                if (list != null && list.isEmpty()) {
                    item {
                        if (tab == 0) {
                            EmptyState(
                                "🌤️", "Nothing to answer",
                                "When someone you're a Petal Pal for asks for more time, it shows up here.",
                            )
                        } else {
                            EmptyState(
                                "🌱", "No requests yet",
                                "When an app is resting, you can ask your Petal Pals for a little more time.",
                            )
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
            onClick = {
                if (profile.approverIds.isEmpty()) nav.switchTab("circle") else asking = true
            },
            icon = { Icon(Icons.Outlined.MoreTime, contentDescription = null) },
            text = { Text(if (profile.approverIds.isEmpty()) "Invite a Petal Pal" else "Ask for time") },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    if (asking) AskForTimeDialog(packageName = null, onDismiss = { asking = false })
}

@Composable
fun RequestCard(request: LimitRequest, myUid: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val mine = request.requesterId == myUid
    val canDecide = !mine && request.status == RequestStatus.PENDING && myUid in request.approverIds
    var deciding by remember { mutableStateOf<Boolean?>(null) }
    val action = rememberAction()
    val celebrate = LocalCelebrate.current

    MeadowCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (request.packageName != null) AppIcon(request.packageName, size = 36.dp)
            else FlowerAvatar("💌", size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (mine) "You asked" else "${request.requesterName} asks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(request.summary, style = MaterialTheme.typography.titleMedium)
            }
            StatusPill(request.status)
        }
        if (request.message.isNotBlank()) {
            Text(
                "“${request.message}”",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!compact) {
            Text(
                relativeTime(request.createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (request.status != RequestStatus.PENDING && request.decidedByName != null) {
            val verb = if (request.status == RequestStatus.APPROVED) "said yes" else "said no"
            Text(
                buildString {
                    append("${request.decidedByName} $verb")
                    if (!request.decisionNote.isNullOrBlank()) append(": “${request.decisionNote}”")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ErrorNote(action.error, Modifier.padding(top = 8.dp))
        if (canDecide) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { deciding = true }, enabled = !action.busy, modifier = Modifier.weight(1f)) { Text("Say yes 🌸") }
                OutlinedButton(onClick = { deciding = false }, enabled = !action.busy, modifier = Modifier.weight(1f)) { Text("Not today") }
            }
        }
        if (mine && request.status == RequestStatus.PENDING) {
            TextButton(
                onClick = { action.run(successMessage = "Request withdrawn") { AppGraph.requests.cancel(request.id) } },
                enabled = !action.busy,
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Withdraw") }
        }
    }

    deciding?.let { approve ->
        DecideDialog(request, approve, onDismiss = { deciding = null }) { note, minutes ->
            deciding = null
            action.run(
                successMessage = if (approve) "Sent your yes 🌸" else "Answered gently 🍃",
                onSuccess = { if (approve) celebrate() },
            ) {
                AppGraph.requests.decide(request.id, approve, note, minutes)
            }
        }
    }
}

@Composable
private fun StatusPill(status: RequestStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        RequestStatus.PENDING -> Pill("Waiting", scheme.tertiaryContainer)
        RequestStatus.APPROVED -> Pill("Yes 🌸", scheme.primaryContainer)
        RequestStatus.DENIED -> Pill("Not today", scheme.secondaryContainer)
        RequestStatus.CANCELLED -> Pill("Withdrawn", scheme.surfaceVariant)
        RequestStatus.EXPIRED -> Pill("Expired", scheme.surfaceVariant)
    }
}

@Composable
private fun DecideDialog(request: LimitRequest, approve: Boolean, onDismiss: () -> Unit, onConfirm: (String, Int?) -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(request.minutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (approve) "Say yes to ${request.requesterName}?" else "Not today?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.summary)
                if (approve && request.type == RequestType.EXTRA_TIME) {
                    Text("You can give a little less if you like:", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30, 60).filter { it <= request.minutes }.plus(request.minutes).distinct().sorted().forEach {
                            FilterChip(selected = minutes == it, onClick = { minutes = it }, label = { Text(formatMinutes(it)) })
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(200) },
                    label = { Text("A kind word (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(note, if (request.type == RequestType.EXTRA_TIME && approve) minutes else null)
            }) { Text(if (approve) "Say yes" else "Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Back") } },
    )
}

/** Lets someone ask their approvers for extra minutes on a limited app. */
@Composable
fun AskForTimeDialog(packageName: String?, onDismiss: () -> Unit, onSent: () -> Unit = {}) {
    val limits by AppGraph.store.limits.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(packageName ?: limits.firstOrNull()?.packageName) }
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    var message by rememberSaveable { mutableStateOf("") }
    val action = rememberAction()
    val limit: AppLimit? = limits.firstOrNull { it.packageName == selected }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask for a little more time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (packageName == null) {
                    if (limits.isEmpty()) Text("You haven't planted any limits yet.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        limits.forEach {
                            FilterChip(
                                selected = selected == it.packageName,
                                onClick = { selected = it.packageName },
                                label = { Text(it.appName) },
                            )
                        }
                    }
                }
                Text("How much?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30, 60).forEach {
                        FilterChip(selected = minutes == it, onClick = { minutes = it }, label = { Text(formatMinutes(it)) })
                    }
                }
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(280) },
                    label = { Text("Why? (helps them say yes)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorNote(action.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = limit != null && !action.busy,
                onClick = {
                    val l = limit ?: return@TextButton
                    action.run(successMessage = "Asked your Petal Pals 💌", onSuccess = { onSent(); onDismiss() }) {
                        AppGraph.requests.create(
                            type = RequestType.EXTRA_TIME,
                            message = message,
                            packageName = l.packageName,
                            appName = l.appName,
                            minutes = minutes,
                        )
                    }
                },
            ) { Text(if (action.busy) "Sending…" else "Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
