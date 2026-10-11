package dev.pcoffline.meadow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device copy of everything enforcement needs. Enforcement only ever reads from here,
 * so limits keep working offline, signed out, or while the backend is unreachable.
 * The copy is refreshed from Firestore by [SyncManager].
 */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("meadow_store", Context.MODE_PRIVATE)

    private val _limits = MutableStateFlow(readLimits())
    val limits: StateFlow<List<AppLimit>> = _limits.asStateFlow()

    private val _grants = MutableStateFlow(readGrants())
    val grants: StateFlow<List<Grant>> = _grants.asStateFlow()

    private val _strictMode = MutableStateFlow(prefs.getBoolean(KEY_STRICT, false))
    val strictMode: StateFlow<Boolean> = _strictMode.asStateFlow()

    private val _approverCount = MutableStateFlow(prefs.getInt(KEY_APPROVERS, 0))
    val approverCount: StateFlow<Int> = _approverCount.asStateFlow()

    private val _emergency = MutableStateFlow(readEmergency())
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

    fun setLimits(limits: List<AppLimit>) {
        val array = JSONArray()
        limits.forEach {
            array.put(
                JSONObject()
                    .put("p", it.packageName)
                    .put("n", it.appName)
                    .put("d", it.dailyMinutes)
                    .put("w", it.weeklyMinutes)
            )
        }
        prefs.edit().putString(KEY_LIMITS, array.toString()).apply()
        _limits.value = limits
    }

    fun setGrants(grants: List<Grant>) {
        val array = JSONArray()
        grants.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("p", it.packageName)
                    .put("m", it.minutes)
                    .put("k", it.dayKey)
            )
        }
        prefs.edit().putString(KEY_GRANTS, array.toString()).apply()
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
        prefs.edit()
            .putLong(KEY_EMERGENCY_UNTIL, emergency?.untilMillis ?: 0L)
            .putString(KEY_EMERGENCY_PACKAGE, emergency?.packageName)
            .apply()
        _emergency.value = emergency
    }

    /** When the last offline emergency unlock was taken; offline ones are limited to one per 30 days. */
    var lastOfflineEmergencyMillis: Long
        get() = prefs.getLong(KEY_OFFLINE_EMERGENCY, 0L)
        set(value) = prefs.edit().putLong(KEY_OFFLINE_EMERGENCY, value).apply()

    /** An offline emergency unlock that still has to be reported to the backend. */
    var pendingEmergency: JSONObject?
        get() = prefs.getString(KEY_PENDING_EMERGENCY, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_PENDING_EMERGENCY, value?.toString()).apply()

    private fun readEmergency(): Emergency? {
        val until = prefs.getLong(KEY_EMERGENCY_UNTIL, 0L)
        return if (until > 0L) Emergency(until, prefs.getString(KEY_EMERGENCY_PACKAGE, null)) else null
    }

    private fun readLimits(): List<AppLimit> = parseArray(prefs.getString(KEY_LIMITS, null)) {
        AppLimit(
            packageName = it.getString("p"),
            appName = it.optString("n", it.getString("p")),
            dailyMinutes = it.optInt("d", 0),
            weeklyMinutes = it.optInt("w", 0),
        )
    }

    private fun readGrants(): List<Grant> = parseArray(prefs.getString(KEY_GRANTS, null)) {
        Grant(
            id = it.getString("id"),
            packageName = it.getString("p"),
            minutes = it.optInt("m", 0),
            dayKey = it.getString("k"),
        )
    }

    private fun <T> parseArray(raw: String?, map: (JSONObject) -> T): List<T> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { map(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_LIMITS = "limits"
        const val KEY_GRANTS = "grants"
        const val KEY_STRICT = "strict_mode"
        const val KEY_APPROVERS = "approver_count"
        const val KEY_OWNER = "owner_uid"
        const val KEY_PROTECTION = "last_protection"
        const val KEY_WARNED = "warned_keys"
        const val KEY_EMERGENCY_UNTIL = "emergency_until"
        const val KEY_EMERGENCY_PACKAGE = "emergency_package"
        const val KEY_OFFLINE_EMERGENCY = "offline_emergency_at"
        const val KEY_PENDING_EMERGENCY = "pending_emergency"
    }
}
