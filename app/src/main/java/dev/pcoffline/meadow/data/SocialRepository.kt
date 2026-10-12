package dev.pcoffline.meadow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SocialRepository(
    private val backend: Backend,
    private val store: LocalStore,
    private val requests: RequestRepository,
) {
    private val invites get() = backend.db.collection("invites")

    fun profile(uid: String): Flow<UserProfile?> =
        backend.user(uid).observe().map { if (it.exists()) it.toProfile() else null }

    fun profiles(uids: List<String>): Flow<List<UserProfile>> {
        if (uids.isEmpty()) return flowOf(emptyList())
        return combine(uids.distinct().map { profile(it) }) { list -> list.filterNotNull() }
    }

    fun incomingInvites(uid: String): Flow<List<Invite>> =
        invites.whereEqualTo("toUid", uid).whereEqualTo("status", "pending").observe()
            .map { snapshot -> snapshot.documents.map { it.toInvite() }.sortedByDescending { it.createdAtMillis } }

    fun outgoingInvites(uid: String): Flow<List<Invite>> =
        invites.whereEqualTo("fromUid", uid).whereEqualTo("status", "pending").observe()
            .map { snapshot -> snapshot.documents.map { it.toInvite() }.sortedByDescending { it.createdAtMillis } }

    /** Asks the person with [friendCode] to become my Petal Pal. */
    suspend fun invite(friendCode: String) {
        backend.call("sendInvite", mapOf("friendCode" to friendCode.trim().uppercase()))
    }

    suspend fun respond(inviteId: String, accept: Boolean) {
        backend.call("respondInvite", mapOf("inviteId" to inviteId, "accept" to accept))
    }

    suspend fun cancelInvite(inviteId: String) {
        backend.call("cancelInvite", mapOf("inviteId" to inviteId))
    }

    /** I stop being [approveeUid]'s Petal Pal. They are notified. */
    suspend fun stepDown(approveeUid: String) {
        backend.call("stepDown", mapOf("approveeUid" to approveeUid))
    }

    /** Removes one of my Petal Pals straight away. They, and my other pals, are notified. */
    suspend fun removeApprover(approverUid: String) {
        backend.call("removeApprover", mapOf("approverUid" to approverUid))
    }

    suspend fun updateProfile(displayName: String, flower: String) {
        val uid = backend.requireUid()
        backend.user(uid).update(mapOf("displayName" to displayName.trim(), "flower" to flower)).await()
    }

    /** Turning strict mode on is instant; turning it off needs a Petal Pal's yes once you have any. */
    suspend fun setStrictMode(enabled: Boolean, message: String = ""): ChangeOutcome {
        val uid = backend.requireUid()
        if (!enabled && store.approverCount.value > 0) {
            requests.create(type = RequestType.DISABLE_STRICT_MODE, message = message)
            return ChangeOutcome.REQUESTED
        }
        backend.user(uid).update("strictMode", enabled).await()
        return ChangeOutcome.APPLIED
    }
}
