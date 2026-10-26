package dev.pcoffline.meadow.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Chat
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.components.TabTitle
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.stateWhileVisible
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(uid: String) : ViewModel() {
    /** Null while loading. */
    val chats: StateFlow<List<Chat>?> = AppGraph.chats.chats(uid).stateWhileVisible(this, null)
}

@Composable
fun ChatListScreen(
    profile: UserProfile,
    onOpenChat: (String) -> Unit,
    viewModel: ChatListViewModel = viewModel(key = "chats-${profile.uid}") { ChatListViewModel(profile.uid) },
) {
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.chats_title))
        Text(
            stringResource(R.string.chats_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val list = chats
        if (list != null && list.isEmpty()) {
            EmptyState("🕊️", stringResource(R.string.chats_empty_title), stringResource(R.string.chats_empty_body))
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(list.orEmpty(), key = { it.id }) { chat ->
                ChatRow(chat, profile.uid, onOpen = { onOpenChat(chat.id) }, Modifier.animateItem())
            }
        }
    }
}

/** One conversation in the list; an unread one is bolder and says so to a screen reader too. */
@Composable
internal fun ChatRow(chat: Chat, uid: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val unread = chat.isUnread(uid)
    ListItem(
        headlineContent = {
            Text(chat.otherName(uid), fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
        },
        supportingContent = {
            Text(
                chat.lastMessage.ifBlank { stringResource(R.string.chats_say_hello) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        leadingContent = { PersonAvatar(chat.otherAvatar(uid)) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(relativeTime(chat.lastMessageAtMillis), style = MaterialTheme.typography.labelSmall)
                if (unread) {
                    val unreadLabel = stringResource(R.string.chats_unread)
                    Box(
                        Modifier.semantics { contentDescription = unreadLabel }
                            .padding(top = 4.dp).size(10.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onOpen).padding(horizontal = 8.dp),
    )
}

/** A chat once loaded; [chat] is null when it's closed (the friendship ended). */
data class LoadedChat(val chat: Chat?)

class ChatViewModel(private val chatId: String) : ViewModel() {
    /** Null until the chat has loaded, so loading isn't mistaken for a closed chat. */
    val chat: StateFlow<LoadedChat?> = AppGraph.chats.chat(chatId).map { LoadedChat(it) }.stateWhileVisible(this, null)
    val messages: StateFlow<List<ChatMessage>> = AppGraph.chats.messages(chatId).stateWhileVisible(this, emptyList())

    suspend fun send(text: String) = AppGraph.chats.send(chatId, text)

    fun markRead() {
        viewModelScope.launch { AppGraph.chats.markRead(chatId) }
    }
}

@Composable
fun ChatScreen(
    profile: UserProfile,
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(key = "chat-$chatId") { ChatViewModel(chatId) },
) {
    val loaded by viewModel.chat.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val sending = rememberAction()
    ChatContent(
        profile, loaded?.chat, messages, loading = loaded == null,
        sending = sending.busy,
        error = sending.error,
        onSend = { text, sent -> sending.run(onSuccess = sent) { viewModel.send(text) } },
        onSeen = viewModel::markRead,
        onBack = onBack,
    )
}

/**
 * A conversation, drawn from plain state (also used for previews and screenshots).
 * [onSend] gets the draft and a callback to run once it's sent, which clears the draft; until then
 * (and if sending fails, shown as [error]) the draft stays. Sending is off while [sending].
 * [onSeen] runs when it opens and whenever a new message arrives. [requestCard] shows a request
 * message (with whether you asked it); by default it follows the live request.
 */
@Composable
fun ChatContent(
    profile: UserProfile,
    chat: Chat?,
    messages: List<ChatMessage>,
    loading: Boolean = false,
    sending: Boolean = false,
    error: String? = null,
    onSend: (text: String, sent: () -> Unit) -> Unit = { _, sent -> sent() },
    onSeen: () -> Unit = {},
    onBack: () -> Unit = {},
    requestCard: @Composable (requestId: String, mine: Boolean) -> Unit = { id, mine -> RequestMessage(id, profile.uid, mine) },
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keyed on the newest message, not the count: only the latest 300 are loaded, so once a
    // chat has that many a new message leaves the count as it was.
    val latestId = messages.lastOrNull()?.id
    LaunchedEffect(latestId) { onSeen() }

    // Opens at the latest message straight away. Later ones scroll into view only if you're
    // already at the bottom or sent it yourself, so reading back isn't interrupted.
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(latestId) {
        if (messages.isEmpty()) return@LaunchedEffect
        val last = messages.size - 1 + if (chat == null && !loading) 1 else 0
        if (!opened) {
            listState.scrollToItem(last)
            opened = true
            return@LaunchedEffect
        }
        val seen = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: last
        if (seen >= last - 2 || messages.last().senderId == profile.uid) listState.animateScrollToItem(last)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val title = chat?.otherName(profile.uid).orEmpty()
        BackTopBar(title, onBack = onBack)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (chat == null && !loading) {
                item { EmptyState("🕊️", stringResource(R.string.chat_closed_title), stringResource(R.string.chat_closed_body)) }
            }
            items(messages, key = { it.id }) { message ->
                Box(Modifier.animateItem()) {
                    when (message.kind) {
                        MessageKind.REQUEST -> message.requestId?.let { requestCard(it, message.senderId == profile.uid) }
                        MessageKind.SYSTEM -> SystemMessage(message)
                        MessageKind.TEXT -> Bubble(message, mine = message.senderId == profile.uid)
                    }
                }
            }
        }
        ErrorNote(error, Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(2000) },
                placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                enabled = chat != null || loading,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val text = draft
                    // Anything typed while it was sending stays.
                    onSend(text) { if (draft == text) draft = "" }
                },
                enabled = draft.isNotBlank() && chat != null && !sending,
            ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.action_send)) }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, mine: Boolean) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp, topEnd = 20.dp,
                        bottomStart = if (mine) 20.dp else 6.dp,
                        bottomEnd = if (mine) 6.dp else 20.dp,
                    ),
                )
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else Garden.colors.cardTint)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .alpha(if (message.pending) 0.6f else 1f),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (message.pending) stringResource(R.string.chat_sending) else relativeTime(message.createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun SystemMessage(message: ChatMessage) {
    Text(
        message.text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
    )
}

class RequestMessageViewModel(requestId: String, myUid: String, mine: Boolean) : ViewModel() {
    val request: StateFlow<LimitRequest?> =
        (if (mine) AppGraph.requests.observe(requestId) else AppGraph.requests.observeInbox(myUid, requestId))
            .stateWhileVisible(this, null)
}

@Composable
private fun RequestMessage(requestId: String, myUid: String, mine: Boolean) {
    val viewModel = viewModel(key = "request-$requestId") { RequestMessageViewModel(requestId, myUid, mine) }
    val request by viewModel.request.collectAsStateWithLifecycle()
    request?.let { RequestCard(it, myUid, modifier = Modifier.padding(horizontal = 12.dp), compact = true) }
}
