@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.circle

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Invite
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.data.formatMinutes
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.GrowthBar
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.Pill
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.theme.Garden

private const val STALE_HEARTBEAT_MS = 6 * 60 * 60_000L

@Composable
fun CircleScreen(profile: UserProfile, nav: NavController) {
    val approvers by remember(profile.approverIds) { AppGraph.social.profiles(profile.approverIds) }
        .collectAsStateWithLifecycle(emptyList())
    val approvees by remember(profile.approveeIds) { AppGraph.social.profiles(profile.approveeIds) }
        .collectAsStateWithLifecycle(emptyList())
    val incoming by remember(profile.uid) { AppGraph.social.incomingInvites(profile.uid) }.collectAsStateWithLifecycle(emptyList())
    val outgoing by remember(profile.uid) { AppGraph.social.outgoingInvites(profile.uid) }.collectAsStateWithLifecycle(emptyList())

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text("Your circle", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "The friends who keep your garden honest — and the ones you look after.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { InviteCard(profile) }

        if (incoming.isNotEmpty()) {
            item { SectionTitle("Asking for your help", Modifier.padding(horizontal = 20.dp)) }
            items(incoming, key = { it.id }) { IncomingInvite(it) }
        }

        item { SectionTitle("Your approvers", Modifier.padding(horizontal = 20.dp)) }
        if (approvers.isEmpty()) {
            item {
                EmptyState(
                    "🌻", "No approvers yet",
                    "Share your code with someone you trust. Once they accept, only they can give you extra time or loosen your limits.",
                )
            }
        }
        items(approvers, key = { "approver-" + it.uid }) { friend ->
            ApproverRow(profile, friend, onChat = { nav.navigate("chat/${ChatRepository.chatIdFor(profile.uid, friend.uid)}") })
        }
        if (outgoing.isNotEmpty()) {
            items(outgoing, key = { it.id }) { OutgoingInvite(it) }
        }

        item { SectionTitle("You're approving for", Modifier.padding(horizontal = 20.dp)) }
        if (approvees.isEmpty()) {
            item {
                Text(
                    "Nobody yet. When a friend invites you with their code, they'll show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        items(approvees, key = { "approvee-" + it.uid }) { friend ->
            ApproveeRow(friend) { nav.navigate("friend/${friend.uid}") }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun InviteCard(profile: UserProfile) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    val action = rememberAction()
    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), tint = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your friend code", style = MaterialTheme.typography.labelLarge)
                Text(
                    profile.friendCode,
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                )
            }
            IconButton(onClick = {
                val text = "Will you be my Meadow approver? 🌼 Open Meadow → Circle and enter my code: ${profile.friendCode}"
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share your code"),
                )
            }) { Icon(Icons.Outlined.Share, contentDescription = "Share code") }
        }
        Spacer(Modifier.height(10.dp))
        Text("Invite someone to be your approver", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                placeholder = { Text("Their code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    action.run {
                        val direct = AppGraph.social.invite(code)
                        action.toast(if (direct) "Invite sent 💌" else "Your approvers will be asked to OK this first 💌")
                        code = ""
                    }
                },
                enabled = code.length >= 4 && !action.busy,
            ) { Text("Invite") }
        }
        ErrorNote(action.error, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun IncomingInvite(invite: Invite) {
    val action = rememberAction()
    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowerAvatar(invite.fromFlower)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${invite.fromName} would like you as their approver", style = MaterialTheme.typography.titleSmall)
                Text(
                    "You'll be able to say yes or no when they ask for more time.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        ErrorNote(action.error, Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { action.run(successMessage = "You're now ${invite.fromName}'s approver 🌻") { AppGraph.social.respond(invite.id, true) } },
                enabled = !action.busy, modifier = Modifier.weight(1f),
            ) { Text("Accept") }
            OutlinedButton(
                onClick = { action.run { AppGraph.social.respond(invite.id, false) } },
                enabled = !action.busy, modifier = Modifier.weight(1f),
            ) { Text("Decline") }
        }
    }
}

@Composable
private fun OutgoingInvite(invite: Invite) {
    val action = rememberAction()
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowerAvatar("✉️", tint = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(invite.toName, style = MaterialTheme.typography.titleSmall)
            Text("Invite sent ${relativeTime(invite.createdAtMillis)}", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { action.run { AppGraph.social.cancelInvite(invite.id) } }, enabled = !action.busy) { Text("Cancel") }
    }
}

