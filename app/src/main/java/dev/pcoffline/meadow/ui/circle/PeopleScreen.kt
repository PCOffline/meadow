@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.pcoffline.meadow.ui.circle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Avatar
import dev.pcoffline.meadow.data.Chat
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.Invite
import dev.pcoffline.meadow.data.InviteLinks
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.LocalSnackbarClearance
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.components.TabTitle
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.ui.durationText
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.requests.AskForTimeDialog
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.stateWhileVisible
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

class PeopleViewModel(uid: String) : ViewModel() {
    private val profile = AppGraph.sync.profile.mapNotNull { (it as? ProfileState.Ready)?.profile }

    /** People who look after my garden. */
    private val bumblebees = profile.map { it.approverIds }.distinctUntilChanged().flatMapLatest { AppGraph.social.profiles(it) }

    /** People whose gardens I look after. */
    private val lookingAfter = profile.map { it.approveeIds }.distinctUntilChanged().flatMapLatest { AppGraph.social.profiles(it) }

    private val invites = combine(AppGraph.social.incomingInvites(uid), AppGraph.social.outgoingInvites(uid)) { i, o -> i to o }

    /** Only what's still waiting: answered requests live on in their chats. */
    private val requests = combine(AppGraph.requests.incoming(uid), AppGraph.requests.outgoing(uid)) { i, o ->
        i.filter { it.status == RequestStatus.PENDING } to o.filter { it.status == RequestStatus.PENDING }
    }

    val people: StateFlow<People> = combine(bumblebees, lookingAfter, invites, AppGraph.chats.chats(uid), requests) { bees, gardens, (i, o), chats, (ri, ro) ->
        People(bees, gardens, i, o, chats, ri, ro)
    }.stateWhileVisible(this, People())
}

/**
 * Everyone in my circle and what's going on with them: [incomingRequests] and [outgoingRequests]
 * are the ones still waiting for an answer.
 */
data class People(
    val bumblebees: List<UserProfile> = emptyList(),
    val lookingAfter: List<UserProfile> = emptyList(),
    val incomingInvites: List<Invite> = emptyList(),
    val outgoingInvites: List<Invite> = emptyList(),
    val chats: List<Chat> = emptyList(),
    val incomingRequests: List<LimitRequest> = emptyList(),
    val outgoingRequests: List<LimitRequest> = emptyList(),
)

@Composable
fun PeopleScreen(
    profile: UserProfile,
    onOpenChat: (String) -> Unit,
    onOpenBeeStats: () -> Unit,
    viewModel: PeopleViewModel = viewModel(key = "people-${profile.uid}") { PeopleViewModel(profile.uid) },
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    PeopleContent(profile, people, onOpenChat, onOpenBeeStats)
}

/**
 * Everyone in one place, drawn from plain state (also used for tests and screenshots): what's
 * waiting for an answer first, then Bumblebees and gardens, each opening their chat, where
 * requests, their garden and Bumblebee choices are too.
 */
