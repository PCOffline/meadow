package dev.pcoffline.meadow.data

import android.util.Log
import dev.pcoffline.meadow.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SocialRepository(
    private val backend: Backend,
    /** Whether Android lets Meadow run unrestricted and reach notifications, as strict mode needs. */
    private val strictReady: () -> Boolean = { true },
) {
    private val invites get() = backend.db.collection("invites")

    /** Someone's name card; null if it's gone or can't be read, so lists built from several still show the rest. */
    fun profile(uid: String): Flow<UserProfile?> =
        backend.user(uid).tracedSnapshots()
            .map { if (it.exists()) it.toProfile() else null }
            .catch {
                Log.w("MeadowSocial", "Profile listener failed", it)
                emit(null)
            }

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

    /** Creates a one-time link; whoever accepts it becomes my Bumblebee. */
    suspend fun createInviteLink(): InviteLink {
        val result = backend.call("createInviteLink")
        return InviteLink(
            token = result["token"] as String,
            url = result["url"] as String,
            expiresAtMillis = (result["expiresAt"] as Number).toLong(),
        )
    }

    suspend fun previewInviteLink(token: String): InviteLinkPreview {
        val result = backend.call("previewInviteLink", mapOf("token" to token))
        return InviteLinkPreview(
            ownerName = result["ownerName"] as? String ?: "",
            ownerFlower = result["ownerFlower"] as? String ?: "🌼",
            status = when (result["status"]) {
                "expired" -> InviteLinkStatus.EXPIRED
                "used" -> InviteLinkStatus.USED
                "own" -> InviteLinkStatus.OWN
                "already" -> InviteLinkStatus.ALREADY
                else -> InviteLinkStatus.OK
            },
        )
    }

    /** Becomes the Bumblebee of whoever created the link. */
    suspend fun acceptInviteLink(token: String) {
        backend.call("acceptInviteLink", mapOf("token" to token))
    }

    /** Asks the person with [friendCode] to be my Bumblebee. */
    suspend fun invite(friendCode: String) {
        backend.call("sendInvite", mapOf("friendCode" to friendCode.trim().uppercase()))
    }

    suspend fun respond(inviteId: String, accept: Boolean) {
        backend.call("respondInvite", mapOf("inviteId" to inviteId, "accept" to accept))
    }

    suspend fun cancelInvite(inviteId: String) {
        backend.call("cancelInvite", mapOf("inviteId" to inviteId))
    }

    /** I stop being [approveeUid]'s Bumblebee. They are notified. */
    suspend fun stepDown(approveeUid: String) {
        backend.call("stepDown", mapOf("approveeUid" to approveeUid))
    }

    /**
     * Removes one of my Bumblebees after a day's wait, so it can't be a way around a "no". Only
     * they are told, straight away. Returns when it takes effect.
     */
    suspend fun removeApprover(approverUid: String): Long {
        val result = backend.call("removeApprover", mapOf("approverUid" to approverUid))
        return (result["effectiveAt"] as? Number)?.toLong() ?: (System.currentTimeMillis() + REMOVAL_DELAY_MS)
    }

    /** Keeps a Bumblebee whose removal is still waiting. */
    suspend fun cancelRemoval(approverUid: String) {
        backend.call("cancelRemoval", mapOf("approverUid" to approverUid))
    }

    /**
     * Strict mode is the person's own business: it turns on and off straight away, and nobody
     * else is told. It needs Meadow to be safe from battery savers and able to reach notifications.
     */
    suspend fun setStrictMode(enabled: Boolean) {
        if (enabled && !strictReady()) throw FriendlyException(R.string.error_strict_needs_setup)
        val uid = backend.requireUid()
        backend.account(uid).update("strictMode", enabled).awaitWrite()
    }

    companion object {
        const val REMOVAL_DELAY_MS = 24 * 60 * 60_000L
    }
}
