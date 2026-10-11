package dev.pcoffline.meadow.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class AuthRepository(private val backend: Backend) {

    val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        backend.auth.addAuthStateListener(listener)
        awaitClose { backend.auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    suspend fun signIn(email: String, password: String) {
        backend.auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(email: String, password: String) {
        backend.auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        backend.auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Creates the Firestore profile and claims a unique friend code. */
    suspend fun createProfile(displayName: String, flower: String) {
        backend.call("createProfile", mapOf("displayName" to displayName.trim(), "flower" to flower))
    }

    suspend fun signOut() {
        val uid = backend.uid
        if (uid != null) {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                backend.user(uid).update("fcmTokens", FieldValue.arrayRemove(token)).await()
            }
        }
        backend.auth.signOut()
    }
}
