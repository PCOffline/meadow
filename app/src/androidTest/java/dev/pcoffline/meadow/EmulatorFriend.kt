package dev.pcoffline.meadow

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dev.pcoffline.meadow.data.Backend
import dev.pcoffline.meadow.ui.legal.LegalDoc
import java.util.concurrent.TimeUnit

/**
 * Someone else on the Firebase emulators, played by the test: their own signed-in Firebase
 * instance, separate from the app's, calling the same functions and bound by the same rules.
 */
class EmulatorFriend private constructor(private val firebase: FirebaseApp, val uid: String, val email: String) {
    val db: FirebaseFirestore = FirebaseFirestore.getInstance(firebase)
    private val functions = FirebaseFunctions.getInstance(firebase, Backend.FUNCTIONS_REGION)

    /** Calls a Cloud Function as this person and returns its answer. */
    fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *>? =
        await(functions.getHttpsCallable(name).call(data)).data as? Map<*, *>

    /** Writes [text] in a chat, as the app does: the message and the chat's summary together. */
    fun send(chatId: String, text: String) {
        val chat = db.document("chats/$chatId")
        val batch = db.batch()
        batch.set(
            chat.collection("messages").document(),
            mapOf("senderId" to uid, "text" to text, "kind" to "text", "createdAt" to FieldValue.serverTimestamp()),
        )
        batch.update(chat, mapOf("lastMessage" to text, "lastMessageAt" to FieldValue.serverTimestamp(), "lastSenderId" to uid))
        await(batch.commit())
    }

    fun close() = firebase.delete()

    companion object {
        const val PASSWORD = "petals-and-bees"

        fun <T> await(task: Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)

        /**
         * Signs up a new person with an email (so they can invite Bumblebees) and, unless
         * [profile] is false, a profile that accepted the terms in [termsVersion].
         */
        fun create(
            context: Context,
            name: String,
            flower: String,
            profile: Boolean = true,
            termsVersion: Int = LegalDoc.CURRENT_VERSION,
        ): EmulatorFriend {
            val host = BuildConfig.EMULATOR_HOST
            val firebase = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "friend-$name-${System.nanoTime()}")
            FirebaseAuth.getInstance(firebase).useEmulator(host, 9099)
            FirebaseFirestore.getInstance(firebase).useEmulator(host, 8080)
            FirebaseFunctions.getInstance(firebase, Backend.FUNCTIONS_REGION).useEmulator(host, 5001)
            val email = "${name.lowercase()}-${System.currentTimeMillis()}@example.com"
            val user = await(FirebaseAuth.getInstance(firebase).createUserWithEmailAndPassword(email, PASSWORD)).user!!
            return EmulatorFriend(firebase, user.uid, email).also {
                if (profile) it.call("createProfile", mapOf("displayName" to name, "flower" to flower, "termsVersion" to termsVersion))
            }
        }
    }
}
