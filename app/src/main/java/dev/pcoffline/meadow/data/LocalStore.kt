package dev.pcoffline.meadow.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate

/** An offline emergency unlock that still has to be reported to the backend. */
@Serializable
data class PendingEmergency(
    val minutes: Int,
    val reason: String,
    val packageName: String?,
    val appName: String?,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** The account that took it; null if taken signed out, then it's reported by whoever signs in. */
    val ownerUid: String? = null,
) {
    /** Lets the backend recognise a retried report of the same unlock. */
    val reportId: String get() = "offline-$startedAt"
}

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

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, null))
    val theme: StateFlow<String?> = _theme.asStateFlow()

    private val _darkMode = MutableStateFlow(prefs.getString(KEY_DARK_MODE, null))
    val darkMode: StateFlow<String?> = _darkMode.asStateFlow()

    fun setAppearance(theme: String?, darkMode: String?) {
        prefs.edit {
            putString(KEY_THEME, theme)
            putString(KEY_DARK_MODE, darkMode)
        }
        _theme.value = theme
        _darkMode.value = darkMode
    }

    private val _autoUpdate = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE, true))

    /** Whether Meadow checks GitHub for new versions on its own. */
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    fun setAutoUpdate(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_UPDATE, enabled) }
        _autoUpdate.value = enabled
    }

    private val _weekRule = MutableStateFlow(
        WeekRule(
            first = DayOfWeek.of(prefs.getInt(KEY_WEEK_FIRST, DayOfWeek.MONDAY.value)),
            previous = DayOfWeek.of(prefs.getInt(KEY_WEEK_PREVIOUS, prefs.getInt(KEY_WEEK_FIRST, DayOfWeek.MONDAY.value))),
            limitsFrom = prefs.getString(KEY_WEEK_LIMITS_FROM, null)?.let(Days::parse),
        ),
    )

    /** The day weeks start on, for Growth and weekly limits. */
    val weekRule: StateFlow<WeekRule> = _weekRule.asStateFlow()

    fun setWeekStart(day: DayOfWeek, today: LocalDate = Days.today()) {
        val rule = _weekRule.value.changedTo(day, today)
        prefs.edit {
            putInt(KEY_WEEK_FIRST, rule.first.value)
            putInt(KEY_WEEK_PREVIOUS, rule.previous.value)
            putString(KEY_WEEK_LIMITS_FROM, rule.limitsFrom?.let(Days::key))
        }
        _weekRule.value = rule
    }

    private val _privacy = MutableStateFlow(Privacy(prefs.getBoolean(KEY_SHARE_USAGE, true)))

    /** Mirrors the profile's privacy choices so background work can respect them offline. */
    val privacy: StateFlow<Privacy> = _privacy.asStateFlow()

    fun setPrivacy(privacy: Privacy) {
        prefs.edit { putBoolean(KEY_SHARE_USAGE, privacy.shareUsage) }
        _privacy.value = privacy
    }

    /** The address a sign-in link was last sent to from this phone, to finish signing in with it. */
    var pendingLinkEmail: String?
        get() = prefs.getString(KEY_LINK_EMAIL, null)
        set(value) = prefs.edit { putString(KEY_LINK_EMAIL, value) }

    /** The last release version a notification was shown for, so each one is offered once. */
    var notifiedUpdate: String?
        get() = prefs.getString(KEY_NOTIFIED_UPDATE, null)
        set(value) = prefs.edit { putString(KEY_NOTIFIED_UPDATE, value) }

    private val _acceptedTerms = MutableStateFlow(prefs.getInt(KEY_TERMS, 0))

    /** The version of the terms and privacy notice accepted on this account (0 = none yet). */
    val acceptedTerms: StateFlow<Int> = _acceptedTerms.asStateFlow()

    var acceptedTermsVersion: Int
        get() = _acceptedTerms.value
        set(value) {
            prefs.edit { putInt(KEY_TERMS, value) }
            _acceptedTerms.value = value
        }

    /**
     * Forgets what belonged to the previous account when a different one signs in. Limits stay
     * until the new account's arrive, so signing in elsewhere is never a way around them.
     */
    fun switchAccount(uid: String) {
        val previous = ownerUid
        ownerUid = uid
        if (previous == null || previous == uid) return
        setGrants(emptyList())
        acceptedTermsVersion = 0
        lastReportedProtection = null
        warnedKeys = emptySet()
        lastAskedBees = emptySet()
        pendingEmergency?.let { if (it.ownerUid != null && it.ownerUid != uid) pendingEmergency = null }
    }

    /** Wipes everything this phone holds, after the account is deleted. */
    fun clearAll() {
        prefs.edit { clear() }
        _limits.value = emptyList()
        _grants.value = emptyList()
        _strictMode.value = false
        _approverCount.value = 0
        _emergency.value = null
        _pendingInvite.value = null
        _privacy.value = Privacy()
        _theme.value = null
        _darkMode.value = null
        _autoUpdate.value = true
        _weekRule.value = WeekRule()
        _acceptedTerms.value = 0
    }

    private val _pendingInvite = MutableStateFlow(prefs.getString(KEY_PENDING_INVITE, null))

    /** An invite link opened before (or while) setting up; offered once a profile exists. */
    val pendingInvite: StateFlow<String?> = _pendingInvite.asStateFlow()

    fun setPendingInvite(token: String?) {
        prefs.edit { putString(KEY_PENDING_INVITE, token) }
        _pendingInvite.value = token
    }

    var ownerUid: String?
        get() = prefs.getString(KEY_OWNER, null)
        set(value) = prefs.edit { putString(KEY_OWNER, value) }

    /** The protections last seen switched on, to notice one being switched off. */
    var lastReportedProtection: String?
        get() = prefs.getString(KEY_PROTECTION, null)
        set(value) = prefs.edit { putString(KEY_PROTECTION, value) }

    /** When this phone last told the backend it's alive. */
    var lastHeartbeatMillis: Long
        get() = prefs.getLong(KEY_HEARTBEAT, 0L)
        set(value) = prefs.edit { putLong(KEY_HEARTBEAT, value) }

    /** The Bumblebees last asked for something, picked first next time. */
    var lastAskedBees: Set<String>
        get() = prefs.getStringSet(KEY_LAST_ASKED, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_LAST_ASKED, value) }

    var warnedKeys: Set<String>
        get() = prefs.getStringSet(KEY_WARNED, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_WARNED, value) }

    /** When the last offline emergency unlock was taken; offline ones are limited to one per 30 days. */
    var lastOfflineEmergencyMillis: Long
        get() = prefs.getLong(KEY_OFFLINE_EMERGENCY, 0L)
        set(value) = prefs.edit { putLong(KEY_OFFLINE_EMERGENCY, value) }

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
        prefs.edit { putBoolean(KEY_STRICT, enabled) }
        _strictMode.value = enabled
    }

    fun setApproverCount(count: Int) {
        prefs.edit { putInt(KEY_APPROVERS, count) }
        _approverCount.value = count
    }

    fun setEmergency(emergency: Emergency?) {
        write(KEY_EMERGENCY, emergency)
        _emergency.value = emergency
    }

    private inline fun <reified T> read(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> write(key: String, value: T?) {
        prefs.edit {
            if (value == null) remove(key) else putString(key, json.encodeToString(value))
        }
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
        const val KEY_LAST_ASKED = "last_asked_bees"
        const val KEY_HEARTBEAT = "last_heartbeat"
        const val KEY_EMERGENCY = "emergency"
        const val KEY_OFFLINE_EMERGENCY = "offline_emergency_at"
        const val KEY_PENDING_EMERGENCY = "pending_emergency_v2"
        const val KEY_PENDING_INVITE = "pending_invite"
        const val KEY_THEME = "theme"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_WEEK_FIRST = "week_first"
        const val KEY_WEEK_PREVIOUS = "week_previous"
        const val KEY_WEEK_LIMITS_FROM = "week_limits_from"
        const val KEY_SHARE_USAGE = "share_usage"
        const val KEY_TERMS = "accepted_terms_version"
        const val KEY_NOTIFIED_UPDATE = "notified_update"
        const val KEY_LINK_EMAIL = "link_email"
    }
}
