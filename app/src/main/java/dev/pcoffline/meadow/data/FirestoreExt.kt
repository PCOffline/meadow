package dev.pcoffline.meadow.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MeadowFirestore"

/** Live query results via the SDK's own Flow. A failed listener is logged and listens again ([relisten]). */
fun Query.observe(): Flow<QuerySnapshot> = tracedSnapshots().relisten("Query listener")

fun DocumentReference.observe(): Flow<DocumentSnapshot> = tracedSnapshots().relisten("Document listener")

/**
 * Listens again whenever this listener fails, so one dropped by the server (offline for too long,
 * refused by the rules for a moment, signed out mid-way) doesn't stay dead for as long as the
 * process lives. Each failure is logged under [label], then [onFailure] runs before the pause.
 */
fun <T> Flow<T>.relisten(label: String, onFailure: suspend (Throwable) -> Unit = {}): Flow<T> =
    retryWithBackoff { e, waitMs ->
        Log.w(TAG, "$label failed; listening again in ${waitMs / 1_000} s", e)
        onFailure(e)
    }

/**
 * Starts this flow again after every failure, pausing [backoffMs] first: 1 s, then 2 s, 4 s and so
 * on, never more than a minute, so a listener that can't recover (signed out, no longer allowed)
 * asks once a minute instead of spinning. The count is kept for as long as the flow is collected.
 * The SDK's flows end with a [kotlinx.coroutines.CancellationException] wrapping the error; that is
 * retried too, while cancelling the collector still stops it. Failures thrown by the collector
 * itself are not caught: put work that should be retried in an `onEach` before this.
 */
fun <T> Flow<T>.retryWithBackoff(onFailure: suspend (cause: Throwable, waitMs: Long) -> Unit = { _, _ -> }): Flow<T> =
    retryWhen { cause, attempt ->
        val waitMs = backoffMs(attempt)
        onFailure(cause, waitMs)
        delay(waitMs)
        true
    }

/** The pause before listening again after failure number [attempt] (counted from 0). */
fun backoffMs(attempt: Long): Long = minOf(MAX_BACKOFF_MS, 1_000L shl attempt.coerceIn(0L, 6L).toInt())

const val MAX_BACKOFF_MS = 60_000L

/**
 * Waits for the server to confirm a write, but not forever. Offline, the write's task only
 * completes once back online; Firestore has already applied it locally and keeps it queued, so
 * there's nothing to wait for. A write the server rejects still throws.
 */
suspend fun <T> Task<T>.awaitWrite(timeoutMs: Long = WRITE_TIMEOUT_MS) {
    if (!Connectivity.online.value) return
    withTimeoutOrNull(timeoutMs) { await() }
}

const val WRITE_TIMEOUT_MS = 8_000L

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

/**
 * Someone's name card. Pass [account] (your own account doc) for your own profile: private
 * settings and status live there. Profiles not yet migrated still carry them, so fall back.
 */
fun DocumentSnapshot.toProfile(account: DocumentSnapshot? = null): UserProfile {
    val acc = account?.takeIf { it.exists() }?.toAccount()
    val own = account != null
    fun <T> pick(field: String, read: DocumentSnapshot.(String) -> T?): T? =
        account?.takeIf { it.exists() && it.contains(field) }?.read(field) ?: if (own) read(field) else null
    return UserProfile(
        uid = id,
        displayName = getString("displayName") ?: "Friend",
        avatar = toAvatar(),
        bio = getString("bio").orEmpty(),
        friendCode = pick("friendCode") { getString(it) }.orEmpty(),
        approverIds = acc?.approverIds.orEmpty(),
        approveeIds = acc?.approveeIds.orEmpty(),
        pendingRemovals = acc?.pendingRemovals.orEmpty(),
        privacy = Privacy.read { name -> pick("privacy") { getBoolean("privacy.$name") } },
        strictMode = pick("strictMode") { getBoolean(it) } ?: false,
        emergencyUntilMillis = pick("emergencyUntil") { getTimestamp(it)?.toDate()?.time } ?: 0L,
        emergencyPackage = pick("emergencyPackage") { getString(it) },
        emergencyUsesMillis = pick("emergencyUses") { timestampList(it) }.orEmpty(),
    )
}

