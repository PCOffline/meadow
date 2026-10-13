package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val TAG = "MeadowFirestore"

/** Shares a daily usage summary so Bumblebees can see how their friend is doing. */
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

    /** Tells the backend this phone is alive. Only when, never what is switched on. */
    suspend fun heartbeat() {
        val uid = backend.uid ?: return
        val ref = backend.heartbeat(uid)
        try {
            ref.update("at", FieldValue.serverTimestamp()).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
            ref.set(mapOf("at" to FieldValue.serverTimestamp(), "staleNotified" to false)).await()
        }
    }

    /**
     * Tells the backend a protection was switched off; it lets every Bumblebee know that Meadow
     * may not be looking after this garden. Which one, and why, stays on this phone.
     */
    suspend fun reportProtectionLost() {
        val uid = backend.uid ?: return
        backend.user(uid).collection("alerts").add(
            mapOf("kind" to "protection_off", "createdAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    /**
     * A friend's shared screen time for [dayKey]. When it can't be read (they stopped sharing,
     * or you're no longer their Bumblebee), it reads as not shared, so neither what was on
     * screen nor a copy cached on this phone stays visible.
     */
    fun usage(uid: String, dayKey: String): Flow<SharedUsage?> =
        backend.user(uid).collection("usage").document(dayKey).tracedSnapshots()
            .map { if (it.exists()) it.toSharedUsage() else null }
            .catch {
                Log.w(TAG, "Shared usage unreadable", it)
                emit(null)
            }

    fun limits(uid: String): Flow<List<AppLimit>> =
        backend.user(uid).collection("limits").observe()
            .map { snapshot -> snapshot.documents.map { it.toLimit() }.sortedBy { it.appName.lowercase() } }
}
