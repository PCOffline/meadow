@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.pcoffline.meadow.ui.circle

import dev.pcoffline.meadow.data.Avatar
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.TabTitle
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.Invite
import dev.pcoffline.meadow.data.InviteLinks
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.stateWhileVisible
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull


class CircleViewModel(uid: String) : ViewModel() {
    private val profile = AppGraph.sync.profile.mapNotNull { (it as? ProfileState.Ready)?.profile }

    /** People who look after my garden. */
    val bumblebees: StateFlow<List<UserProfile>> = profile.map { it.approverIds }.distinctUntilChanged()
        .flatMapLatest { AppGraph.social.profiles(it) }
        .stateWhileVisible(this, emptyList())

    /** People whose gardens I look after. */
    val lookingAfter: StateFlow<List<UserProfile>> = profile.map { it.approveeIds }.distinctUntilChanged()
        .flatMapLatest { AppGraph.social.profiles(it) }
        .stateWhileVisible(this, emptyList())

    val incomingInvites: StateFlow<List<Invite>> = AppGraph.social.incomingInvites(uid).stateWhileVisible(this, emptyList())
    val outgoingInvites: StateFlow<List<Invite>> = AppGraph.social.outgoingInvites(uid).stateWhileVisible(this, emptyList())
}

@Composable
fun CircleScreen(
    profile: UserProfile,
    onOpenChat: (String) -> Unit,
    onOpenFriend: (String) -> Unit,
    viewModel: CircleViewModel = viewModel(key = "circle-${profile.uid}") { CircleViewModel(profile.uid) },
) {
    val bumblebees by viewModel.bumblebees.collectAsStateWithLifecycle()
    val lookingAfter by viewModel.lookingAfter.collectAsStateWithLifecycle()
    val incoming by viewModel.incomingInvites.collectAsStateWithLifecycle()
    val outgoing by viewModel.outgoingInvites.collectAsStateWithLifecycle()
    CircleContent(profile, bumblebees, lookingAfter, incoming, outgoing, onOpenChat, onOpenFriend)
}

/** The circle, drawn from plain state (also used for previews and screenshots). */
@Composable
fun CircleContent(
    profile: UserProfile,
    bumblebees: List<UserProfile>,
    lookingAfter: List<UserProfile>,
    incoming: List<Invite>,
    outgoing: List<Invite>,
    onOpenChat: (String) -> Unit = {},
    onOpenFriend: (String) -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TabTitle(stringResource(R.string.circle_title)) }
        item { InviteCard(Modifier.padding(horizontal = 20.dp)) }

        if (incoming.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.circle_invites_for_you), Modifier.padding(horizontal = 20.dp)) }
            items(incoming, key = { it.id }) { IncomingInvite(it, Modifier.animateItem()) }
        }

        // Sections show only with someone in them, judged by the profile too (it's already here),
        // so they don't flash in while friends load.
        if (profile.approverIds.isNotEmpty() || bumblebees.isNotEmpty() || outgoing.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.circle_your_bees), Modifier.padding(horizontal = 20.dp)) }
        }
        items(bumblebees, key = { "bee-" + it.uid }) { bee ->
            BumblebeeRow(bee, leavingAt = profile.pendingRemovals[bee.uid], modifier = Modifier.animateItem()) {
                onOpenChat(ChatRepository.chatIdFor(profile.uid, bee.uid))
            }
        }
        // Asks to be my Bumblebee wait among my Bumblebees; offers to look after a garden, among gardens.
        val (offers, asks) = outgoing.partition { it.offer }
        items(asks, key = { it.id }) { OutgoingInvite(it, Modifier.animateItem()) }

        if (profile.approveeIds.isNotEmpty() || lookingAfter.isNotEmpty() || offers.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.circle_looking_after), Modifier.padding(horizontal = 20.dp)) }
        }
        items(lookingAfter, key = { "garden-" + it.uid }) { friend ->
            GardenRow(friend, Modifier.animateItem(), onChat = { onOpenChat(ChatRepository.chatIdFor(profile.uid, friend.uid)) }) {
                onOpenFriend(friend.uid)
            }
        }
        items(offers, key = { it.id }) { OutgoingInvite(it, Modifier.animateItem()) }

        item { SectionTitle(stringResource(R.string.circle_join_title), Modifier.padding(horizontal = 20.dp)) }
        item { JoinCard(profile, Modifier.padding(horizontal = 20.dp)) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Asks someone to be my Bumblebee: by a link to send them, or by their friend code. A pasted
 * invite link works here too, for when one opened in the browser instead of in Meadow.
 */
@Composable
private fun InviteCard(modifier: Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var code by rememberSaveable { mutableStateOf("") }
    val linkAction = rememberAction()
    val codeAction = rememberAction()
    val shareTitle = stringResource(R.string.invite_share_title)
    val sent = stringResource(R.string.invite_code_sent)
    val pastedLink = InviteLinks.tokenFromText(code)
    val friendCode = code.uppercase().filter { it.isLetterOrDigit() }
    MeadowCard(modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.primaryContainer) {
        Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
        Button(
            onClick = {
                var url: String? = null
                linkAction.run(onSuccess = { url?.let { context.shareText(resources.getString(R.string.invite_share_text, it), shareTitle) } }) {
                    url = AppGraph.social.createInviteLink().url
                }
            },
            enabled = !linkAction.busy,
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
        ) { Text(stringResource(if (linkAction.busy) R.string.invite_link_busy else R.string.invite_link_button)) }
        ErrorNote(linkAction.error, Modifier.padding(top = 8.dp))

        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.invite_code_title), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(200) },
                placeholder = { Text(stringResource(R.string.invite_code_placeholder)) },
                singleLine = true,
                enabled = !codeAction.busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    if (pastedLink != null) {
                        AppGraph.store.setPendingInvite(pastedLink)
                        code = ""
                    } else {
                        codeAction.run(successMessage = sent, onSuccess = { code = "" }) { AppGraph.social.invite(friendCode) }
                    }
                },
                enabled = (pastedLink != null || friendCode.length in 4..8) && !codeAction.busy,
            ) { Text(stringResource(R.string.invite_code_button)) }
        }
        ErrorNote(codeAction.error, Modifier.padding(top = 8.dp))
    }
}

