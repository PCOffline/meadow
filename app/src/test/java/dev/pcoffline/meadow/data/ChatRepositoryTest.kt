package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A chat between a person and their Bumblebee, against the emulators: messages, cards, read marks and parting ways. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ChatRepositoryTest : EmulatorTest() {
    private val chats by lazy { ChatRepository(backend) }

    @Test
    fun `a Bumblebee gets a chat, and both read what the other sends`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        val chatId = ChatRepository.chatIdFor(robin.uid, bee.uid)

        onBackend {
            val chat = chats.chats(bee.uid).firstWhere { it.isNotEmpty() }.single()
            assertEquals(chatId, chat.id)
            assertEquals(mapOf(robin.uid to "Robin", bee.uid to "Bee"), chat.names)
            // Blank words send nothing; the rest are trimmed.
            chats.send(chatId, "   ")
            chats.send(chatId, "  Hi Robin  ")
        }

        signInAs(robin)
        onBackend {
            val words = chats.messages(chatId).firstWhere { list -> list.any { it.text == "Hi Robin" } }
            val hi = words.single { it.text == "Hi Robin" }
            assertEquals(bee.uid, hi.senderId)
            assertEquals(MessageKind.TEXT, hi.kind)
            assertEquals(bee.uid, chats.chat(chatId).firstWhere { it?.lastSenderId == bee.uid }?.lastSenderId)

            chats.markRead(chatId)
            val read = chats.chat(chatId).firstWhere { (it?.lastReadMillis?.get(robin.uid) ?: 0) > 0 }
            assertTrue(read!!.lastReadMillis.getValue(robin.uid) >= hi.createdAtMillis)
        }
    }

    @Test
    fun `a limit card and a picture arrive as what they are`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        val chatId = ChatRepository.chatIdFor(robin.uid, bee.uid)
        val limit = AppLimit(APP, "Pebbles", dailyMinutes = 30, weeklyMinutes = 0)
        val picture = ChatPicture("chats/$chatId/${bee.uid}/abcdefgh12.jpg", width = 640, height = 480, gif = false)

        onBackend {
            chats.sendLimit(chatId, limit, LimitCardMode.SUGGESTED, "Suggested a limit for Pebbles")
            chats.sendPicture(chatId, picture)
            // Bee's chat also holds the backend's note that they became a Bumblebee: wait for each kind.
            val messages = chats.messages(chatId).firstWhere { list ->
                listOf(MessageKind.LIMIT, MessageKind.IMAGE).all { kind -> list.any { it.kind == kind && !it.pending } }
            }
            val card = messages.single { it.kind == MessageKind.LIMIT }
            assertEquals(LimitCardMode.SUGGESTED, card.limitMode)
            assertEquals(30, card.limit?.dailyMinutes)
            assertEquals(picture, messages.single { it.kind == MessageKind.IMAGE }.picture)
        }
    }

    @Test
    fun `a card without a limit is refused`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        val chatId = ChatRepository.chatIdFor(robin.uid, bee.uid)
        assertThrows(Exception::class.java) {
            onBackend { chats.sendLimit(chatId, AppLimit(APP, "Pebbles", 0, 0), LimitCardMode.SHARED, "Shared a limit for Pebbles") }
        }
    }

    @Test
    fun `when the two part ways, the chat is no longer listed and takes no messages`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        val chatId = ChatRepository.chatIdFor(robin.uid, bee.uid)
        onBackend {
            chats.chats(bee.uid).firstWhere { it.isNotEmpty() }
            SocialRepository(backend).stepDown(robin.uid)
            chats.chats(bee.uid).firstWhere { it.isEmpty() }
            assertNull(chats.chat(chatId).firstWhere { it == null })
        }
        assertThrows(Exception::class.java) { onBackend { chats.send(chatId, "Still there?") } }
    }

    @Test
    fun `nobody else can read the chat`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        val chatId = ChatRepository.chatIdFor(robin.uid, bee.uid)
        person("Stranger")
        assertThrows(FirebaseFirestoreException::class.java) {
            onBackend { backend.db.collection("chats").document(chatId).collection("messages").get().await() }
        }
        // A read mark the rules refuse is dropped quietly.
        onBackend { chats.markRead(chatId) }
    }

    private companion object {
        const val APP = "com.example.pebbles"
    }
}
