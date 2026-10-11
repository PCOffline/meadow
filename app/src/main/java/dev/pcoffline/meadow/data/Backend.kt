package dev.pcoffline.meadow.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/** Thin access point to Firebase. Only touched once [dev.pcoffline.meadow.AppGraph.backendReady] is true. */
class Backend {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance(FUNCTIONS_REGION) }

    val uid: String? get() = auth.currentUser?.uid

    fun requireUid(): String = uid ?: throw IllegalStateException("Please sign in first.")

    fun user(uid: String) = db.collection("users").document(uid)

    /** Calls an HTTPS callable Cloud Function, turning its error into a readable message. */
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        try {
            val result = functions.getHttpsCallable(name).call(data).await()
            @Suppress("UNCHECKED_CAST")
            return (result.getData() as? Map<String, Any?>) ?: emptyMap()
        } catch (e: FirebaseFunctionsException) {
            throw IllegalStateException(e.message ?: "Something went wrong (${e.code}).", e)
        }
    }

    companion object {
        const val FUNCTIONS_REGION = "us-central1"
    }
}
