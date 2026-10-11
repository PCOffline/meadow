package dev.pcoffline.meadow.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(private val backend: Backend) {
    private val chats get() = backend.db.collection("chats")

    fun chats(uid: String): Flow<List<Chat>> =
        chats.whereArrayContains("participants", uid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .observe()
            .map { snapshot -> snapshot.documents.map { it.toChat() } }

    fun chat(chatId: String): Flow<Chat?> =
        chats.document(chatId).observe().map { if (it.exists()) it.toChat() else null }

    fun messages(chatId: String): Flow<List<ChatMessage>> =
        chats.document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(300)
            .observe()
            .map { snapshot -> snapshot.documents.map { it.toMessage() } }

    suspend fun send(chatId: String, text: String) {
        val uid = backend.requireUid()
        val body = text.trim()
        if (body.isEmpty()) return
        val chatRef = chats.document(chatId)
        val messageRef = chatRef.collection("messages").document()
        val batch = backend.db.batch()
        batch.set(
            messageRef,
            mapOf(
                "senderId" to uid,
                "text" to body,
                "kind" to "text",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            chatRef,
            mapOf(
                "lastMessage" to body.take(140),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastSenderId" to uid,
                "lastRead.$uid" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit()
    }

    suspend fun markRead(chatId: String) {
        val uid = backend.uid ?: return
        runCatching { chats.document(chatId).update("lastRead.$uid", FieldValue.serverTimestamp()).await() }
    }

    companion object {
        fun chatIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")
    }
}