@Composable
private fun ApproverRow(me: UserProfile, friend: UserProfile, onChat: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val action = rememberAction()
    Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowerAvatar(friend.flower)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
            Text("Approves your extra time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onChat) { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Chat") }
        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Ask to remove") }, onClick = { menu = false; confirm = true })
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remove ${friend.displayName}?") },
            text = {
                Text(
                    if (me.approverIds.size > 1) "Your other approvers will be asked to agree first."
                    else "${friend.displayName} will be asked to agree, since they're your only approver.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    action.run(successMessage = "Request sent 💌") { AppGraph.social.requestRemoveApprover(friend.uid) }
                }) { Text("Ask") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
        )
    }
    action.error?.let { ErrorNote(it, Modifier.padding(horizontal = 20.dp)) }
}

@Composable
private fun ApproveeRow(friend: UserProfile, onClick: () -> Unit) {
    val stale = friend.lastHeartbeatMillis?.let { System.currentTimeMillis() - it > STALE_HEARTBEAT_MS } ?: false
    val unprotected = friend.protection["accessibility"] == false || friend.protection["usageAccess"] == false
    MeadowCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowerAvatar(friend.flower, tint = MaterialTheme.colorScheme.secondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    friend.lastHeartbeatMillis?.let { "Last seen ${relativeTime(it)}" } ?: "Not seen yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                unprotected -> Pill("Protection off", MaterialTheme.colorScheme.errorContainer)
                stale -> Pill("Quiet lately", MaterialTheme.colorScheme.tertiaryContainer)
                else -> Pill("Blooming", MaterialTheme.colorScheme.primaryContainer)
            }
        }
    }
}

@Composable
fun FriendScreen(me: UserProfile, uid: String, nav: NavController) {
    val friend by remember(uid) { AppGraph.social.profile(uid) }.collectAsStateWithLifecycle(null)
    val usage by remember(uid) { AppGraph.share.usage(uid, Days.todayKey()) }.collectAsStateWithLifecycle(null)
    val limits by remember(uid) { AppGraph.share.limits(uid) }.collectAsStateWithLifecycle(emptyList())
    var confirmStepDown by remember { mutableStateOf(false) }
    val action = rememberAction()

    Column(Modifier.fillMaxSize()) {
        BackTopBar(friend?.displayName ?: "", onBack = { nav.popBackStack() })
        val f = friend
        if (f == null) Loading() else Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowerAvatar(f.flower, size = 64.dp, tint = MaterialTheme.colorScheme.secondaryContainer)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.displayName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (f.strictMode) "Strict mode on 🌿" else "Strict mode off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = { nav.navigate("chat/${ChatRepository.chatIdFor(me.uid, f.uid)}") }) { Text("Chat") }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Text("Protections", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "usageAccess" to "Usage access",
                    "accessibility" to "Garden keeper",
                    "deviceAdmin" to "Uninstall protection",
                ).forEach { (key, label) ->
                    val on = f.protection[key]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            when (on) { true -> "On ✓"; false -> "Off ⚠️"; null -> "Unknown" },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    f.lastHeartbeatMillis?.let { "Phone last checked in ${relativeTime(it)}" } ?: "Their phone hasn't checked in yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Text("Today", style = MaterialTheme.typography.titleMedium)
                val u = usage
                if (u == null) {
                    Text("No usage shared yet today.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${formatMinutes(u.totalMinutes)} of screen time", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    val max = u.apps.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                    u.apps.take(8).forEach { app ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app.packageName, size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(app.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(
                                        if (app.limitMinutes > 0) "${formatMinutes(app.minutes)} / ${formatMinutes(app.limitMinutes)}"
                                        else formatMinutes(app.minutes),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                GrowthBar(
                                    if (app.limitMinutes > 0) app.minutes / app.limitMinutes.toFloat() else app.minutes / max.toFloat(),
                                    height = 6.dp,
                                    color = if (app.limitMinutes > 0) null else Garden.colors.leaf,
                                )
                            }
                        }
                    }
                }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                Text("Their limits", style = MaterialTheme.typography.titleMedium)
                if (limits.isEmpty()) Text("No limits planted yet.", style = MaterialTheme.typography.bodySmall)
                limits.forEach { limit ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(limit.packageName, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(limit.appName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(LimitRequest.limitText(limit.dailyMinutes, limit.weeklyMinutes), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            ErrorNote(action.error)
            OutlinedButton(onClick = { confirmStepDown = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop being ${f.displayName}'s approver")
            }
            Spacer(Modifier.height(24.dp))
        }
        if (confirmStepDown && f != null) {
            AlertDialog(
                onDismissRequest = { confirmStepDown = false },
                title = { Text("Step down?") },
                text = { Text("${f.displayName} will be told, and you won't receive their requests anymore.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmStepDown = false
                        action.run(successMessage = "You've stepped down", onSuccess = { nav.popBackStack() }) {
                            AppGraph.social.stepDown(f.uid)
                        }
                    }) { Text("Step down") }
                },
                dismissButton = { TextButton(onClick = { confirmStepDown = false }) { Text("Stay") } },
            )
        }
    }
}
