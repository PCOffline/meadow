package dev.pcoffline.meadow.data

/** A committed limit for one app. A value of 0 means "no limit" for that period. */
data class AppLimit(
    val packageName: String,
    val appName: String,
    val dailyMinutes: Int,
    val weeklyMinutes: Int,
)

/** Extra minutes an approver granted for a single day. */
data class Grant(
    val id: String,
    val packageName: String,
    val minutes: Int,
    val dayKey: String,
)

data class UserProfile(
    val uid: String,
    val displayName: String,
    val flower: String,
    val friendCode: String,
    val approverIds: List<String>,
    val approveeIds: List<String>,
    val strictMode: Boolean,
    val protection: Map<String, Boolean>,
    val lastHeartbeatMillis: Long?,
)

enum class RequestType(val wire: String) {
    EXTRA_TIME("extra_time"),
    CHANGE_LIMIT("change_limit"),
    REMOVE_LIMIT("remove_limit"),
    DISABLE_STRICT_MODE("disable_strict_mode"),
    ADD_APPROVER("add_approver"),
    REMOVE_APPROVER("remove_approver");

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
    val targetUid: String?,
    val targetName: String?,
    val message: String,
    val status: RequestStatus,
    val approverIds: List<String>,
    val decidedByName: String?,
    val decisionNote: String?,
    val dayKey: String,
    val createdAtMillis: Long,
) {
    val summary: String
        get() = when (type) {
            RequestType.EXTRA_TIME -> "$minutes more minutes on ${appName ?: "an app"}"
            RequestType.CHANGE_LIMIT -> "Change ${appName ?: "an app"} to ${limitText(dailyMinutes, weeklyMinutes)}"
            RequestType.REMOVE_LIMIT -> "Remove the limit on ${appName ?: "an app"}"
            RequestType.DISABLE_STRICT_MODE -> "Turn off strict mode"
            RequestType.ADD_APPROVER -> "Invite ${targetName ?: "someone"} as an approver"
            RequestType.REMOVE_APPROVER -> "Remove ${targetName ?: "an approver"} as an approver"
        }

    companion object {
        fun limitText(daily: Int, weekly: Int): String = buildList {
            if (daily > 0) add("${formatMinutes(daily)} a day")
            if (weekly > 0) add("${formatMinutes(weekly)} a week")
        }.ifEmpty { listOf("no limit") }.joinToString(" · ")
    }
}

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, CANCELLED }

/** An invitation from [fromUid] asking [toUid] to become their approver. */
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

data class Chat(
    val id: String,
    val participants: List<String>,
    val names: Map<String, String>,
    val flowers: Map<String, String>,
    val lastMessage: String,
    val lastMessageAtMillis: Long,
    val lastSenderId: String?,
    val lastReadMillis: Map<String, Long>,
) {
    fun otherId(me: String) = participants.firstOrNull { it != me } ?: me
    fun otherName(me: String) = names[otherId(me)] ?: "Friend"
    fun otherFlower(me: String) = flowers[otherId(me)] ?: "🌼"
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

/** Per-app usage of a person for one day, as uploaded for their approvers. */
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

fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "0m"
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    if (totalMinutes == 0 && millis > 0) return "<1m"
    return formatMinutes(totalMinutes)
}

/** Flowers people can pick as their avatar. */
val FlowerAvatars = listOf("🌼", "🌸", "🌻", "🌷", "🌹", "🪻", "🌺", "🍀", "🌿", "🍄")
