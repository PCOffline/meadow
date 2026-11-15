package dev.pcoffline.meadow.pictures.stories

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import org.junit.Test

/** A chat opens at what's new, and offers the way down to a message that comes while you read back. */
class ChatNewMessagesStory : Story("A chat opens at its new messages") {
    private fun text(id: Int) = compose.activity.getString(id)
    private fun message(i: Int) =
        ChatMessage("m$i", DemoGarden.maya.uid, "Message $i from Maya", MessageKind.TEXT, null, i * 60_000L, pending = false)

    @Test
    fun story() {
        val messages = mutableStateOf((1..30).map(::message))
        // Robin read up to message 26.
        val chat = DemoGarden.chat.copy(lastReadMillis = mapOf(DemoGarden.robin.uid to 26 * 60_000L + 1))
        show(firstCaption = "The chat opens at the first new message, under a New line.") {
            ChatContent(DemoGarden.robin, chat, messages.value)
        }
        step("Reading back, Robin is at older messages.") {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Message 5 from Maya"))
        }
        step("A new message comes. The list stays put, and a New message button shows.") {
            messages.value = (1..31).map(::message)
        }
        step("The button goes down to the latest message, and hides.") {
            compose.onNodeWithContentDescription(text(R.string.chat_new_message_spoken)).performClick()
        }
    }
}
