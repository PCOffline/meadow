package dev.pcoffline.meadow.data

import com.google.firebase.firestore.FieldValue
import dev.pcoffline.meadow.R

enum class ChangeOutcome { APPLIED, REQUESTED }

/**
 * Tightening a limit always applies immediately. Loosening or removing one needs a Bumblebee
 * to agree once you have any approvers; Firestore rules and the backend enforce the same.
 */
class LimitsRepository(
    private val backend: Backend,
    private val store: LocalStore,
    private val requests: RequestRepository,
) {
    /** [approverIds] picks which Bumblebees to ask if it needs a yes; null asks all of them. */
    suspend fun save(limit: AppLimit, message: String = "", approverIds: List<String>? = null): ChangeOutcome {
        val uid = backend.requireUid()
        if (!limit.holdsBack) {
            throw FriendlyException(R.string.error_set_a_limit)
        }
        val current = store.limits.value.firstOrNull { it.packageName == limit.packageName }
        val hasBumblebees = store.approverCount.value > 0
        if (current != null && isLooser(current, limit) && hasBumblebees) {
            requests.create(
                type = RequestType.CHANGE_LIMIT,
                message = message,
                packageName = limit.packageName,
                appName = limit.appName,
                dailyMinutes = limit.dailyMinutes,
                weeklyMinutes = limit.weeklyMinutes,
                schedule = limit.schedule,
                dayMinutes = limit.dayMinutes,
                caps = limit.caps,
                approverIds = approverIds,
            )
            return ChangeOutcome.REQUESTED
        }
        // Rules can't compare hours, days' own limits or caps, so with Bumblebees a change to them
        // goes through the backend, which only accepts stricter ones.
        if (current != null && hasBumblebees && !current.sameUncomparable(limit)) {
            backend.call(
                "saveLimit",
                mapOf(
                    "packageName" to limit.packageName,
                    "appName" to limit.appName,
                    "dailyMinutes" to limit.dailyMinutes,
                    "weeklyMinutes" to limit.weeklyMinutes,
                    "schedule" to limit.schedule?.toFirestore(),
                    "dayMinutes" to limit.dayMinutes.takeIf { it.isNotEmpty() }?.let(DayMinutes::toFirestore),
                    "caps" to limit.caps.map(TimeCap::toFirestore),
                ),
            )
            return ChangeOutcome.APPLIED
        }
        val data = mutableMapOf<String, Any>(
            "packageName" to limit.packageName,
            "appName" to limit.appName,
            "dailyMinutes" to limit.dailyMinutes,
            "weeklyMinutes" to limit.weeklyMinutes,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        limit.schedule?.let { data["schedule"] = it.toFirestore() }
        if (limit.dayMinutes.isNotEmpty()) data["dayMinutes"] = DayMinutes.toFirestore(limit.dayMinutes)
        if (limit.caps.isNotEmpty()) data["caps"] = limit.caps.map(TimeCap::toFirestore)
        if (current == null) data["createdAt"] = FieldValue.serverTimestamp()
        // The local snapshot updates right away; offline writes are queued by Firestore.
        backend.user(uid).collection("limits").document(limit.packageName).set(data).awaitWrite()
        return ChangeOutcome.APPLIED
    }

    suspend fun remove(limit: AppLimit, message: String = "", approverIds: List<String>? = null): ChangeOutcome {
        val uid = backend.requireUid()
        if (store.approverCount.value > 0) {
            requests.create(
                type = RequestType.REMOVE_LIMIT,
                message = message,
                packageName = limit.packageName,
                appName = limit.appName,
                approverIds = approverIds,
            )
            return ChangeOutcome.REQUESTED
        }
        backend.user(uid).collection("limits").document(limit.packageName).delete().awaitWrite()
        return ChangeOutcome.APPLIED
    }

    companion object {
        fun isLooser(old: AppLimit, new: AppLimit): Boolean = LimitRules.isLooser(old, new)
    }
}
