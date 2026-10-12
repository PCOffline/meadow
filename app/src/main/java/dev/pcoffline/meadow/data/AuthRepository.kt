package dev.pcoffline.meadow.data

import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.BuildConfig
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

class AuthRepository(private val backend: Backend, private val store: LocalStore) {

    private val _anonymous = MutableStateFlow(false)

    /** True while the account has no email attached (started with "no registration"). */
    val anonymous: StateFlow<Boolean> = _anonymous.asStateFlow()

    private val _creatingProfile = MutableStateFlow(false)

    /**
     * True from planting a new garden until its profile has loaded (or after a failed try), so
     * onboarding stays on screen, with any error, instead of flashing other screens.
     */
    val creatingProfile: StateFlow<Boolean> = _creatingProfile.asStateFlow()

    /** Called once the new profile has loaded, or on signing out. */
    fun finishCreatingProfile() {
        _creatingProfile.value = false
    }

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
        if (displayName.isBlank()) throw FriendlyException(R.string.error_name_required)
        _creatingProfile.value = true
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

    /**
     * Signs in with a Google ID token. A garden started without an email keeps everything:
     * the Google account is attached to it instead of starting a new one.
     */
    suspend fun continueWithGoogle(idToken: String) {
        signInOrAttach(GoogleAuthProvider.getCredential(idToken, null))
    }

    /** Emails a one-time sign-in link: no password needed. */
    suspend fun sendSignInLink(email: String) {
        val address = email.trim()
        if (address.isBlank()) throw FriendlyException(R.string.error_email_required)
        val settings = ActionCodeSettings.newBuilder()
            .setUrl("https://${BuildConfig.LINK_HOST}/signed-in")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(BuildConfig.APPLICATION_ID, false, null)
            .setLinkDomain(BuildConfig.LINK_HOST)
            .build()
        backend.auth.sendSignInLinkToEmail(address, settings).await()
        store.pendingLinkEmail = address
    }

    private val _incomingLink = MutableStateFlow<String?>(null)

    /** A sign-in link that opened the app, waiting to be used. */
    val incomingLink: StateFlow<String?> = _incomingLink.asStateFlow()

    /** Returns true if [link] is a sign-in link, which is then kept until it's used or dismissed. */
    fun receiveLink(link: String): Boolean {
        if (!backend.auth.isSignInWithEmailLink(link)) return false
        _incomingLink.value = link
        return true
    }

    fun dismissLink() {
        _incomingLink.value = null
    }

    /** The email a link was sent to from this phone, if any. */
    val linkEmail: String? get() = store.pendingLinkEmail

    /** Uses a sign-in link. [email] must be the address it was sent to. */
    suspend fun finishSignInLink(link: String, email: String) {
        signInOrAttach(EmailAuthProvider.getCredentialWithLink(email.trim(), link))
        store.pendingLinkEmail = null
        _incomingLink.value = null
    }

    private suspend fun signInOrAttach(credential: AuthCredential) {
        val user = backend.auth.currentUser
        if (user != null && user.isAnonymous) {
            user.linkWithCredential(credential).await()
            user.reload().await()
            _anonymous.value = false
        } else {
            backend.auth.signInWithCredential(credential).await()
        }
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
        // Forget the Google account choice too, so the next sign-in can pick another.
        runCatchingCancellable { GoogleSignIn.forget(AppGraph.app) }
    }
}
