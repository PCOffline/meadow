package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private const val TAG = "MeadowFirestore"

/** Live query results via the SDK's own Flow; listener errors are logged instead of crashing collectors. */
fun Query.observe(): Flow<QuerySnapshot> =
    snapshots().catch { Log.w(TAG, "Query listener failed", it) }

fun DocumentReference.observe(): Flow<DocumentSnapshot> =
    snapshots().catch { Log.w(TAG, "Document listener failed", it) }

fun DocumentSnapshot.millis(field: String): Long =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L

fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

fun DocumentSnapshot.int(field: String): Int = (getLong(field) ?: 0L).toInt()

fun DocumentSnapshot.stringMap(field: String): Map<String, String> =
    (get(field) as? Map<*, *>)?.entries
        ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
        ?.toMap() ?: emptyMap()

fun DocumentSnapshot.timestampMap(field: String): Map<String, Long> =
    (get(field) as? Map<*, *>)?.entries
        ?.mapNotNull { (k, v) -> if (k is String && v is Timestamp) k to v.toDate().time else null }
        ?.toMap() ?: emptyMap()

fun DocumentSnapshot.toAvatar() = Avatar(
    emoji = getString("flower") ?: Avatar.Default.emoji,
    color = getString("avatarColor") ?: Avatar.Default.color,
    photoUrl = getString("photoUrl"),
)

/** Someone's public profile. Pass [account] for your own profile to fill in the private lists. */
fun DocumentSnapshot.toProfile(account: Account? = null) = UserProfile(
    uid = id,
    displayName = getString("displayName") ?: "Friend",
    avatar = toAvatar(),
    bio = getString("bio").orEmpty(),
    friendCode = getString("friendCode") ?: "",
    approverIds = account?.approverIds.orEmpty(),
    approveeIds = account?.approveeIds.orEmpty(),
    privacy = Privacy(
        shareUsage = getBoolean("privacy.shareUsage") ?: true,
        shareProtection = getBoolean("privacy.shareProtection") ?: true,
    ),
    strictMode = getBoolean("strictMode") ?: false,
    protection = (get("protection") as? Map<*, *>)?.entries
        ?.mapNotNull { (k, v) -> if (k is String && v is Boolean) k to v else null }
        ?.toMap() ?: emptyMap(),
    lastHeartbeatMillis = getTimestamp("lastHeartbeat")?.toDate()?.time,
    emergencyUntilMillis = getTimestamp("emergencyUntil")?.toDate()?.time ?: 0L,
    emergencyPackage = getString("emergencyPackage"),
    emergencyUsesMillis = (get("emergencyUses") as? List<*>)?.filterIsInstance<Timestamp>()?.map { it.toDate().time }
        ?: emptyList(),
)

fun DocumentSnapshot.toAccount() = Account(
    approverIds = stringList("approverIds"),
    approveeIds = stringList("approveeIds"),
    theme = getString("theme"),
    darkMode = getString("darkMode"),
    acceptedTermsVersion = (getLong("acceptedTerms.version") ?: 0L).toInt(),
)

fun DocumentSnapshot.toLimit() = AppLimit(
    packageName = getString("packageName") ?: id,
    appName = getString("appName") ?: id,
    dailyMinutes = int("dailyMinutes"),
    weeklyMinutes = int("weeklyMinutes"),
)

fun DocumentSnapshot.toGrant() = Grant(
    id = id,
    packageName = getString("packageName") ?: "",
    minutes = int("minutes"),
    dayKey = getString("dayKey") ?: "",
)

fun DocumentSnapshot.toRequest() = LimitRequest(
    id = id,
    requesterId = getString("requesterId") ?: "",
    requesterName = getString("requesterName") ?: "Friend",
    type = RequestType.fromWire(getString("type")),
    packageName = getString("packageName"),
    appName = getString("appName"),
    minutes = int("minutes"),
    dailyMinutes = int("dailyMinutes"),
    weeklyMinutes = int("weeklyMinutes"),
    message = getString("message") ?: "",
    status = RequestStatus.fromWire(getString("status")),
    approverIds = stringList("approverIds"),
    decidedByName = getString("decidedByName"),
    decisionNote = getString("decisionNote"),
    dayKey = getString("dayKey") ?: "",
    createdAtMillis = millis("createdAt"),
)

fun DocumentSnapshot.toInvite() = Invite(
    id = id,
    fromUid = getString("fromUid") ?: "",
    fromName = getString("fromName") ?: "Friend",
    fromFlower = getString("fromFlower") ?: "🌼",
    toUid = getString("toUid") ?: "",
    toName = getString("toName") ?: "Friend",
    status = when (getString("status")) {
        "accepted" -> InviteStatus.ACCEPTED
        "declined" -> InviteStatus.DECLINED
        "cancelled" -> InviteStatus.CANCELLED
        else -> InviteStatus.PENDING
    },
    createdAtMillis = millis("createdAt"),
)

fun DocumentSnapshot.toChat() = Chat(
    id = id,
    participants = stringList("participants"),
    names = stringMap("names"),
    avatars = (get("avatars") as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
        val m = v as? Map<*, *> ?: return@mapNotNull null
        (k as? String ?: return@mapNotNull null) to Avatar(
            emoji = m["flower"] as? String ?: Avatar.Default.emoji,
            color = m["color"] as? String ?: Avatar.Default.color,
            photoUrl = m["photo"] as? String,
        )
    }?.toMap() ?: emptyMap(),
    lastMessage = getString("lastMessage") ?: "",
    lastMessageAtMillis = millis("lastMessageAt"),
    lastSenderId = getString("lastSenderId"),
    lastReadMillis = timestampMap("lastRead"),
)

fun DocumentSnapshot.toMessage() = ChatMessage(
    id = id,
    senderId = getString("senderId") ?: "",
    text = getString("text") ?: "",
    kind = when (getString("kind")) {
        "request" -> MessageKind.REQUEST
        "system" -> MessageKind.SYSTEM
        else -> MessageKind.TEXT
    },
    requestId = getString("requestId"),
    createdAtMillis = millis("createdAt"),
    pending = metadata.hasPendingWrites(),
)

fun DocumentSnapshot.toSharedUsage(): SharedUsage {
    val apps = (get("apps") as? List<*>)?.mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        SharedAppUsage(
            packageName = map["packageName"] as? String ?: return@mapNotNull null,
            appName = map["appName"] as? String ?: "",
            minutes = (map["minutes"] as? Number)?.toInt() ?: 0,
            limitMinutes = (map["limitMinutes"] as? Number)?.toInt() ?: 0,
        )
    } ?: emptyList()
    return SharedUsage(dayKey = id, totalMinutes = int("totalMinutes"), apps = apps)
}
