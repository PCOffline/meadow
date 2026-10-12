@file:OptIn(ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.clickable
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.components.EmptyState
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.relativeTime
import dev.pcoffline.meadow.ui.requests.RequestCard
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(profile: UserProfile, nav: NavController) {
    val chats by remember(profile.uid) { AppGraph.chats.chats(profile.uid) }.collectAsStateWithLifecycle(null)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Chats", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
        Text(
            "Little notes with your circle",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        val list = chats
        if (list != null && list.isEmpty()) {
            EmptyState("🕊️", "No conversations yet", "A chat opens automatically with each Petal Pal and each friend you're a Petal Pal for.")
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(list.orEmpty(), key = { it.id }) { chat ->
                val unread = chat.isUnread(profile.uid)
                ListItem(
                    headlineContent = {
                        Text(chat.otherName(profile.uid), fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text(
                            chat.lastMessage.ifBlank { "Say hello 🌼" },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingContent = { FlowerAvatar(chat.otherFlower(profile.uid)) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(relativeTime(chat.lastMessageAtMillis), style = MaterialTheme.typography.labelSmall)
                            if (unread) {
                                Box(
                                    Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.animateItem().clickable { nav.navigate("chat/${chat.id}") }.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
fun ChatScreen(profile: UserProfile, chatId: String, nav: NavController) {
    val chat by remember(chatId) { AppGraph.chats.chat(chatId) }.collectAsStateWithLifecycle(null)
    val messages by remember(chatId) { AppGraph.chats.messages(chatId) }.collectAsStateWithLifecycle(emptyList())
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        AppGraph.chats.markRead(chatId)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        val title = chat?.let { "${it.otherFlower(profile.uid)} ${it.otherName(profile.uid)}" } ?: ""
        BackTopBar(title, onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (chat == null) {
                item {
                    EmptyState("🕊️", "This chat isn't open", "Chats open once someone becomes a Petal Pal.")
                }
            }
            items(messages, key = { it.id }) { message ->
                Box(Modifier.animateItem()) {
                when (message.kind) {
                    MessageKind.REQUEST -> message.requestId?.let { RequestMessage(it, profile.uid) }
                    MessageKind.SYSTEM -> SystemMessage(message)
                    MessageKind.TEXT -> Bubble(message, mine = message.senderId == profile.uid)
                }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(2000) },
                placeholder = { Text("Write something kind…") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = chat != null,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    val text = draft
                    draft = ""
                    scope.launch { runCatching { AppGraph.chats.send(chatId, text) } }
                },
                enabled = draft.isNotBlank() && chat != null,
            ) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send") }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, mine: Boolean) {
    val garden = Garden.colors
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
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else garden.cardTint)
                .padding(horizontal = 14.dp, vertical = 9.dp)
                .alpha(if (message.pending) 0.6f else 1f),
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (message.pending) "sending…" else relativeTime(message.createdAtMillis),
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

@Composable
private fun RequestMessage(requestId: String, myUid: String) {
    val request by remember(requestId) { AppGraph.requests.observe(requestId) }.collectAsStateWithLifecycle(null)
    request?.let { RequestCard(it, myUid, modifier = Modifier.padding(horizontal = 12.dp), compact = true) }
}
