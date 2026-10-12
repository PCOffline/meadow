package dev.pcoffline.meadow.data

import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RequestRepository(private val backend: Backend) {
    private val requests get() = backend.db.collection("requests")

    /**
     * Requests other people sent to me as one of their Bumblebees: my own copies, which don't say
     * who else was asked or who answered.
     */
    fun incoming(uid: String, limit: Long = 100): Flow<List<LimitRequest>> =
        backend.inbox(uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .observe()
            .map { snapshot -> snapshot.documents.map { it.toRequest() } }

    /** Requests I sent. */
    fun outgoing(uid: String): Flow<List<LimitRequest>> =
        requests.whereEqualTo("requesterId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .observe()
            .map { snapshot -> snapshot.documents.map { it.toRequest() } }

    /** One of my own requests. */
    fun observe(id: String): Flow<LimitRequest?> =
        requests.document(id).observe().map { if (it.exists()) it.toRequest() else null }

    /** My copy of a request someone sent me. */
    fun observeInbox(uid: String, id: String): Flow<LimitRequest?> =
        backend.inbox(uid).document(id).observe().map { if (it.exists()) it.toRequest() else null }

    suspend fun create(
        type: RequestType,
        message: String = "",
        packageName: String? = null,
        appName: String? = null,
        minutes: Int = 0,
        dailyMinutes: Int = 0,
        weeklyMinutes: Int = 0,
        schedule: Schedule? = null,
        /** Which Bumblebees to ask; null asks all of them. */
        approverIds: List<String>? = null,
    ): String {
        val result = backend.call(
            "createRequest",
            mapOf(
                "type" to type.wire,
                "message" to message.trim(),
                "packageName" to packageName,
                "appName" to appName,
                "minutes" to minutes,
                "dailyMinutes" to dailyMinutes,
                "weeklyMinutes" to weeklyMinutes,
                "dayKey" to Days.todayKey(),
                "schedule" to schedule?.toFirestore(),
                "approverIds" to approverIds,
            ),
        )
        return result["requestId"] as? String ?: ""
    }

    suspend fun decide(id: String, approve: Boolean, note: String = "", minutes: Int? = null) {
        backend.call(
            "decideRequest",
            mapOf("requestId" to id, "approve" to approve, "note" to note.trim(), "minutes" to minutes),
        )
    }

    suspend fun cancel(id: String) {
        backend.call("cancelRequest", mapOf("requestId" to id))
    }
}
