package dev.pcoffline.meadow.data

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

enum class ChangeOutcome { APPLIED, REQUESTED }

/**
 * Tightening a limit always applies immediately. Loosening or removing one needs an approver
 * to agree once you have any approvers; Firestore rules enforce the same thing server-side.
 */
class LimitsRepository(
    private val backend: Backend,
    private val store: LocalStore,
    private val requests: RequestRepository,
) {
    suspend fun save(limit: AppLimit, message: String = ""): ChangeOutcome {
        val uid = backend.requireUid()
        require(limit.dailyMinutes > 0 || limit.weeklyMinutes > 0) { "Set a daily or weekly limit." }
        val current = store.limits.value.firstOrNull { it.packageName == limit.packageName }
        if (current != null && isLooser(current, limit) && store.approverCount.value > 0) {
            requests.create(
                type = RequestType.CHANGE_LIMIT,
                message = message,
                packageName = limit.packageName,
                appName = limit.appName,
                dailyMinutes = limit.dailyMinutes,
                weeklyMinutes = limit.weeklyMinutes,
            )
            return ChangeOutcome.REQUESTED
        }
        val data = mutableMapOf<String, Any>(
            "packageName" to limit.packageName,
            "appName" to limit.appName,
            "dailyMinutes" to limit.dailyMinutes,
            "weeklyMinutes" to limit.weeklyMinutes,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (current == null) data["createdAt"] = FieldValue.serverTimestamp()
        // The local snapshot updates right away; offline writes are queued by Firestore.
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            backend.user(uid).collection("limits").document(limit.packageName).set(data).await()
        }
        return ChangeOutcome.APPLIED
    }

    suspend fun remove(limit: AppLimit, message: String = ""): ChangeOutcome {
        val uid = backend.requireUid()
        if (store.approverCount.value > 0) {
            requests.create(
                type = RequestType.REMOVE_LIMIT,
                message = message,
                packageName = limit.packageName,
                appName = limit.appName,
            )
            return ChangeOutcome.REQUESTED
        }
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            backend.user(uid).collection("limits").document(limit.packageName).delete().await()
        }
        return ChangeOutcome.APPLIED
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 8_000L

        fun isLooser(old: AppLimit, new: AppLimit): Boolean {
            val dailyLooser = old.dailyMinutes > 0 && (new.dailyMinutes == 0 || new.dailyMinutes > old.dailyMinutes)
            val weeklyLooser = old.weeklyMinutes > 0 && (new.weeklyMinutes == 0 || new.weeklyMinutes > old.weeklyMinutes)
            return dailyLooser || weeklyLooser
        }
    }
}
