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

    /**
     * Invites the person with [friendCode] to become my approver. When I already have approvers,
     * this becomes a request they must approve first, so nobody can quietly add a friendlier one.
     * Returns true when the invite was sent right away.
     */
    suspend fun invite(friendCode: String, message: String = ""): Boolean {
        val result = backend.call(
            "sendInvite",
            mapOf("friendCode" to friendCode.trim().uppercase(), "message" to message.trim()),
        )
        return result["status"] == "invited"
    }

    suspend fun respond(inviteId: String, accept: Boolean) {
        backend.call("respondInvite", mapOf("inviteId" to inviteId, "accept" to accept))
    }

    suspend fun cancelInvite(inviteId: String) {
        backend.call("cancelInvite", mapOf("inviteId" to inviteId))
    }

    /** I stop approving for [approveeUid]. They are notified. */
    suspend fun stepDown(approveeUid: String) {
        backend.call("stepDown", mapOf("approveeUid" to approveeUid))
    }

    suspend fun requestRemoveApprover(approverUid: String, message: String = "") {
        requests.create(type = RequestType.REMOVE_APPROVER, message = message, targetUid = approverUid)
    }

    suspend fun updateProfile(displayName: String, flower: String) {
        val uid = backend.requireUid()
        backend.user(uid).update(mapOf("displayName" to displayName.trim(), "flower" to flower)).await()
    }

    /** Turning strict mode on is instant; turning it off needs approval once approvers exist. */
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
