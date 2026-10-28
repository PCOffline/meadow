package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import dev.pcoffline.meadow.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface ProfileState {
    data object SignedOut : ProfileState
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Ready(val profile: UserProfile) : ProfileState

    /** The server refused or failed to send the profile, so it can't be shown; [cause] says why. */
    data class Unavailable(val cause: Throwable) : ProfileState
}

/**
 * Mirrors the signed-in user's profile, limits and grants into [LocalStore] for as long as
 * the process lives, so enforcement always sees the latest commitments.
 */
class SyncManager(
    private val backend: Backend,
    private val auth: AuthRepository,
    private val store: LocalStore,
    private val scope: CoroutineScope,
) {
    private val _profile = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profile: StateFlow<ProfileState> = _profile.asStateFlow()

    val currentProfile: UserProfile? get() = (profile.value as? ProfileState.Ready)?.profile

    /** Bumped to listen again after the profile couldn't be loaded. */
    private val attempt = MutableStateFlow(0)

    fun retry() {
        attempt.value++
    }

    fun start() {
        scope.launch {
            combine(auth.uid, attempt) { uid, _ -> uid }.collectLatest { uid ->
                if (uid == null) {
                    leaving = false
                    auth.finishCreatingProfile()
                    _profile.value = ProfileState.SignedOut
                    return@collectLatest
                }
                _profile.value = ProfileState.Loading
                store.switchAccount(uid)
                // Scoped so every listener stops when the account changes.
                coroutineScope {
                    launch { syncProfile(uid) }
                    launch { syncLimits(uid) }
                    launch { syncGrants(uid) }
                }
            }
        }
    }

    /** Set while the account is being deleted, so its vanishing profile isn't mistaken for a new one. */
    @Volatile private var leaving = false

    fun beginLeaving() {
        leaving = true
    }

    fun cancelLeaving() {
        leaving = false
    }

    private val _accountConfirmed = MutableStateFlow(false)

    /** True once the account has been read from the server, so choices like accepted terms are known. */
    val accountConfirmed: StateFlow<Boolean> = _accountConfirmed.asStateFlow()

    private suspend fun syncProfile(uid: String) {
        _accountConfirmed.value = false
        var pushRegistered = false
        var appearanceSynced = false
        // The name card plus the owner-only account (who looks after whom, settings, status).
        combine(backend.user(uid).tracedSnapshots(), backend.account(uid).tracedSnapshots()) { doc, accountDoc -> doc to accountDoc }
            .onEach { (doc, accountDoc) ->
                if (!doc.exists()) {
                    if (!doc.metadata.isFromCache && !leaving) _profile.value = ProfileState.Missing
                    return@onEach
                }
                val account = if (accountDoc.exists()) accountDoc.toAccount() else Account()
                val profile = doc.toProfile(accountDoc)
                store.setStrictMode(profile.strictMode)
                store.setApproverCount(profile.approverIds.size)
                store.setPrivacy(profile.privacy)
                if (account.acceptedTermsVersion > store.acceptedTermsVersion) store.acceptedTermsVersion = account.acceptedTermsVersion
                if (!accountDoc.metadata.isFromCache) _accountConfirmed.value = true
                // Theme choices follow the person between phones; this phone's choice wins until synced once.
                if (!appearanceSynced && accountDoc.exists() && !accountDoc.metadata.isFromCache) {
                    appearanceSynced = true
                    if (account.theme != null || account.darkMode != null) store.setAppearance(account.theme, account.darkMode)
                }
                // An unreported offline unlock wins until the backend has recorded it.
                if (store.pendingEmergency == null) {
                    store.setEmergency(
                        if (profile.emergencyUntilMillis > 0L) Emergency(profile.emergencyUntilMillis, profile.emergencyPackage) else null,
                    )
                }
                _profile.value = ProfileState.Ready(profile)
                auth.finishCreatingProfile()
                // The account document must exist before the phone can be added to it.
                if (!pushRegistered && !doc.metadata.isFromCache) {
                    pushRegistered = true
                    scope.launch { registerPush(uid) }
                    scope.launch { runCatchingCancellable { AppGraph.emergency.flushPending() } }
                }
            }
            .relisten("Profile listener") { e ->
                // Without this the app would wait forever. A profile already shown stays, so
                // limits keep working, and the listener tries again until it's back.
                if (_profile.value !is ProfileState.Ready) {
                    _profile.value = ProfileState.Unavailable(e)
                    auth.finishCreatingProfile()
                }
            }
            .collect()
    }

    private suspend fun syncLimits(uid: String) {
        // Stored in onEach, before relisten, so a snapshot that can't be stored is retried like a
        // dropped listener rather than ending sync.
        backend.user(uid).collection("limits").tracedSnapshots().onEach { snapshot ->
            // An empty answer from a cold cache must not wipe commitments we already hold. One that
            // comes from this phone's own pending write (the last limit removed offline) is real.
            if (snapshot.isEmpty && snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites() &&
                store.limits.value.isNotEmpty()
            ) return@onEach
            store.setLimits(snapshot.documents.map { it.toLimit() }.filter { it.packageName.isNotBlank() })
        }.relisten("Limits listener").collect()
    }

    private suspend fun syncGrants(uid: String) {
        val since = Days.key(Days.today().minusDays(7))
        backend.user(uid).collection("grants")
            .whereGreaterThanOrEqualTo("dayKey", since)
            .tracedSnapshots()
            .onEach { snapshot ->
                if (snapshot.isEmpty && snapshot.metadata.isFromCache && store.grants.value.isNotEmpty()) return@onEach
                store.setGrants(snapshot.documents.map { it.toGrant() })
            }
            .relisten("Grants listener")
            .collect()
    }

    /** Registers this phone for pushes and records its installation ID on the account. */
    private suspend fun registerPush(uid: String) {
        runCatchingCancellable { registerPushNow(uid) }.onFailure { Log.w(TAG, "Could not register for pushes", it) }
    }

    /**
     * Registers this phone for pushes again, throwing if it can't (e.g. no Google Play services).
     * Troubleshoot does this before a test notification, so a failed registration at startup is
     * fixed there rather than leaving the phone silent.
     */
    suspend fun registerThisPhone() = registerPushNow(backend.uid ?: error("Not signed in"))

    private suspend fun registerPushNow(uid: String) {
        FirebaseMessaging.getInstance().register().await()
        savePushId(uid, FirebaseInstallations.getInstance().id.await())
    }

    /** FCM registered this phone again, e.g. after its installation ID changed. */
    fun onPushRegistered(installationId: String) {
        val uid = backend.uid ?: return
        scope.launch { runCatchingCancellable { savePushId(uid, installationId) } }
    }

    /**
     * Adds this phone's installation ID. IDs of reinstalled or reset phones are never removed by
     * them, so the list keeps only the newest few, or the backend would refuse new phones.
     */
    private suspend fun savePushId(uid: String, installationId: String) {
        val account = backend.account(uid)
        val current = account.get().await().stringList(PUSH_IDS)
        if (installationId in current) return
        account.update(PUSH_IDS, (current + installationId).takeLast(MAX_PUSH_IDS)).awaitWrite()
    }

    private companion object {
        const val TAG = "MeadowSync"

        const val PUSH_IDS = "pushIds"

        /** Must stay within the limit in firestore.rules. */
        const val MAX_PUSH_IDS = 10
    }
}
