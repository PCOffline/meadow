package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Test

/** A chat opens at its latest message, and stays there while what's above it loads. */
class ChatOpensAtLatestStory : Story("A chat opens at its latest message") {
    private var loaded by mutableStateOf(false)

    @Test
    fun story() {
        val maya = DemoGarden.maya.uid
        val older = (1..30).map { i -> ChatMessage("o$i", if (i % 2 == 0) maya else DemoGarden.robin.uid, "An older message, $i", MessageKind.TEXT, null, i * 1_000L, pending = false) }
        val messages = older + DemoGarden.messages
        show(firstCaption = "The chat opens at its latest message, \"Thank you! 💛\".") {
            Box(Modifier.statusBarsPadding()) {
                ChatContent(DemoGarden.robin, DemoGarden.chat, messages, requestCard = { _, _ ->
                    // The request card loads after the chat opens, and grows as it does.
                    if (loaded) {
                        RequestCard(DemoGarden.request, DemoGarden.robin.uid, Modifier.padding(horizontal = 12.dp))
                    } else {
                        Box(Modifier.fillMaxWidth().height(24.dp))
                    }
                })
            }
        }
        step("The request card above loads and grows. The latest message stays in view.") { loaded = true }
    }
}
