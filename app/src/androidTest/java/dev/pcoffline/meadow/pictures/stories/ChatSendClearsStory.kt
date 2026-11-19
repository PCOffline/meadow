package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import dev.pcoffline.meadow.ui.requests.RequestCard
import org.junit.Test

/** The box clears as soon as a message is sent. A message that fails comes back to it. */
class ChatSendClearsStory : Story("A sent message leaves the box at once") {
    private var messages by mutableStateOf(DemoGarden.messages)
    private var error by mutableStateOf<String?>(null)
    private var fail: () -> Unit = {}

    private fun send() = compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_send)).performClick()

    @Test
    fun story() {
        show(firstCaption = "Robin opens the chat with Maya.") {
            Box(Modifier.statusBarsPadding()) {
                ChatContent(DemoGarden.robin, DemoGarden.chat, messages, error = error, onSend = { text, failed ->
                    // Each message shows as sending, and stays so.
                    messages = messages + ChatMessage("s${messages.size}", DemoGarden.robin.uid, text, MessageKind.TEXT, null, 0L, pending = true)
                    fail = failed
                }, requestCard = { _, _ ->
                    RequestCard(DemoGarden.request, DemoGarden.robin.uid, Modifier.padding(horizontal = 12.dp))
                })
            }
        }
        step("Robin types a message.") { compose.onNode(hasSetTextAction()).performTextInput("See you tomorrow") }
        step("Robin sends it. The box is empty at once, while the message is still sending.") { send() }
        step("Robin types the next message straight away. Only the new text is in the box.") {
            compose.onNode(hasSetTextAction()).performTextInput("At the park?")
        }
        step("The first message fails to send. It comes back above the new text, with the reason.") {
            compose.runOnIdle {
                messages = messages.filterNot { it.text == "See you tomorrow" }
                error = compose.activity.getString(R.string.error_offline)
                fail()
            }
        }
    }
}
