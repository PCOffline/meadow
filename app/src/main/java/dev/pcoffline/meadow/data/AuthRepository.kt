package dev.pcoffline.meadow.data

import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import dev.pcoffline.meadow.BuildConfig
import dev.pcoffline.meadow.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

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
            Trace.log {
                val user = it.currentUser
                if (user == null) "auth: signed out"
                else "auth: ${user.uid} anonymous=${user.isAnonymous} providers=${user.providerData.map { p -> p.providerId }}"
            }
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
     * Emails a one-time sign-in link: no password needed. The link goes through the project's
     * firebaseapp.com domain, which opens the app ([BuildConfig.AUTH_LINK_HOST]). Firebase refuses
     * a default Hosting domain as a custom link domain, so none is set.
     */
    suspend fun sendSignInLink(email: String) {
        val address = email.trim()
        if (address.isBlank()) throw FriendlyException(R.string.error_email_required)
        val settings = ActionCodeSettings.newBuilder()
            .setUrl("https://${BuildConfig.LINK_HOST}/signed-in")
            .setHandleCodeInApp(true)
            .setAndroidPackageName(BuildConfig.APPLICATION_ID, false, null)
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

    private val linking = Mutex()
    private var usedLink: String? = null

    /**
     * Uses a sign-in link. [email] must be the address it was sent to. A garden started without
     * an email keeps everything: the address is attached to it instead of starting a new one.
     * A link works once, so a second try at the same one (after a rotation, say) is ignored.
     */
    suspend fun finishSignInLink(link: String, email: String) = linking.withLock {
        if (link != usedLink) {
            signInOrAttach(email.trim(), EmailAuthProvider.getCredentialWithLink(email.trim(), link))
            usedLink = link
        }
        store.pendingLinkEmail = null
        _incomingLink.value = null
    }

    private suspend fun signInOrAttach(email: String, credential: AuthCredential) {
        val user = backend.auth.currentUser
        if (user == null) {
            backend.auth.signInWithCredential(credential).await()
            return
        }
        when {
            user.isAnonymous -> try {
                user.linkWithCredential(credential).await()
                user.reload().await()
                _anonymous.value = false
            } catch (e: FirebaseAuthUserCollisionException) {
                // The address already has a garden. An unused anonymous start (no profile yet)
                // has nothing to lose, so sign in to that garden instead.
                if (hasProfile(user.uid)) throw e
                backend.auth.signInWithCredential(credential).await()
            }
            // Already signed in with this address: nothing to do.
            user.email.equals(email, ignoreCase = true) -> Unit
            // Signed in as someone else: sign them out properly (push token included) first.
            else -> {
                signOut()
                backend.auth.signInWithCredential(credential).await()
            }
        }
    }

    private suspend fun hasProfile(uid: String): Boolean =
        runCatchingCancellable { backend.user(uid).get().await().exists() }.getOrDefault(true)

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

    /** Stops this phone getting the account's pushes, then signs out. Never waits long offline. */
    suspend fun signOut() {
        val uid = backend.uid
        if (uid != null) {
            runCatchingCancellable {
                withTimeoutOrNull(SIGN_OUT_TIMEOUT_MS) {
                    val installations = FirebaseInstallations.getInstance()
                    val installationId = installations.id.await()
                    backend.account(uid).update("pushIds", FieldValue.arrayRemove(installationId)).awaitWrite(SIGN_OUT_TIMEOUT_MS)
                    FirebaseMessaging.getInstance().unregister().await()
                    // A fresh ID for whoever signs in next, so pushes still addressed to this one
                    // (if removing it above didn't reach the server) can't reach them.
                    installations.delete().await()
                }
            }
        }
        backend.auth.signOut()
    }

    private companion object {
        const val SIGN_OUT_TIMEOUT_MS = 4_000L
    }
}
