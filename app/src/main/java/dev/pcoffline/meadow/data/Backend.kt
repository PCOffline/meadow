package dev.pcoffline.meadow.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dev.pcoffline.meadow.R
import kotlinx.coroutines.tasks.await
import java.io.IOException

/** Thin access point to Firebase. Only touched once [dev.pcoffline.meadow.AppGraph.backendReady] is true. */
class Backend {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(FUNCTIONS_REGION) }

    val uid: String? get() = auth.currentUser?.uid

    fun requireUid(): String = uid ?: throw FriendlyException(R.string.error_sign_in_first)

    fun user(uid: String) = db.collection("users").document(uid)

    /** Owner-only document: who looks after whom, push tokens and preferences. */
    fun account(uid: String) = user(uid).collection("private").document("account")

    /** Calls an HTTPS callable Cloud Function, turning its error into a readable message. */
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        try {
            val result = functions.getHttpsCallable(name).call(data).await()
            @Suppress("UNCHECKED_CAST")
            return (result.getData() as? Map<String, Any?>) ?: emptyMap()
        } catch (e: FirebaseFunctionsException) {
            val unreachable = e.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
                e.cause is IOException
            throw CallFailed(e.message.orEmpty(), unreachable, e)
        } catch (e: IOException) {
            throw CallFailed("", unreachable = true, cause = e)
        }
    }

    /**
     * A failed Cloud Function call. Its message is the backend's friendly text (see copy.ts);
     * [unreachable] means the backend couldn't be reached at all.
     */
    class CallFailed(message: String, val unreachable: Boolean, cause: Throwable?) : IllegalStateException(message, cause)

    companion object {
        const val FUNCTIONS_REGION = "us-central1"
    }
}
