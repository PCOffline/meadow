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
            .map { snapshot -> listed(snapshot.documents.map { it.toChat() }) }

    /** Null when the chat is gone or closed. Its old messages stay readable. */
    fun chat(chatId: String): Flow<Chat?> =
        chats.document(chatId).observe().map { if (it.exists()) openOrNull(it.toChat()) else null }

    fun messages(chatId: String): Flow<List<ChatMessage>> =
        chats.document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(300)
            .observe()
            .map { snapshot -> snapshot.documents.map { it.toMessage() } }

    /** Sends [text]; throws if the server refuses it. Offline, it's queued and counts as sent ([awaitWrite]). */
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
        batch.update(chatRef, summaryOf(uid))
        batch.commit().awaitWrite()
    }

    /** Sends a picture already uploaded to [picture]'s path. Throws if the server refuses it. */
    suspend fun sendPicture(chatId: String, picture: ChatPicture) {
        val uid = backend.requireUid()
        val chatRef = chats.document(chatId)
        val batch = backend.db.batch()
        batch.set(
            chatRef.collection("messages").document(),
            mapOf(
                "senderId" to uid,
                "kind" to "image",
                "path" to picture.path,
                "width" to picture.width,
                "height" to picture.height,
                "gif" to picture.gif,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(chatRef, summaryOf(uid))
        batch.commit().awaitWrite()
    }

    /** Who sent the latest message and when, for the unread mark. Never its words. */
    private fun summaryOf(uid: String) = mapOf(
        "lastMessageAt" to FieldValue.serverTimestamp(),
        "lastSenderId" to uid,
        "lastRead.$uid" to FieldValue.serverTimestamp(),
    )

    suspend fun markRead(chatId: String) {
        val uid = backend.uid ?: return
        runCatchingCancellable { chats.document(chatId).update("lastRead.$uid", FieldValue.serverTimestamp()).await() }
    }

    companion object {
        fun chatIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

        /** Closed chats (the two parted ways) aren't listed. */
        fun listed(chats: List<Chat>): List<Chat> = chats.filterNot { it.closed }

        fun openOrNull(chat: Chat): Chat? = chat.takeUnless { it.closed }
    }
}
