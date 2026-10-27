package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges.INCLUDE
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

private const val TAG = "MeadowFirestore"

/** Shares how someone is doing, part by part, so their Bumblebees can see it. */
class UsageShareRepository(private val backend: Backend) {

    /**
     * Uploads today's copy of each part [privacy] shares, over yesterday's: only the latest is ever
     * kept, so earlier days never leave the phone. [today] and [progress] are only read for parts
     * that are shared. A part that isn't shared was taken down when it was turned off ([takeDown]).
     */
    suspend fun share(privacy: Privacy, today: () -> SharedUsage, progress: () -> Progress) {
        val uid = backend.uid ?: return
        val user = backend.user(uid)
        val usage = if (privacy.shareTime || privacy.shareApps) today() else null
        val updatedAt = "updatedAt" to FieldValue.serverTimestamp()
        val parts = listOfNotNull(
            usage?.let { u ->
                Sharing.minutes(privacy, u)?.let { user.collection(TIME).document(TODAY) to mapOf("dayKey" to u.dayKey, "totalMinutes" to it, updatedAt) }
            },
            usage?.let { u ->
                Sharing.apps(privacy, u)?.let { apps ->
                    user.collection(APPS).document(TODAY) to mapOf("dayKey" to u.dayKey, "apps" to apps.map { it.toFirestore() }, updatedAt)
                }
            },
            if (privacy.shareProgress) user.collection(PROGRESS).document(PROGRESS_DOC) to progress().toFirestore() + updatedAt else null,
        )
        if (parts.isEmpty()) return
        val batch = backend.db.batch()
        parts.forEach { (doc, data) -> batch.set(doc, data) }
        batch.commit().awaitWrite()
    }

    /**
     * Takes down what's shared of each part [privacy] doesn't share. Offline, it's kept and done
     * once back online, even if Meadow is closed meanwhile.
     */
    suspend fun takeDown(privacy: Privacy) {
        val uid = backend.uid ?: return
        val user = backend.user(uid)
        val parts = listOfNotNull(
            user.collection(TIME).document(TODAY).takeIf { !privacy.shareTime },
            user.collection(APPS).document(TODAY).takeIf { !privacy.shareApps },
            user.collection(PROGRESS).document(PROGRESS_DOC).takeIf { !privacy.shareProgress },
        )
        if (parts.isEmpty()) return
        val batch = backend.db.batch()
        parts.forEach { batch.delete(it) }
        batch.commit().awaitWrite()
    }

    /** Tells the backend this phone is alive. Only when, never what is switched on. */
    suspend fun heartbeat() {
        val uid = backend.uid ?: return
        val ref = backend.heartbeat(uid)
        try {
            ref.update("at", FieldValue.serverTimestamp()).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
            ref.set(mapOf("at" to FieldValue.serverTimestamp(), "staleNotified" to false)).await()
        }
    }

    /**
     * Tells the backend a protection was switched off; it lets every Bumblebee know that Meadow
     * may not be looking after this garden. Which one, and why, stays on this phone.
     */
    suspend fun reportProtectionLost() {
        val uid = backend.uid ?: return
        backend.user(uid).collection("alerts").add(
            mapOf("kind" to "protection_off", "createdAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    /** A friend's screen time on [dayKey]; null while it isn't shared, or there's none for that day yet. */
    fun minutes(uid: String, dayKey: String): Flow<Int?> =
        sharedPart("Shared time", { backend.user(uid).collection(TIME).document(TODAY).tracedSnapshots(INCLUDE) }, { it.metadata.isFromCache }) {
            if (it.exists() && it.getString("dayKey") == dayKey) it.int("totalMinutes") else null
        }

    /** A friend's top apps on [dayKey]; null while they aren't shared, or there are none for that day yet. */
    fun apps(uid: String, dayKey: String): Flow<List<SharedAppUsage>?> =
        sharedPart("Shared apps", { backend.user(uid).collection(APPS).document(TODAY).tracedSnapshots(INCLUDE) }, { it.metadata.isFromCache }) {
            if (it.exists() && it.getString("dayKey") == dayKey) it.toSharedApps() else null
        }

    /** A friend's progress; null while it isn't shared, or there's none yet. */
    fun progress(uid: String): Flow<Progress?> =
        sharedPart("Shared progress", { backend.user(uid).collection(PROGRESS).document(PROGRESS_DOC).tracedSnapshots(INCLUDE) }, { it.metadata.isFromCache }) {
            if (it.exists()) it.toProgress() else null
        }

    /** A friend's limits; null while they aren't shared. */
    fun limits(uid: String): Flow<List<AppLimit>?> =
        sharedPart("Shared limits", { backend.user(uid).collection("limits").tracedSnapshots(INCLUDE) }, { it.metadata.isFromCache }) { snapshot ->
            snapshot.documents.map { it.toLimit() }.sortedBy { it.appName.lowercase() }
        }

    companion object {
        /**
         * A part of a friend's page. When it can't be read (they stopped sharing it, or you're no
         * longer their Bumblebee) it reads as not shared, and it's tried again now and then, in case
         * it's shared again. Until the server has said it may be read, this phone's cached copy of
         * what used to be shared is never shown. [listen] must include metadata changes: a cached copy
         * the server agrees with otherwise never comes again as confirmed.
         */
        internal fun <S, T> sharedPart(label: String, listen: () -> Flow<S>, fromCache: (S) -> Boolean, read: (S) -> T?): Flow<T?> =
            flow {
                // Per listen: each time the part is watched again, the server has to say so again.
                var unconfirmed = true
                emitAll(
                    listen().filter { !(unconfirmed && fromCache(it)) }.map {
                        unconfirmed = false
                        read(it)
                    },
                )
            }.retryWhen { cause, _ ->
                val refused = refusal(cause) ?: return@retryWhen false
                Log.w(TAG, "$label unreadable", refused)
                emit(null)
                delay(RECHECK_MS)
                true
            }

        /**
         * The Firestore error behind [cause], if that's what ended a listener. The SDK's listeners
         * end with a cancellation wrapping it, possibly more than once.
         */
        internal fun refusal(cause: Throwable): FirebaseFirestoreException? =
            generateSequence(cause) { it.cause }.take(MAX_CAUSES).filterIsInstance<FirebaseFirestoreException>().firstOrNull()

        /** Today's total, read while "Today's screen time" is shared. */
        const val TIME = "usage"

        /** The one document of [TIME] and [APPS]: the latest day, which says which day it is. */
        const val TODAY = "today"

        /** Today's top apps, read while they're shared. */
        const val APPS = "appUsage"

        /** [Progress], in one document, read while it's shared. */
        const val PROGRESS = "progress"
        const val PROGRESS_DOC = "current"
        private const val RECHECK_MS = 30_000L
        private const val MAX_CAUSES = 8
    }
}
