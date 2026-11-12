@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.circle

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
import java.text.Collator
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.LocalSnackbarClearance
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.Hint
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
 * waiting for an answer first, then Bumblebees and gardens (unread chats first, then by name),
 * each opening their chat, where requests, their garden and Bumblebee choices are too. The
 * section names say what each person is to me, so the cards don't repeat it. A plus button by
 * each section name adds someone to it.
 */
@Composable
fun PeopleContent(
    profile: UserProfile,
    people: People,
    onOpenChat: (String) -> Unit = {},
    onOpenBeeStats: () -> Unit = {},
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<AddSheet?>(null) }
    val chats = people.chats.associateBy { it.id }
    fun chatWith(uid: String) = ChatRepository.chatIdFor(profile.uid, uid)
    // News first, then by name, in each section.
    val collator = remember { Collator.getInstance() }
    fun List<UserProfile>.inOrder() = sortedWith(
        compareBy<UserProfile> { chats[chatWith(it.uid)]?.isUnread(profile.uid) != true }.thenBy(collator) { it.displayName },
    )
    // Sections show only with someone in them, judged by the profile too (it's already here), so
    // they don't flash in while friends load.
    val (offers, asks) = people.outgoingInvites.partition { it.offer }
    val hasBees = profile.approverIds.isNotEmpty() || people.bumblebees.isNotEmpty() || asks.isNotEmpty()
    val hasGardens = profile.approveeIds.isNotEmpty() || people.lookingAfter.isNotEmpty() || offers.isNotEmpty()
    val nobody = !hasBees && !hasGardens && people.incomingInvites.isEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TabTitle(stringResource(R.string.people_title)) }
            // With nobody yet, a first Bumblebee is the one thing to do here.
            if (nobody) item(key = "welcome") { WelcomeCard(Modifier.animateItem().padding(horizontal = 20.dp)) { adding = AddSheet.INVITE } }

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

            item {
                SectionTitle(stringResource(R.string.circle_your_bees), Modifier.padding(start = 20.dp, end = 8.dp)) {
                    AddButton(stringResource(R.string.people_invite_more)) { adding = AddSheet.INVITE }
                }
            }
            if (!hasBees) item { Hint(stringResource(R.string.people_bees_empty), Modifier.padding(horizontal = 20.dp)) }
            items(people.bumblebees.inOrder(), key = { "bee-" + it.uid }) { bee ->
                PersonRow(bee, chats[chatWith(bee.uid)], profile.uid, Modifier.animateItem(), leavingAt = profile.pendingRemovals[bee.uid]) {
                    onOpenChat(chatWith(bee.uid))
                }
            }
            items(asks, key = { it.id }) { OutgoingInvite(it, Modifier.animateItem()) }

            item {
                SectionTitle(stringResource(R.string.circle_looking_after), Modifier.padding(start = 20.dp, end = 8.dp)) {
                    AddButton(stringResource(R.string.circle_join_title)) { adding = AddSheet.JOIN }
                }
            }
            if (!hasGardens) item { Hint(stringResource(R.string.people_gardens_empty), Modifier.padding(horizontal = 20.dp)) }
            items(people.lookingAfter.inOrder(), key = { "garden-" + it.uid }) { friend ->
                PersonRow(friend, chats[chatWith(friend.uid)], profile.uid, Modifier.animateItem()) { onOpenChat(chatWith(friend.uid)) }
            }
            items(offers, key = { it.id }) { OutgoingInvite(it, Modifier.animateItem()) }
            // How I answer as a Bumblebee belongs with the gardens I answer for, under them.
            if (hasGardens) item(key = "bee-stats") { BeeStatsRow(Modifier.animateItem(), onOpenBeeStats) }
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
    adding?.let { sheet ->
        // Open all the way: the invite has a field to type in, which the keyboard would cover.
        ModalBottomSheet(onDismissRequest = { adding = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                when (sheet) {
                    AddSheet.INVITE -> InviteSheet(onDone = { adding = null })
                    AddSheet.JOIN -> JoinSheet(profile)
                }
            }
        }
    }
}

/** For someone with nobody yet: what a Bumblebee is, and a button that opens the invite. */
@Composable
private fun WelcomeCard(modifier: Modifier, onInvite: () -> Unit) {
    MeadowCard(modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.primaryContainer) {
        Text(stringResource(R.string.invite_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
        Text(stringResource(R.string.people_welcome_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        Button(onClick = onInvite, modifier = Modifier.padding(top = 14.dp).fillMaxWidth()) { Text(stringResource(R.string.people_invite_more)) }
    }
}

/** How I answer as a Bumblebee, under the gardens I answer for: a row with a chart at its start. */
@Composable
private fun BeeStatsRow(modifier: Modifier, onOpen: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.bee_stats_open), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What a section's plus button opens: inviting a Bumblebee, or my friend code to look after a garden. */
private enum class AddSheet { INVITE, JOIN }

/** A plus button at the end of a section name; [label] says what it adds, for screen readers. */
@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Outlined.Add, contentDescription = label, tint = MaterialTheme.colorScheme.primary) }
}

/**
 * Someone in my circle, opening our chat: their bio, if they wrote one, and a dot while the chat
 * has news. A Bumblebee [leavingAt] some time says so, with a way to keep them.
 */
@Composable
private fun PersonRow(
    person: UserProfile,
    chat: Chat?,
    myUid: String,
    modifier: Modifier,
    leavingAt: Long? = null,
    onOpen: () -> Unit,
) {
    val action = rememberAction()
    val kept = stringResource(R.string.circle_bee_kept, person.displayName)
    val unread = chat?.isUnread(myUid) == true
    PersonCard(
        avatar = person.avatar,
        name = person.displayName,
        about = if (leavingAt != null) {
            stringResource(R.string.circle_bee_leaving, durationText(leavingAt - System.currentTimeMillis()))
        } else {
            person.bio.ifBlank { null }
        },
        aboutIsWarning = leavingAt != null,
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
 * invite link works here too, for when one opened in the browser instead of in Meadow. Either
 * closes the sheet with [onDone]: a code invite then waits under Your Bumblebees, and a link opens.
 */
@Composable
private fun InviteSheet(onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var code by rememberSaveable { mutableStateOf("") }
    val linkAction = rememberAction()
    val codeAction = rememberAction()
    val shareTitle = stringResource(R.string.invite_share_title)
    val sent = stringResource(R.string.invite_code_sent)
    val pastedLink = InviteLinks.tokenFromText(code)
    val friendCode = code.uppercase().filter { it.isLetterOrDigit() }
    Column(Modifier.fillMaxWidth()) {
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
                        onDone()
                    } else {
                        codeAction.run(successMessage = sent, onSuccess = { code = ""; onDone() }) { AppGraph.social.invite(friendCode) }
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
private fun JoinSheet(profile: UserProfile) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val action = rememberAction()
    val copied = stringResource(R.string.join_code_copied)
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.circle_join_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading())
        Hint(stringResource(R.string.join_code_body), Modifier.padding(top = 4.dp, bottom = 12.dp))
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
 * about them if there is one, and [actions] at the end.
 */
@Composable
private fun PersonCard(
    avatar: Avatar,
    name: String,
    about: String?,
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
                if (about != null) Text(
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
