package dev.pcoffline.meadow.data

import com.google.firebase.auth.EmailAuthProvider
import dev.pcoffline.meadow.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class AuthRepository(private val backend: Backend) {

    private val _anonymous = MutableStateFlow(false)

    /** True while the account has no email attached (started with "no registration"). */
    val anonymous: StateFlow<Boolean> = _anonymous.asStateFlow()

    val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener {
            _anonymous.value = it.currentUser?.isAnonymous == true
            trySend(it.currentUser?.uid)
        }
        backend.auth.addAuthStateListener(listener)
        awaitClose { backend.auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    suspend fun signIn(email: String, password: String) {
        backend.auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(email: String, password: String) {
        backend.auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    /** Starts without an email: an anonymous account plus a profile. */
    suspend fun startWithoutEmail(displayName: String, flower: String, termsVersion: Int) {
        if (backend.auth.currentUser == null) backend.auth.signInAnonymously().await()
        createProfile(displayName, flower, termsVersion)
    }

    /**
     * Attaches an email and password to an account started without one, keeping all its data.
     * Needed before inviting approvers, so a reinstall can't wipe your commitments.
     */
    suspend fun addEmail(email: String, password: String) {
        val user = backend.auth.currentUser ?: throw FriendlyException(R.string.error_sign_in_first)
        if (password.length < 6) throw FriendlyException(R.string.error_auth_weak_password)
        user.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password)).await()
        user.reload().await()
        _anonymous.value = false
    }

    suspend fun sendPasswordReset(email: String) {
        if (email.isBlank()) throw FriendlyException(R.string.error_email_required)
        backend.auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Creates the Firestore profile and claims a unique friend code. */
    suspend fun createProfile(displayName: String, flower: String, termsVersion: Int) {
        if (displayName.isBlank()) throw FriendlyException(R.string.error_name_required)
        backend.call(
            "createProfile",
            mapOf("displayName" to displayName.trim(), "flower" to flower, "termsVersion" to termsVersion),
        )
    }

    suspend fun signOut() {
        val uid = backend.uid
        if (uid != null) {
            runCatchingCancellable {
                val token = FirebaseMessaging.getInstance().token.await()
                backend.account(uid).update("fcmTokens", FieldValue.arrayRemove(token)).await()
            }
        }
        backend.auth.signOut()
    }
}