fun DocumentSnapshot.timestampList(field: String): List<Long> =
    (get(field) as? List<*>)?.filterIsInstance<Timestamp>()?.map { it.toDate().time } ?: emptyList()

fun DocumentSnapshot.toAccount() = Account(
    approverIds = stringList("approverIds"),
    approveeIds = stringList("approveeIds"),
    pendingRemovals = timestampMap("pendingRemovals"),
    friendCode = getString("friendCode").orEmpty(),
    strictMode = getBoolean("strictMode") ?: false,
    privacy = Privacy.read { getBoolean("privacy.$it") },
    emergencyUntilMillis = getTimestamp("emergencyUntil")?.toDate()?.time ?: 0L,
    emergencyPackage = getString("emergencyPackage"),
    emergencyUsesMillis = timestampList("emergencyUses"),
    theme = getString("theme"),
    darkMode = getString("darkMode"),
    acceptedTermsVersion = (getLong("acceptedTerms.version") ?: 0L).toInt(),
)

fun DocumentSnapshot.toLimit() = AppLimit(
    packageName = getString("packageName") ?: id,
    appName = getString("appName") ?: id,
    dailyMinutes = int("dailyMinutes"),
    weeklyMinutes = int("weeklyMinutes"),
    schedule = Schedule.fromFirestore(get("schedule")),
)

fun DocumentSnapshot.toGrant() = Grant(
    id = id,
    packageName = getString("packageName") ?: "",
    minutes = int("minutes"),
    dayKey = getString("dayKey") ?: "",
)

/** A request doc (as its asker) or an inbox copy (as one Bumblebee), keyed by the request's id. */
fun DocumentSnapshot.toRequest() = LimitRequest(
    id = getString("requestId") ?: id,
    requesterId = getString("requesterId") ?: "",
    requesterName = getString("requesterName") ?: "Friend",
    type = RequestType.fromWire(getString("type")),
    packageName = getString("packageName"),
    appName = getString("appName"),
    minutes = int("minutes"),
    dailyMinutes = int("dailyMinutes"),
    weeklyMinutes = int("weeklyMinutes"),
    message = getString("message") ?: "",
    status = RequestStatus.effective(RequestStatus.fromWire(getString("status")), millis("createdAt")),
    approverIds = stringList("approverIds"),
    decidedByName = getString("decidedByName"),
    decisionNote = getString("decisionNote"),
    dayKey = getString("dayKey") ?: "",
    createdAtMillis = millis("createdAt"),
    schedule = Schedule.fromFirestore(get("schedule")),
    requesterFlower = getString("requesterFlower") ?: "🌼",
    myDecision = getString("myDecision"),
    grantedMinutes = int("grantedMinutes"),
    decidedAtMillis = millis("decidedAt"),
    usedMinutes = getLong("usedMinutes")?.toInt(),
    allowedMinutes = getLong("allowedMinutes")?.toInt(),
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
    closed = getBoolean("closed") == true,
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

fun DocumentSnapshot.toSharedApps(): List<SharedAppUsage> =
    (get("apps") as? List<*>)?.mapNotNull { raw ->
        val map = raw as? Map<*, *> ?: return@mapNotNull null
        SharedAppUsage(
            packageName = map["packageName"] as? String ?: return@mapNotNull null,
            appName = map["appName"] as? String ?: "",
            minutes = (map["minutes"] as? Number)?.toInt() ?: 0,
            limitMinutes = (map["limitMinutes"] as? Number)?.toInt() ?: 0,
        )
    } ?: emptyList()

fun SharedAppUsage.toFirestore(): Map<String, Any> =
    mapOf("packageName" to packageName, "appName" to appName, "minutes" to minutes, "limitMinutes" to limitMinutes)

fun DocumentSnapshot.toProgress() = Progress(
    weekChange = getLong("weekChange")?.toInt(),
    calmerWeeks = int("calmerWeeks"),
    keptDays = int("keptDays"),
    limitDays = int("limitDays"),
)

fun Progress.toFirestore(): Map<String, Any?> =
    mapOf("weekChange" to weekChange, "calmerWeeks" to calmerWeeks, "keptDays" to keptDays, "limitDays" to limitDays)
