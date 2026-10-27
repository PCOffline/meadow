package dev.pcoffline.meadow.ui.screens

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.ChatPicture
import dev.pcoffline.meadow.data.MessageKind
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.chat.ChatContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Photos and GIFs in a chat: shown at their own shape, opened full screen, sent from the picker or the keyboard. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ChatPicturesTest : ScreenTest() {
    private fun picture(id: String, sender: String, path: String, gif: Boolean = false) = ChatMessage(
        id, sender, "", MessageKind.IMAGE, null, 1_000L, pending = false, picture = ChatPicture(path, 800, 600, gif),
    )

    @Test
    fun `a photo shows in the chat and opens full screen`() {
        val photo = File(app.cacheDir, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val messages = listOf(picture("p1", DemoGarden.maya.uid, "chats/c/maya/aaaaaaaa.jpg"))
        show { ChatContent(DemoGarden.robin, DemoGarden.chat, messages, pictureFile = { photo }) }
        val description = text(R.string.chat_picture_photo)
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        assertAccessible()

        compose.onNodeWithContentDescription(description).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        compose.onNodeWithContentDescription(text(R.string.action_close)).assertDoesNotExist()
    }

    @Test
    fun `a picture from a closed chat says it's gone, and one that couldn't load tries again on a tap`() {
        var tries = 0
        val messages = listOf(
            picture("p1", DemoGarden.maya.uid, "chats/c/maya/aaaaaaaa.jpg"),
            picture("p2", DemoGarden.robin.uid, "chats/c/robin/bbbbbbbb.gif", gif = true),
        )
        show {
            ChatContent(DemoGarden.robin, DemoGarden.chat, messages, pictureFile = { path ->
                if (path.endsWith(".jpg")) {
                    null
                } else {
                    tries++
                    error("offline")
                }
            })
        }
        node(R.string.chat_picture_gone).assertExists()
        node(R.string.chat_picture_failed).assertExists()
        assertAccessible()
        node(R.string.chat_picture_failed).performClick()
        compose.waitUntil(5_000) { tries == 2 }
    }

    @Test
    fun `pictures are sent from the keyboard or pasted, and the picker is at hand`() {
        val sent = mutableListOf<Uri>()
        // A GIF from the keyboard arrives the same way as a pasted picture.
        val gif = Uri.parse("content://keyboard.example/gifs/wave.gif")
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData(ClipDescription("GIF", arrayOf("image/gif")), ClipData.Item(gif)))
        show { ChatContent(DemoGarden.robin, DemoGarden.chat, DemoGarden.messages, onSendPicture = { sent += it }, requestCard = { _, _ -> }) }
        compose.onNodeWithContentDescription(text(R.string.chat_add_picture)).assertIsEnabled()

        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.PasteText)
        compose.waitForIdle()
        assertEquals(listOf(gif), sent)
        // It's sent as a picture, not pasted as text.
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().none { node ->
            node.config.getOrNull(SemanticsProperties.EditableText)?.text?.isNotEmpty() == true
        })
    }

    @Test
    fun `a closed chat can't be sent pictures`() {
        show { ChatContent(DemoGarden.robin, chat = null, messages = emptyList()) }
        compose.onNodeWithContentDescription(text(R.string.chat_add_picture)).assertIsNotEnabled()
    }
}
