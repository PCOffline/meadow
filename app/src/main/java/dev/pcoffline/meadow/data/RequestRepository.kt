package dev.pcoffline.meadow.data

import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Minutes spent on an app today, and minutes today allows in all (null when only hours limit it). */
data class DayUsage(val usedMinutes: Int, val allowedMinutes: Int?)

class RequestRepository(
    private val backend: Backend,
    /** How the day stands for an app, sent along so Bumblebees can decide. */
    private val usage: (packageName: String) -> DayUsage? = { null },
) {
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

    /**
     * Requests I sent. [fresh] waits a moment for the server's copy rather than show the cached
     * one first, for screens that decide from whether one is still waiting: it may well have been
     * answered while nothing here was listening.
     */
    fun outgoing(uid: String, fresh: Boolean = false): Flow<List<LimitRequest>> =
        requests.whereEqualTo("requesterId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .observe()
            .let { if (fresh) it.preferServer({ snapshot -> snapshot.metadata.isFromCache }) else it }
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
        dayMinutes: Map<Int, Int> = emptyMap(),
        caps: List<TimeCap> = emptyList(),
        /** Which Bumblebees to ask; null asks all of them. */
        approverIds: List<String>? = null,
    ): String {
        val day = packageName?.let { pkg -> withContext(Dispatchers.IO) { runCatching { usage(pkg) }.getOrNull() } }
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
                "dayMinutes" to dayMinutes.takeIf { it.isNotEmpty() }?.let(DayMinutes::toFirestore),
                "caps" to caps.map(TimeCap::toFirestore),
                "approverIds" to approverIds,
                "usedMinutes" to day?.usedMinutes,
                "allowedMinutes" to day?.allowedMinutes,
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
