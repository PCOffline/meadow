package dev.pcoffline.meadow.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Emergency unlocks lift limits right away, without waiting for an approver. They're rationed
 * (three per 30 days, counted by the backend) and approvers hear about each one with its reason.
 * Without a connection, one unlock per 30 days can be taken offline; it's reported once back online.
 */
class EmergencyRepository(
    private val context: Context,
    private val backend: Backend,
    private val store: LocalStore,
) {
    data class Result(val untilMillis: Long, val offline: Boolean)

    fun canUnlockOffline(now: Long = System.currentTimeMillis()): Boolean =
        now - store.lastOfflineEmergencyMillis > Emergency.WINDOW_MS

    suspend fun unlock(minutes: Int, reason: String, packageName: String?, appName: String?): Result {
        require(minutes in Emergency.MINUTE_OPTIONS) { "Pick 15, 30 or 60 minutes." }
        require(reason.trim().length >= 10) { "Please say briefly what the emergency is." }
        if (backend.uid != null && isOnline()) {
            try {
                val response = backend.call(
                    "emergencyUnlock",
                    mapOf("minutes" to minutes, "reason" to reason.trim(), "packageName" to packageName, "appName" to appName),
                )
                val until = (response["until"] as? Number)?.toLong() ?: (System.currentTimeMillis() + minutes * 60_000L)
                store.setEmergency(Emergency(until, packageName))
                return Result(until, offline = false)
            } catch (e: Backend.CallFailed) {
                if (!e.unreachable) throw e
            }
        }
        return unlockOffline(minutes, reason, packageName, appName)
    }

    private fun unlockOffline(minutes: Int, reason: String, packageName: String?, appName: String?): Result {
        check(canUnlockOffline()) {
            "You can't reach Meadow right now, and your one offline emergency unlock for this month is used up."
        }
        val now = System.currentTimeMillis()
        val until = now + minutes * 60_000L
        store.lastOfflineEmergencyMillis = now
        store.pendingEmergency = JSONObject()
            .put("minutes", minutes)
            .put("reason", reason.trim())
            .put("packageName", packageName ?: JSONObject.NULL)
            .put("appName", appName ?: JSONObject.NULL)
            .put("startedAt", now)
        store.setEmergency(Emergency(until, packageName))
        return Result(until, offline = true)
    }

    /** Reports an offline unlock so approvers hear about it and it counts against the allowance. */
    suspend fun flushPending() {
        val pending = store.pendingEmergency ?: return
        if (backend.uid == null) return
        try {
            backend.call(
                "emergencyUnlock",
                mapOf(
                    "minutes" to pending.getInt("minutes"),
                    "reason" to pending.getString("reason"),
                    "packageName" to pending.optString("packageName").takeIf { !pending.isNull("packageName") },
                    "appName" to pending.optString("appName").takeIf { !pending.isNull("appName") },
                    "startedAt" to pending.getLong("startedAt"),
                    "endedAt" to pending.optLong("endedAt").takeIf { pending.has("endedAt") },
                ),
            )
            store.pendingEmergency = null
        } catch (e: Backend.CallFailed) {
            // Unreachable: try again on the next sync. Anything else would never succeed.
            if (!e.unreachable) store.pendingEmergency = null
        }
    }

    /** Ends the current unlock early. Works offline too; Firestore queues the write. */
    suspend fun end() {
        val now = System.currentTimeMillis()
        store.setEmergency(Emergency(now, null))
        store.pendingEmergency?.let {
            store.pendingEmergency = it.put("endedAt", now)
            return
        }
        val uid = backend.uid ?: return
        withTimeoutOrNull(8_000L) { backend.user(uid).update("emergencyUntil", FieldValue.serverTimestamp()).await() }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
