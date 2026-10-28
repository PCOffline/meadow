package dev.pcoffline.meadow

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dev.pcoffline.meadow.EmulatorFriend.Companion.await
import dev.pcoffline.meadow.data.ChatMedia
import dev.pcoffline.meadow.data.ChatPicture
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.data.WRITE_TIMEOUT_MS
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End to end, with two people: the person on this phone becomes Sam's Bumblebee and chats with
 * Sam. A message written offline stays on the phone, counts as sent and reaches Sam once back
 * online. A photo goes up under the rules and is sent. When they part ways the chat closes:
 * nothing more can be written, but what was said stays readable.
 */
@RunWith(AndroidJUnit4::class)
class ChatJourneyTest : EndToEndTest() {
    private var sam: EmulatorFriend? = null

    @After
    fun closeSam() {
        sam?.close()
    }

    @Test
    fun aChatKeepsOfflineMessagesAndClosesWhenTheyPartWays() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }

        ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            val me = acceptInviteFrom(sam, "Sam")
            val chatId = ChatRepository.chatIdFor(me, sam.uid)
            sam.send(chatId, "Thanks for looking after me")

            // People shows Sam with the last message, and opens the chat.
            tab(R.string.tab_people).performClick()
            waitForText("Thanks for looking after me")
            compose.onNode(hasText("Sam") and hasClickAction()).performClick()
            waitForText(text(R.string.chat_placeholder))
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()

            // Sam's garden is a tap away from the chat, and leads back to it.
            openGardenOf("Sam")
            node(R.string.action_chat).performScrollTo().performClick()
            waitForText("Thanks for looking after me")

            // Offline, the message shows as sending and the box clears once it's queued.
            val db = FirebaseFirestore.getInstance()
            await(db.disableNetwork())
            val input = hasSetTextAction() and hasText(text(R.string.chat_placeholder))
            typeInto(input, "Written in a tunnel")
            compose.onNodeWithContentDescription(text(R.string.action_send)).assertIsEnabled().performClick()
            val bubble = hasText("Written in a tunnel") and !hasSetTextAction()
            waitShowing("the message sent offline", db, chatId) { compose.onAllNodes(bubble).fetchSemanticsNodes().isNotEmpty() }
            waitShowing("it marked as sending", db, chatId) { compose.onAllNodesWithText(text(R.string.chat_sending)).fetchSemanticsNodes().isNotEmpty() }
            waitShowing("the box cleared", db, chatId, WRITE_TIMEOUT_MS + 10_000) { compose.onAllNodes(input).fetchSemanticsNodes().isNotEmpty() }

            // Back online, it reaches the server as this person's message.
            await(db.enableNetwork())
            waitForBackendDocs("chats/$chatId/messages") { messages ->
                messages.any { it.string("text") == "Written in a tunnel" && it.string("senderId") == me }
            }
            waitShowing("it sent", db, chatId) { compose.onAllNodesWithText(text(R.string.chat_sending)).fetchSemanticsNodes().isEmpty() }

            // A photo goes the way the chat sends one picked (Android's own picker aside): noted,
            // uploaded under the rules, then sent, which crosses its note off.
            val photo = File(app.cacheDir, "e2e-photo.jpg").apply {
                outputStream().use { Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            val path = runBlocking {
                val media = AppGraph.chatMedia
                val picture = media.prepare(Uri.fromFile(photo))
                media.upload(me, chatId, picture).also {
                    AppGraph.chats.sendPicture(chatId, ChatPicture(it, picture.width, picture.height, picture.gif), text(R.string.chat_photo))
                }
            }
            waitForBackendDocs("chats/$chatId/messages") { messages -> messages.any { it.string("kind") == "image" && it.string("path") == path } }
            poll("the photo's note crossed off") { backendDoc("chatUploads/${Uri.encode(ChatMedia.noteIdFor(path))}") == null }

            // Stepping down (as from another of this person's phones) closes the open chat.
            runBlocking { AppGraph.backend.call("stepDown", mapOf("approveeUid" to sam.uid)) }
            waitFor(R.string.chat_closed_title)
            compose.onNodeWithContentDescription(text(R.string.action_send)).assertIsNotEnabled()
            compose.onNode(hasText(text(R.string.chat_placeholder))).assertIsNotEnabled()
            for (said in listOf("Thanks for looking after me", "Written in a tunnel")) {
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(said))
                waitForText(said)
            }
        }
    }

    /** Waits for [condition]; if it never holds, says what's on screen and in this phone's copy of the chat. */
    private fun waitShowing(what: String, db: FirebaseFirestore, chatId: String, timeout: Long = 20_000, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeout, condition)
        } catch (e: ComposeTimeoutException) {
            val onPhone = runCatching {
                await(db.collection("chats/$chatId/messages").get(Source.CACHE)).documents
                    .map { "${it.getString("text")} (pending=${it.metadata.hasPendingWrites()})" }
            }.getOrElse { "unreadable: $it" }
            throw AssertionError("Never saw $what. On screen: ${screenText()}. Messages on the phone: $onPhone", e)
        }
    }
}
