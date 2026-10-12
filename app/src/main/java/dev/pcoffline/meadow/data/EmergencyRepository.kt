package dev.pcoffline.meadow.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.firestore.FieldValue
import dev.pcoffline.meadow.R
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Emergency unlocks lift limits right away, without waiting for an approver. They're rationed
 * (three per 30 days, counted by the backend) and Bumblebees hear about each one with its reason.
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
        if (minutes !in Emergency.MINUTE_OPTIONS) throw FriendlyException(R.string.error_emergency_minutes)
        if (reason.trim().length < MIN_REASON_LENGTH) throw FriendlyException(R.string.error_emergency_reason)
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
        if (!canUnlockOffline()) throw FriendlyException(R.string.error_emergency_offline_used)
        val now = System.currentTimeMillis()
        val until = now + minutes * 60_000L
        store.lastOfflineEmergencyMillis = now
        store.pendingEmergency = PendingEmergency(minutes, reason.trim(), packageName, appName, startedAt = now, ownerUid = backend.uid)
        store.setEmergency(Emergency(until, packageName))
        return Result(until, offline = true)
    }

    private val flushing = Mutex()

    /**
     * Reports an offline unlock so approvers hear about it and it counts against the allowance.
     * App start and background sync can both get here at once; the lock keeps it to one report.
     */
    suspend fun flushPending(): Unit = flushing.withLock {
        val pending = store.pendingEmergency ?: return@withLock
        val uid = backend.uid ?: return@withLock
        if (pending.ownerUid != null && pending.ownerUid != uid) return@withLock
        try {
            backend.call(
                "emergencyUnlock",
                mapOf(
                    "minutes" to pending.minutes,
                    "reason" to pending.reason,
                    "packageName" to pending.packageName,
                    "appName" to pending.appName,
                    "startedAt" to pending.startedAt,
                    "endedAt" to pending.endedAt,
                    "reportId" to pending.reportId,
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
            store.pendingEmergency = it.copy(endedAt = now)
            return
        }
        val uid = backend.uid ?: return
        withTimeoutOrNull(8_000L) { backend.user(uid).update("emergencyUntil", FieldValue.serverTimestamp()).await() }
    }

    companion object {
        const val MIN_REASON_LENGTH = 10
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
