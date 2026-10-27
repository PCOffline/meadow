package dev.pcoffline.meadow.data

import kotlinx.serialization.Serializable

/**
 * A committed limit for one app. A value of 0 means "no limit" for that period; a null
 * [schedule] means the app may be used at any hour.
 */
@Serializable
data class AppLimit(
    val packageName: String,
    val appName: String,
    val dailyMinutes: Int,
    val weeklyMinutes: Int,
    val schedule: Schedule? = null,
)

/** Extra minutes a Bumblebee granted for a single day. */
@Serializable
data class Grant(
    val id: String,
    val packageName: String,
    val minutes: Int,
    val dayKey: String,
)

/** How someone looks: an emoji on a soft coloured circle, or their photo. */
data class Avatar(val emoji: String, val color: String, val photoUrl: String?) {
    companion object {
        val Default = Avatar("🌼", "leaf", null)
    }
}

/**
 * What someone shares with their Bumblebees, each part on its own. Protections, strict mode,
 * extra time and earlier days' screen time are never shared.
 */
data class Privacy(
    /** Which apps have limits, and what they are. */
    val shareLimits: Boolean = true,
    /** Today's total screen time. */
    val shareTime: Boolean = true,
    /** The apps used most today, and for how long. */
    val shareApps: Boolean = true,
    /**
     * How the weeks compare, and how often limits were kept: see [Progress]. It sums up earlier
     * days, which otherwise never leave the phone, so it's only shared when chosen.
     */
    val shareProgress: Boolean = false,
) {
    /** As stored on the account, under "privacy". */
    fun toFirestore(): Map<String, Boolean> =
        mapOf("shareLimits" to shareLimits, "shareTime" to shareTime, "shareApps" to shareApps, "shareProgress" to shareProgress)

    companion object {
        /** Reads the choices through [field] ("shareTime" and so on); one never made is the default. */
        fun read(field: (String) -> Boolean?): Privacy = Privacy(
            shareLimits = field("shareLimits") ?: true,
            shareTime = field("shareTime") ?: true,
            shareApps = field("shareApps") ?: true,
            shareProgress = field("shareProgress") ?: false,
        )
    }
}

/**
 * Owner-only: who looks after whom, settings and status that follow the person between phones.
 * Nobody else can read it.
 */
data class Account(
    val approverIds: List<String> = emptyList(),
    val approveeIds: List<String> = emptyList(),
    /** Bumblebees being removed, with when it takes effect. */
    val pendingRemovals: Map<String, Long> = emptyMap(),
    val friendCode: String = "",
    val strictMode: Boolean = false,
    val privacy: Privacy = Privacy(),
    val emergencyUntilMillis: Long = 0L,
    val emergencyPackage: String? = null,
    val emergencyUsesMillis: List<Long> = emptyList(),
    val theme: String? = null,
    val darkMode: String? = null,
    val acceptedTermsVersion: Int = 0,
)

/**
 * A person. Everyone in a circle sees only the name card ([displayName], [avatar], [bio]); the
 * rest is only known for your own profile, and empty or default for anyone else's.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val avatar: Avatar,
    val bio: String,
    val friendCode: String = "",
    val approverIds: List<String> = emptyList(),
    val approveeIds: List<String> = emptyList(),
    val pendingRemovals: Map<String, Long> = emptyMap(),
    val privacy: Privacy = Privacy(),
    val strictMode: Boolean = false,
    val emergencyUntilMillis: Long = 0L,
    val emergencyPackage: String? = null,
    val emergencyUsesMillis: List<Long> = emptyList(),
) {
    /** Emergency unlocks still available in the rolling 30-day window. */
    val emergenciesLeft: Int
        get() {
            val since = System.currentTimeMillis() - Emergency.WINDOW_MS
            return (Emergency.ALLOWANCE - emergencyUsesMillis.count { it > since }).coerceAtLeast(0)
        }
}

/** Limits lifted without approval until [untilMillis], for one app or (when null) all of them. */
@Serializable
data class Emergency(val untilMillis: Long, val packageName: String?) {
    fun isActive(now: Long = System.currentTimeMillis()) = untilMillis > now

    fun covers(pkg: String, now: Long = System.currentTimeMillis()) =
        isActive(now) && (packageName == null || packageName == pkg)

    companion object {
        /** Must match EMERGENCY_ALLOWANCE / EMERGENCY_WINDOW_MS in the backend. */
        const val ALLOWANCE = 3
        const val WINDOW_MS = 30L * 24 * 60 * 60_000
        val MINUTE_OPTIONS = listOf(15, 30, 60)
    }
}

enum class RequestType(val wire: String) {
    EXTRA_TIME("extra_time"),
    CHANGE_LIMIT("change_limit"),
    REMOVE_LIMIT("remove_limit"),

