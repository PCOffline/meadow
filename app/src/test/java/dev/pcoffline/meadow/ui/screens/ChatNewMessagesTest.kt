package dev.pcoffline.meadow.ui.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A long chat opens at the first message I haven't read, under a "New" line. A message that
 * comes while I read back doesn't move me: a button offers the way down to it.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ChatNewMessagesTest : ScreenTest() {
    private val robin = DemoGarden.robin
    private val maya = DemoGarden.maya
    private val now = System.currentTimeMillis()

    private fun message(i: Int, from: String = if (i % 2 == 0) maya.uid else robin.uid) =
        ChatMessage("m$i", from, "Message $i", MessageKind.TEXT, null, now - (100 - i) * 60_000L, pending = false)

    /** Robin's chat with Maya, read up to [readUpTo] messages in. */
    private fun chatReadUpTo(readUpTo: Int) = DemoGarden.chat.copy(lastReadMillis = mapOf(robin.uid to now - (100 - readUpTo) * 60_000L + 1))

    private fun shows(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    /** The "New" line, as a screen reader says it. */
    private fun newLine() = compose.onAllNodes(hasContentDescription(text(R.string.chat_new_line_spoken))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a long chat opens at the first new message, under a New line`() {
        var seen = 0
        show { ChatContent(robin, chatReadUpTo(20), (1..60).map { message(it) }, onSeen = { seen++ }, requestCard = { _, _ -> Text("") }) }
        compose.waitForIdle()
        assertTrue(newLine())
        // The first new message from Maya is in view, and the latest isn't.
        assertTrue(shows("Message 22"))
        assertTrue(!shows("Message 60"))
        assertTrue(seen >= 1)
    }

    @Test
    fun `a chat read to the end opens at the latest message, with no New line`() {
        show { ChatContent(robin, chatReadUpTo(60), (1..60).map { message(it) }, requestCard = { _, _ -> Text("") }) }
        compose.waitForIdle()
        assertTrue(shows("Message 60"))
        assertTrue(!newLine())
    }

    @Test
    fun `a message that comes while reading back waits below, and the button goes down to it`() {
        var messages by mutableStateOf((1..60).map { message(it) })
        show { ChatContent(robin, chatReadUpTo(20), messages, requestCard = { _, _ -> Text("") }) }
        compose.waitForIdle()
        messages = messages + message(61, from = maya.uid)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(text(R.string.chat_new_message_spoken)).performClick()
        compose.waitForIdle()
        assertTrue(shows("Message 61"))
        assertEquals(0, compose.onAllNodes(hasText(text(R.string.chat_new_message))).fetchSemanticsNodes().size)
    }

    @Test
    fun `my own message brings the chat down to it`() {
        var messages by mutableStateOf((1..60).map { message(it) })
        show { ChatContent(robin, chatReadUpTo(20), messages, requestCard = { _, _ -> Text("") }) }
        compose.waitForIdle()
        messages = messages + message(61, from = robin.uid)
        compose.waitForIdle()
        compose.waitUntil(5_000) { shows("Message 61") }
        compose.onNodeWithText("Message 61").assertExists()
    }
}
