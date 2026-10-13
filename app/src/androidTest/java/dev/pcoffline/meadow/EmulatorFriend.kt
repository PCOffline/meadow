package dev.pcoffline.meadow

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dev.pcoffline.meadow.data.Backend
import java.util.concurrent.TimeUnit

/**
 * Someone else on the Firebase emulators, played by the test: their own signed-in Firebase
 * instance, separate from the app's, calling the same functions and bound by the same rules.
 */
class EmulatorFriend private constructor(private val firebase: FirebaseApp, val uid: String) {
    val db: FirebaseFirestore = FirebaseFirestore.getInstance(firebase)
    private val functions = FirebaseFunctions.getInstance(firebase, Backend.FUNCTIONS_REGION)

    /** Calls a Cloud Function as this person and returns its answer. */
    fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *>? =
        await(functions.getHttpsCallable(name).call(data)).data as? Map<*, *>

    fun close() = firebase.delete()

    companion object {
        fun <T> await(task: Task<T>): T = Tasks.await(task, 30, TimeUnit.SECONDS)

        /** Signs up a new person with an email (so they can invite Bumblebees) and a profile. */
        fun create(context: Context, name: String, flower: String): EmulatorFriend {
            val host = BuildConfig.EMULATOR_HOST
            val firebase = FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, "friend-$name-${System.nanoTime()}")
            FirebaseAuth.getInstance(firebase).useEmulator(host, 9099)
            FirebaseFirestore.getInstance(firebase).useEmulator(host, 8080)
            FirebaseFunctions.getInstance(firebase, Backend.FUNCTIONS_REGION).useEmulator(host, 5001)
            val email = "${name.lowercase()}-${System.currentTimeMillis()}@example.com"
            val user = await(FirebaseAuth.getInstance(firebase).createUserWithEmailAndPassword(email, "petals-and-bees")).user!!
            return EmulatorFriend(firebase, user.uid).also {
                it.call("createProfile", mapOf("displayName" to name, "flower" to flower, "termsVersion" to 1))
            }
        }
    }
}
