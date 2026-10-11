package dev.pcoffline.meadow.data

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Shares a daily usage summary so approvers can see how their friend is doing. */
class UsageShareRepository(private val backend: Backend) {

    suspend fun upload(usage: SharedUsage) {
        val uid = backend.uid ?: return
        backend.user(uid).collection("usage").document(usage.dayKey).set(
            mapOf(
                "dayKey" to usage.dayKey,
                "totalMinutes" to usage.totalMinutes,
                "apps" to usage.apps.map {
                    mapOf(
                        "packageName" to it.packageName,
                        "appName" to it.appName,
                        "minutes" to it.minutes,
                        "limitMinutes" to it.limitMinutes,
                    )
                },
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun heartbeat(protection: Map<String, Boolean>) {
        val uid = backend.uid ?: return
        backend.user(uid).update(
            mapOf(
                "lastHeartbeat" to FieldValue.serverTimestamp(),
                "protection" to protection,
            ),
        ).await()
    }

    /** Raises an alert that the backend forwards to every approver. */
    suspend fun reportAlert(kind: String, detail: String) {
        val uid = backend.uid ?: return
        backend.user(uid).collection("alerts").add(
            mapOf("kind" to kind, "detail" to detail, "createdAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    fun usage(uid: String, dayKey: String): Flow<SharedUsage?> =
        backend.user(uid).collection("usage").document(dayKey).observe()
            .map { if (it.exists()) it.toSharedUsage() else null }

    fun limits(uid: String): Flow<List<AppLimit>> =
        backend.user(uid).collection("limits").observe()
            .map { snapshot -> snapshot.documents.map { it.toLimit() }.sortedBy { it.appName.lowercase() } }
}