@Composable
fun PeopleContent(
    profile: UserProfile,
    people: People,
    onOpenChat: (String) -> Unit = {},
    onOpenBeeStats: () -> Unit = {},
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val chats = people.chats.associateBy { it.id }
    fun chatWith(uid: String) = ChatRepository.chatIdFor(profile.uid, uid)
    // Sections show only with someone in them, judged by the profile too (it's already here), so
    // they don't flash in while friends load.
    val (offers, asks) = people.outgoingInvites.partition { it.offer }
    val hasBees = profile.approverIds.isNotEmpty() || people.bumblebees.isNotEmpty() || asks.isNotEmpty()
    val hasGardens = profile.approveeIds.isNotEmpty() || people.lookingAfter.isNotEmpty() || offers.isNotEmpty()
    val nobody = !hasBees && !hasGardens && people.incomingInvites.isEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TabTitle(stringResource(R.string.people_title)) }
            // With nobody yet, inviting is the one thing to do here.
            if (nobody) item { InviteCard(Modifier.padding(horizontal = 20.dp)) }

            if (people.incomingInvites.isNotEmpty() || people.incomingRequests.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.people_waiting), Modifier.padding(horizontal = 20.dp)) }
                items(people.incomingRequests, key = { "ask-" + it.id }) {
                    RequestCard(it, myUid = profile.uid, modifier = Modifier.animateItem().padding(horizontal = 20.dp))
                }
                items(people.incomingInvites, key = { it.id }) { IncomingInvite(it, Modifier.animateItem()) }
            }
            if (people.outgoingRequests.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.people_your_asks), Modifier.padding(horizontal = 20.dp)) }
                items(people.outgoingRequests, key = { "mine-" + it.id }) {
                    RequestCard(it, myUid = profile.uid, modifier = Modifier.animateItem().padding(horizontal = 20.dp), compact = true)
                }
            }

            if (hasBees) item { SectionTitle(stringResource(R.string.circle_your_bees), Modifier.padding(horizontal = 20.dp)) }
            items(people.bumblebees, key = { "bee-" + it.uid }) { bee ->
                PersonRow(
                    bee, chats[chatWith(bee.uid)], profile.uid, Modifier.animateItem(),
                    role = stringResource(R.string.circle_bee_role),
                    leavingAt = profile.pendingRemovals[bee.uid],
                ) { onOpenChat(chatWith(bee.uid)) }
            }
            items(asks, key = { it.id }) { OutgoingInvite(it, Modifier.animateItem()) }

            if (hasGardens) {
                item {
                    SectionTitle(stringResource(R.string.circle_looking_after), Modifier.padding(start = 20.dp, end = 8.dp)) {
                        TextButton(onClick = onOpenBeeStats) { Text(stringResource(R.string.bee_stats_open)) }
                    }
                }
            }
            items(people.lookingAfter, key = { "garden-" + it.uid }) { friend ->
                PersonRow(friend, chats[chatWith(friend.uid)], profile.uid, Modifier.animateItem(), role = stringResource(R.string.circle_garden_role)) {
                    onOpenChat(chatWith(friend.uid))
                }
            }
            items(offers, key = { it.id }) { OutgoingInvite(it, Modifier.animateItem()) }

            if (!nobody) {
                item { SectionTitle(stringResource(R.string.people_invite_more), Modifier.padding(horizontal = 20.dp)) }
                item { InviteCard(Modifier.padding(horizontal = 20.dp)) }
            }
            item { SectionTitle(stringResource(R.string.circle_join_title), Modifier.padding(horizontal = 20.dp)) }
            item { JoinCard(profile, Modifier.padding(horizontal = 20.dp)) }
            item { Spacer(Modifier.height(if (profile.approverIds.isNotEmpty()) 88.dp else 24.dp)) }
        }
        // Asking for time is the one thing to do with Bumblebees from here; their chats hold the rest.
        if (profile.approverIds.isNotEmpty()) {
            val label = stringResource(R.string.requests_ask_button)
            // Snackbars sit above the button rather than over it (they'd take its taps), only while it's here.
            val clearance = LocalSnackbarClearance.current
            val density = LocalDensity.current
            DisposableEffect(clearance) { onDispose { clearance.height = 0.dp } }
            ExtendedFloatingActionButton(
                onClick = { asking = true },
                icon = { Icon(Icons.Outlined.MoreTime, contentDescription = null) },
                text = { Text(label) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                // Material keeps the label's text from screen readers, so the button names itself.
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .onSizeChanged { clearance.height = with(density) { it.height.toDp() } + 20.dp }
                    .semantics { contentDescription = label },
            )
        }
    }
    if (asking) AskForTimeDialog(packageName = null, onDismiss = { asking = false })
}

/**
 * Someone in my circle, opening our chat: the latest message (bolder while unread), or failing
 * that what they are to me. A Bumblebee [leavingAt] some time says so, with a way to keep them.
 */
@Composable
private fun PersonRow(
    person: UserProfile,
    chat: Chat?,
    myUid: String,
    modifier: Modifier,
    role: String,
    leavingAt: Long? = null,
    onOpen: () -> Unit,
) {
    val action = rememberAction()
    val kept = stringResource(R.string.circle_bee_kept, person.displayName)
    val unread = chat?.isUnread(myUid) == true
    val last = chat?.lastMessage?.takeIf { it.isNotBlank() }
    PersonCard(
        avatar = person.avatar,
        name = person.displayName,
        about = when {
            leavingAt != null -> stringResource(R.string.circle_bee_leaving, durationText(leavingAt - System.currentTimeMillis()))
            last != null -> last
            else -> person.bio.ifBlank { role }
        },
        aboutIsWarning = leavingAt != null,
        aboutIsNew = unread && leavingAt == null,
        modifier = modifier,
        error = action.error,
        onClick = onOpen,
    ) {
        if (leavingAt != null) {
            TextButton(onClick = { action.run(successMessage = kept) { AppGraph.social.cancelRemoval(person.uid) } }, enabled = !action.busy) {
                Text(stringResource(R.string.circle_bee_keep))
            }
        }
        if (unread) {
            val unreadLabel = stringResource(R.string.chats_unread)
            Box(
                Modifier.semantics { contentDescription = unreadLabel }
                    .padding(horizontal = 6.dp).size(10.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    aboutIsNew: Boolean = false,
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
                    fontWeight = if (aboutIsNew) FontWeight.SemiBold else null,
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
