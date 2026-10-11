package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import dev.pcoffline.meadow.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
                    _profile.value = ProfileState.SignedOut
                    return@collectLatest
                }
                _profile.value = ProfileState.Loading
                store.ownerUid = uid
                // Scoped so every listener stops when the account changes.
                coroutineScope {
                    launch { syncProfile(uid) }
                    launch { syncLimits(uid) }
                    launch { syncGrants(uid) }
                }
            }
        }
    }

    private suspend fun syncProfile(uid: String) {
        var tokenRegistered = false
        backend.user(uid).observe().collect { doc ->
            if (!doc.exists()) {
                if (!doc.metadata.isFromCache) _profile.value = ProfileState.Missing
                return@collect
            }
            val profile = doc.toProfile()
            store.setStrictMode(profile.strictMode)
            store.setApproverCount(profile.approverIds.size)
            // An unreported offline unlock wins until the backend has recorded it.
            if (store.pendingEmergency == null) {
                store.setEmergency(
                    if (profile.emergencyUntilMillis > 0L) Emergency(profile.emergencyUntilMillis, profile.emergencyPackage) else null,
                )
            }
            _profile.value = ProfileState.Ready(profile)
            // The profile document must exist before the token can be added to it.
            if (!tokenRegistered && !doc.metadata.isFromCache) {
                tokenRegistered = true
                scope.launch { registerPushToken(uid) }
                scope.launch { runCatching { AppGraph.emergency.flushPending() } }
            }
        }
    }

    private suspend fun syncLimits(uid: String) {
        backend.user(uid).collection("limits").observe().collect { snapshot ->
            // An empty answer from a cold cache must not wipe commitments we already hold.
            if (snapshot.isEmpty && snapshot.metadata.isFromCache && store.limits.value.isNotEmpty()) return@collect
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
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            saveToken(uid, token)
        }.onFailure { Log.w("MeadowSync", "Could not register push token", it) }
    }

    fun onNewToken(token: String) {
        val uid = backend.uid ?: return
        scope.launch { runCatching { saveToken(uid, token) } }
    }

    private suspend fun saveToken(uid: String, token: String) {
        backend.user(uid).update("fcmTokens", FieldValue.arrayUnion(token)).await()
    }
}