    /** Only on old requests: strict mode now turns off without asking. */
    DISABLE_STRICT_MODE("disable_strict_mode");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: EXTRA_TIME
    }
}

enum class RequestStatus(val wire: String) {
    PENDING("pending"), APPROVED("approved"), DENIED("denied"), CANCELLED("cancelled"), EXPIRED("expired");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: PENDING

        /** Unanswered requests expire after this long; must match REQUEST_TTL_MS in requests.ts. */
        const val TTL_MS = 30 * 60_000L

        /** A pending request past its time counts as expired, even before the backend settles it. */
        fun effective(status: RequestStatus, createdAtMillis: Long, now: Long = System.currentTimeMillis()) =
            if (status == PENDING && createdAtMillis > 0L && now - createdAtMillis >= TTL_MS) EXPIRED else status
    }
}

/**
 * A request, as its asker sees it ([approverIds] lists everyone asked) or as one Bumblebee sees
 * their own copy (no [approverIds]; [myDecision] is their own answer, and an answer from someone
 * else only shows as answered).
 */
data class LimitRequest(
    val id: String,
    val requesterId: String,
    val requesterName: String,
    val type: RequestType,
    val packageName: String?,
    val appName: String?,
    val minutes: Int,
    val dailyMinutes: Int,
    val weeklyMinutes: Int,
    val message: String,
    val status: RequestStatus,
    val approverIds: List<String>,
    val decidedByName: String?,
    val decisionNote: String?,
    val dayKey: String,
    val createdAtMillis: Long,
    val schedule: Schedule? = null,
    val requesterFlower: String = "🌼",
    /** "yes" or "no" when this Bumblebee answered it themselves. */
    val myDecision: String? = null,
    val grantedMinutes: Int = 0,
    val decidedAtMillis: Long = 0L,
    /** Minutes spent on the app that day when asking, and minutes the day allowed in all. */
    val usedMinutes: Int? = null,
    val allowedMinutes: Int? = null,
)

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, CANCELLED }

/** An invitation from [fromUid] asking [toUid] to become their Bumblebee. */
data class Invite(
    val id: String,
    val fromUid: String,
    val fromName: String,
    val fromFlower: String,
    val toUid: String,
    val toName: String,
    val status: InviteStatus,
    val createdAtMillis: Long,
    /** The sender offers to be the Bumblebee, rather than asking the receiver to be theirs. */
    val offer: Boolean = false,
)

/** A one-time link that makes whoever accepts it the creator's Bumblebee. */
data class InviteLink(val token: String, val url: String, val expiresAtMillis: Long)

enum class InviteLinkStatus { OK, EXPIRED, USED, OWN, ALREADY }

data class InviteLinkPreview(val ownerName: String, val ownerFlower: String, val status: InviteLinkStatus)

data class Chat(
    val id: String,
    val participants: List<String>,
    val names: Map<String, String>,
    val avatars: Map<String, Avatar>,
    val lastMessage: String,
    val lastMessageAtMillis: Long,
    val lastSenderId: String?,
    val lastReadMillis: Map<String, Long>,
    /** The two parted ways: the chat is read-only and no longer carries their names or pictures. */
    val closed: Boolean = false,
) {
    fun otherId(me: String) = participants.firstOrNull { it != me } ?: me
    fun otherName(me: String) = names[otherId(me)] ?: "Friend"
    fun otherAvatar(me: String) = avatars[otherId(me)] ?: Avatar.Default
    fun isUnread(me: String) = lastSenderId != null && lastSenderId != me &&
        lastMessageAtMillis > (lastReadMillis[me] ?: 0L)
}

enum class MessageKind { TEXT, REQUEST, SYSTEM }

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val kind: MessageKind,
    val requestId: String?,
    val createdAtMillis: Long,
    val pending: Boolean,
)

/** Today's usage of a person, as uploaded for their Bumblebees. */
data class SharedUsage(
    val dayKey: String,
    val totalMinutes: Int,
    val apps: List<SharedAppUsage>,
)

data class SharedAppUsage(
    val packageName: String,
    val appName: String,
    val minutes: Int,
)

/** Emoji people can pick as their avatar. Must match FLOWERS in the backend's schemas.ts. */
val FlowerAvatars = listOf(
    "🌼", "🌸", "🌻", "🌷", "🌹", "🪻", "🌺", "🪷", "🍀", "🌿", "🍄", "🌵",
    "🐝", "🐞", "🦋", "🐌", "🐛", "🐸", "🐰", "🦔", "🐿️", "🦊", "🐱", "🐻", "🐼", "🐢", "🐥", "🦉",
)
