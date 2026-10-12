package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dev.pcoffline.meadow.BuildConfig
import dev.pcoffline.meadow.R
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    /** Write-only: when this phone last checked in. Nobody can read it but the backend. */
    fun heartbeat(uid: String) = db.collection("heartbeats").document(uid)

    /** One Bumblebee's own copies of the requests sent to them. */
    fun inbox(uid: String) = user(uid).collection("inbox")

    /**
     * Calls an HTTPS callable Cloud Function, turning its error into a readable message.
     * [timeoutSeconds] must cover the function's own timeout, or a slow call looks like being offline.
     */
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap(), timeoutSeconds: Long = DEFAULT_TIMEOUT_S): Map<String, Any?> {
        try {
            val callable = functions.getHttpsCallable(name).apply { setTimeout(timeoutSeconds, TimeUnit.SECONDS) }
            val result = callable.call(data).await()
            @Suppress("UNCHECKED_CAST")
            return (result.data as? Map<String, Any?>) ?: emptyMap()
        } catch (e: FirebaseFunctionsException) {
            val unreachable = e.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
                e.cause is IOException
            val text = backendText(e.code, e.message)
            // Not the backend's own words: a setup problem (e.g. a function missing in this region)
            // that the person can't fix, so keep the details for whoever runs the backend.
            if (text == null && !unreachable) Log.w(TAG, "Cloud Function $name failed with ${e.code}", e)
            throw CallFailed(text.orEmpty(), unreachable, e.code, e)
        } catch (e: IOException) {
            throw CallFailed("", unreachable = true, code = null, cause = e)
        }
    }

    /**
     * A failed Cloud Function call. Its message is the backend's friendly text (see copy.ts), or
     * empty when the failure came from elsewhere; [code] then says what kind of failure it was.
     * [unreachable] means the backend couldn't be reached at all.
     */
    class CallFailed(
        message: String,
        val unreachable: Boolean,
        val code: FirebaseFunctionsException.Code?,
        cause: Throwable?,
    ) : IllegalStateException(message, cause)

    companion object {
        /** Set once in backend/functions/.env, which the backend reads too. */
        const val FUNCTIONS_REGION = BuildConfig.FUNCTIONS_REGION
        private const val DEFAULT_TIMEOUT_S = 70L
        private const val TAG = "MeadowBackend"

        /**
         * The codes Meadow's backend throws, always with text written for people (see copy.ts).
         * Lazy: Code's own setup needs the Android runtime, which plain JVM tests of classes that
         * merely reference Backend don't have.
         */
        private val BACKEND_CODES by lazy {
            setOf(
                FirebaseFunctionsException.Code.ALREADY_EXISTS,
                FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                FirebaseFunctionsException.Code.NOT_FOUND,
                FirebaseFunctionsException.Code.PERMISSION_DENIED,
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
                FirebaseFunctionsException.Code.UNAUTHENTICATED,
                FirebaseFunctionsException.Code.UNAVAILABLE,
            )
        }

        /**
         * The backend's own message for a failed call, or null when the failure came from Firebase
         * itself. Those carry only the code's name ("NOT_FOUND" for a function that isn't deployed)
         * or an English diagnostic, neither fit to show.
         */
        internal fun backendText(code: FirebaseFunctionsException.Code, message: String?): String? =
            message?.trim()?.takeIf { code in BACKEND_CODES && it.isNotEmpty() && it != code.name }
    }
}