/** My friend code, for a friend who'd like me as their Bumblebee to invite me with. */
@Composable
private fun JoinCard(profile: UserProfile, modifier: Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val action = rememberAction()
    val copied = stringResource(R.string.join_code_copied)
    MeadowCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.join_code_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    profile.friendCode,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                )
            }
            IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.join_code_title), profile.friendCode))
                action.toast(copied)
            }) { Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.join_code_copy)) }
            IconButton(onClick = {
                context.shareText(resources.getString(R.string.join_code_share_text, profile.friendCode), resources.getString(R.string.join_code_share))
            }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.join_code_share)) }
        }
    }
}

@Composable
private fun IncomingInvite(invite: Invite, modifier: Modifier) {
    val action = rememberAction()
    val celebrate = LocalCelebrate.current
    val accepted = stringResource(if (invite.offer) R.string.circle_offer_accepted else R.string.circle_invite_accepted, invite.fromName)
    MeadowCard(modifier.padding(horizontal = 20.dp).fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowerAvatar(invite.fromFlower)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (invite.offer) R.string.circle_offer_title else R.string.circle_invite_title, invite.fromName),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        ErrorNote(action.error, Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { action.run(successMessage = accepted, onSuccess = celebrate) { AppGraph.social.respond(invite.id, true) } },
                enabled = !action.busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.circle_invite_accept)) }
            OutlinedButton(
                onClick = { action.run { AppGraph.social.respond(invite.id, false) } },
                enabled = !action.busy,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.circle_invite_decline)) }
        }
    }
}

@Composable
private fun OutgoingInvite(invite: Invite, modifier: Modifier) {
    val action = rememberAction()
    Row(modifier.padding(horizontal = 20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowerAvatar("✉️", tint = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(invite.toName, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (invite.offer) R.string.circle_offer_sent_at else R.string.circle_invite_sent_at, relativeTime(invite.createdAtMillis)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = { action.run { AppGraph.social.cancelInvite(invite.id) } }, enabled = !action.busy) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun BumblebeeRow(bee: UserProfile, leavingAt: Long?, modifier: Modifier, onChat: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val action = rememberAction()
    val removing = stringResource(R.string.circle_bee_removing, bee.displayName)
    val kept = stringResource(R.string.circle_bee_kept, bee.displayName)
    PersonCard(
        avatar = bee.avatar,
        name = bee.displayName,
        about = leavingAt?.let { stringResource(R.string.circle_bee_leaving, durationText(it - System.currentTimeMillis())) }
            ?: bee.bio.ifBlank { stringResource(R.string.circle_bee_role) },
        aboutIsWarning = leavingAt != null,
        modifier = modifier,
        error = action.error,
    ) {
        if (leavingAt != null) {
            TextButton(onClick = { action.run(successMessage = kept) { AppGraph.social.cancelRemoval(bee.uid) } }, enabled = !action.busy) {
                Text(stringResource(R.string.circle_bee_keep))
            }
        }
        IconButton(onClick = onChat) {
            Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = stringResource(R.string.circle_chat_with, bee.displayName))
        }
        // Removing is the menu's only choice, so while a removal is under way there's no menu:
        // it used to open empty.
        if (leavingAt == null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_remove)) }, onClick = { menu = false; confirm = true })
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.circle_remove_title, bee.displayName)) },
            text = { Text(stringResource(R.string.circle_remove_body_delayed, bee.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    action.run(successMessage = removing) { AppGraph.social.removeApprover(bee.uid) }
                }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

@Composable
private fun GardenRow(friend: UserProfile, modifier: Modifier, onChat: () -> Unit, onClick: () -> Unit) {
    // Only the name card: how their phone is set up is theirs to know.
    PersonCard(
        avatar = friend.avatar,
        name = friend.displayName,
        about = friend.bio.ifBlank { stringResource(R.string.circle_garden_role) },
        modifier = modifier,
        onClick = onClick,
    ) {
        IconButton(onClick = onChat) {
            Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = stringResource(R.string.circle_chat_with, friend.displayName))
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One person in the circle, the same for Bumblebees and gardens: their picture and name, one line
 * about them, and [actions] at the end.
 */
@Composable
private fun PersonCard(
    avatar: Avatar,
    name: String,
    about: String,
    modifier: Modifier,
    aboutIsWarning: Boolean = false,
    error: String? = null,
    onClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    MeadowCard(modifier.padding(horizontal = 20.dp).fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(avatar)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    about,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (aboutIsWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions()
        }
        ErrorNote(error, Modifier.padding(top = 8.dp))
    }
}

private fun Context.shareText(text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, title))
}
