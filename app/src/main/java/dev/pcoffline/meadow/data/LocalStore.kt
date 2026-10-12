package dev.pcoffline.meadow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** An offline emergency unlock that still has to be reported to the backend. */
@Serializable
data class PendingEmergency(
    val minutes: Int,
    val reason: String,
    val packageName: String?,
    val appName: String?,
    val startedAt: Long,
    val endedAt: Long? = null,
)

/**
 * On-device copy of everything enforcement needs. Enforcement only ever reads from here,
 * so limits keep working offline, signed out, or while the backend is unreachable.
 * The copy is refreshed from Firestore by [SyncManager].
 *
 * SharedPreferences (rather than DataStore) on purpose: enforcement must have the limits
 * synchronously the moment the process starts, before any coroutine has run.
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("meadow_store", Context.MODE_PRIVATE)

    private val _limits = MutableStateFlow(read<List<AppLimit>>(KEY_LIMITS) ?: emptyList())
    val limits: StateFlow<List<AppLimit>> = _limits.asStateFlow()

    private val _grants = MutableStateFlow(read<List<Grant>>(KEY_GRANTS) ?: emptyList())
    val grants: StateFlow<List<Grant>> = _grants.asStateFlow()

    private val _strictMode = MutableStateFlow(prefs.getBoolean(KEY_STRICT, false))
    val strictMode: StateFlow<Boolean> = _strictMode.asStateFlow()

    private val _approverCount = MutableStateFlow(prefs.getInt(KEY_APPROVERS, 0))
    val approverCount: StateFlow<Int> = _approverCount.asStateFlow()

    private val _emergency = MutableStateFlow(read<Emergency>(KEY_EMERGENCY))
    val emergency: StateFlow<Emergency?> = _emergency.asStateFlow()

    var ownerUid: String?
        get() = prefs.getString(KEY_OWNER, null)
        set(value) = prefs.edit().putString(KEY_OWNER, value).apply()

    var lastReportedProtection: String?
        get() = prefs.getString(KEY_PROTECTION, null)
        set(value) = prefs.edit().putString(KEY_PROTECTION, value).apply()

    var warnedKeys: Set<String>
        get() = prefs.getStringSet(KEY_WARNED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_WARNED, value).apply()

    /** When the last offline emergency unlock was taken; offline ones are limited to one per 30 days. */
    var lastOfflineEmergencyMillis: Long
        get() = prefs.getLong(KEY_OFFLINE_EMERGENCY, 0L)
        set(value) = prefs.edit().putLong(KEY_OFFLINE_EMERGENCY, value).apply()

    var pendingEmergency: PendingEmergency?
        get() = read(KEY_PENDING_EMERGENCY)
        set(value) = write(KEY_PENDING_EMERGENCY, value)

    fun setLimits(limits: List<AppLimit>) {
        write(KEY_LIMITS, limits)
        _limits.value = limits
    }

    fun setGrants(grants: List<Grant>) {
        write(KEY_GRANTS, grants)
        _grants.value = grants
    }

    fun setStrictMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STRICT, enabled).apply()
        _strictMode.value = enabled
    }

    fun setApproverCount(count: Int) {
        prefs.edit().putInt(KEY_APPROVERS, count).apply()
        _approverCount.value = count
    }

    fun setEmergency(emergency: Emergency?) {
        write(KEY_EMERGENCY, emergency)
        _emergency.value = emergency
    }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> write(key: String, value: T?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, json.encodeToString(value))
        }.apply()
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        const val KEY_LIMITS = "limits_v2"
        const val KEY_GRANTS = "grants_v2"
        const val KEY_STRICT = "strict_mode"
        const val KEY_APPROVERS = "approver_count"
        const val KEY_OWNER = "owner_uid"
        const val KEY_PROTECTION = "last_protection"
        const val KEY_WARNED = "warned_keys"
        const val KEY_EMERGENCY = "emergency"
        const val KEY_OFFLINE_EMERGENCY = "offline_emergency_at"
        const val KEY_PENDING_EMERGENCY = "pending_emergency_v2"
    }
}
