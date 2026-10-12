package dev.pcoffline.meadow.data

import kotlinx.serialization.Serializable

/** A committed limit for one app. A value of 0 means "no limit" for that period. */
@Serializable
data class AppLimit(
    val packageName: String,
    val appName: String,
    val dailyMinutes: Int,
    val weeklyMinutes: Int,
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

/** What someone shares with their Bumblebees. */
data class Privacy(val shareUsage: Boolean = true, val shareProtection: Boolean = true)

/**
 * Owner-only: who looks after whom, and app preferences that follow the person between phones.
 * Nobody else can read it.
 */
data class Account(
    val approverIds: List<String> = emptyList(),
    val approveeIds: List<String> = emptyList(),
    val theme: String? = null,
    val darkMode: String? = null,
    val acceptedTermsVersion: Int = 0,
)

/**
 * A person's profile. [approverIds] and [approveeIds] are only known for your own profile;
 * for anyone else they are empty, because those lists are private.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val avatar: Avatar,
    val bio: String,
    val friendCode: String,
    val approverIds: List<String>,
    val approveeIds: List<String>,
    val privacy: Privacy,
    val strictMode: Boolean,
    val protection: Map<String, Boolean>,
    val lastHeartbeatMillis: Long?,
    val emergencyUntilMillis: Long,
    val emergencyPackage: String?,
    val emergencyUsesMillis: List<Long>,
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
    DISABLE_STRICT_MODE("disable_strict_mode");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: EXTRA_TIME
    }
}

enum class RequestStatus(val wire: String) {
    PENDING("pending"), APPROVED("approved"), DENIED("denied"), CANCELLED("cancelled"), EXPIRED("expired");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

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

/** Per-app usage of a person for one day, as uploaded for their Bumblebees. */
data class SharedUsage(
    val dayKey: String,
    val totalMinutes: Int,
    val apps: List<SharedAppUsage>,
)

data class SharedAppUsage(
    val packageName: String,
    val appName: String,
    val minutes: Int,
    val limitMinutes: Int,
)

/** Emoji people can pick as their avatar. Must match FLOWERS in the backend's schemas.ts. */
val FlowerAvatars = listOf(
    "🌼", "🌸", "🌻", "🌷", "🌹", "🪻", "🌺", "🪷", "🍀", "🌿", "🍄", "🌵",
    "🐝", "🐞", "🦋", "🐌", "🐛", "🐸", "🐰", "🦔", "🐿️", "🦊", "🐱", "🐻", "🐼", "🐢", "🐥", "🦉",
)
