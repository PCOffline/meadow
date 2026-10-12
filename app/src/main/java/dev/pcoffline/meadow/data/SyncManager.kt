package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dev.pcoffline.meadow.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface ProfileState {
    data object SignedOut : ProfileState
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Ready(val profile: UserProfile) : ProfileState
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

    fun start() {
        scope.launch {
            auth.uid.collectLatest { uid ->
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
        var tokenRegistered = false
        var appearanceSynced = false
        // The public profile plus the owner-only account (who looks after whom, preferences).
        combine(backend.user(uid).observe(), backend.account(uid).observe()) { doc, accountDoc -> doc to accountDoc }.collect { (doc, accountDoc) ->
            if (!doc.exists()) {
                if (!doc.metadata.isFromCache && !leaving) _profile.value = ProfileState.Missing
                return@collect
            }
            val account = if (accountDoc.exists()) accountDoc.toAccount() else Account()
            val profile = doc.toProfile(account)
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
            // The account document must exist before the token can be added to it.
            if (!tokenRegistered && !doc.metadata.isFromCache) {
                tokenRegistered = true
                scope.launch { registerPushToken(uid) }
                scope.launch { runCatchingCancellable { AppGraph.emergency.flushPending() } }
            }
        }
    }

    private suspend fun syncLimits(uid: String) {
        backend.user(uid).collection("limits").observe().collect { snapshot ->
            // An empty answer from a cold cache must not wipe commitments we already hold. One that
            // comes from this phone's own pending write (the last limit removed offline) is real.
            if (snapshot.isEmpty && snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites() &&
                store.limits.value.isNotEmpty()
            ) return@collect
            store.setLimits(snapshot.documents.map { it.toLimit() }.filter { it.packageName.isNotBlank() })
        }
    }

    private suspend fun syncGrants(uid: String) {
        val since = Days.key(Days.today().minusDays(7))
        backend.user(uid).collection("grants")
            .whereGreaterThanOrEqualTo("dayKey", since)
            .observe()
            .collect { snapshot ->
                if (snapshot.isEmpty && snapshot.metadata.isFromCache && store.grants.value.isNotEmpty()) return@collect
                store.setGrants(snapshot.documents.map { it.toGrant() })
            }
    }

    private suspend fun registerPushToken(uid: String) {
        runCatchingCancellable {
            val token = FirebaseMessaging.getInstance().token.await()
            saveToken(uid, token)
        }.onFailure { Log.w("MeadowSync", "Could not register push token", it) }
    }

    fun onNewToken(token: String) {
        val uid = backend.uid ?: return
        scope.launch { runCatchingCancellable { saveToken(uid, token) } }
    }

    /**
     * Adds this phone's push token. Tokens of reinstalled or reset phones are never removed by
     * them, so the list keeps only the newest few, or the backend would refuse new phones.
     */
    private suspend fun saveToken(uid: String, token: String) {
        val account = backend.account(uid)
        val current = account.get().await().stringList("fcmTokens")
        if (token in current) return
        account.update("fcmTokens", (current + token).takeLast(MAX_TOKENS)).awaitWrite()
    }

    private companion object {
        /** Must stay within the limit in firestore.rules. */
        const val MAX_TOKENS = 10
    }
}
