package dev.pcoffline.meadow.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
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
     * Uploads each part [privacy] shares for [dayKey], and takes down that day's copy of each part
     * it doesn't: a choice turned off while offline still ends up off. [today] and [progress] are
     * only read for parts that are shared.
     */
    suspend fun share(privacy: Privacy, dayKey: String, today: () -> SharedUsage, progress: () -> Progress) {
        val uid = backend.uid ?: return
        val user = backend.user(uid)
        val usage = if (privacy.shareTime || privacy.shareApps) today() else null
        val updatedAt = "updatedAt" to FieldValue.serverTimestamp()
        val time = user.collection(TIME).document(dayKey)
        val minutes = usage?.let { Sharing.minutes(privacy, it) }
        if (minutes != null) time.set(mapOf("dayKey" to dayKey, "totalMinutes" to minutes, updatedAt)).await() else time.delete().await()
        val apps = user.collection(APPS).document(dayKey)
        val top = usage?.let { Sharing.apps(privacy, it) }
        if (top != null) apps.set(mapOf("dayKey" to dayKey, "apps" to top.map { it.toFirestore() }, updatedAt)).await() else apps.delete().await()
        val growth = user.collection(PROGRESS).document(PROGRESS_DOC)
        if (privacy.shareProgress) growth.set(progress().toFirestore() + updatedAt).await() else growth.delete().await()
    }

    /** Removes everything already shared of each part [privacy] doesn't share. */
    suspend fun takeDown(privacy: Privacy) {
        val uid = backend.uid ?: return
        val parts = listOfNotNull(TIME.takeIf { !privacy.shareTime }, APPS.takeIf { !privacy.shareApps }, PROGRESS.takeIf { !privacy.shareProgress })
        if (parts.isEmpty()) return
        val batch = backend.db.batch()
        parts.forEach { part -> backend.user(uid).collection(part).get().await().documents.forEach { batch.delete(it.reference) } }
        batch.commit().await()
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

    /** A friend's screen time on [dayKey]; null while it isn't shared, or there's none yet. */
    fun minutes(uid: String, dayKey: String): Flow<Int?> =
        sharedPart("Shared time", { backend.user(uid).collection(TIME).document(dayKey).tracedSnapshots() }, { it.metadata.isFromCache }) {
            if (it.exists()) it.int("totalMinutes") else null
        }

    /** A friend's top apps on [dayKey]; null while they aren't shared, or there are none yet. */
    fun apps(uid: String, dayKey: String): Flow<List<SharedAppUsage>?> =
        sharedPart("Shared apps", { backend.user(uid).collection(APPS).document(dayKey).tracedSnapshots() }, { it.metadata.isFromCache }) {
            if (it.exists()) it.toSharedApps() else null
        }

    /** A friend's progress; null while it isn't shared, or there's none yet. */
    fun progress(uid: String): Flow<Progress?> =
        sharedPart("Shared progress", { backend.user(uid).collection(PROGRESS).document(PROGRESS_DOC).tracedSnapshots() }, { it.metadata.isFromCache }) {
            if (it.exists()) it.toProgress() else null
        }

    /** A friend's limits; null while they aren't shared. */
    fun limits(uid: String): Flow<List<AppLimit>?> =
        sharedPart("Shared limits", { backend.user(uid).collection("limits").tracedSnapshots() }, { it.metadata.isFromCache }) { snapshot ->
            snapshot.documents.map { it.toLimit() }.sortedBy { it.appName.lowercase() }
        }

    /**
     * A part of a friend's page. When it can't be read (they stopped sharing it, or you're no
     * longer their Bumblebee) it reads as not shared, and it's tried again now and then, in case
     * it's shared again. Until the server has said it may be read, this phone's cached copy of
     * what used to be shared is never shown.
     */
    private fun <S, T> sharedPart(label: String, listen: () -> Flow<S>, fromCache: (S) -> Boolean, read: (S) -> T?): Flow<T?> {
        var unconfirmed = true
        return flow {
            emitAll(
                listen().filter { !(unconfirmed && fromCache(it)) }.map {
                    unconfirmed = false
                    read(it)
                },
            )
        }.retryWhen { cause, _ ->
            if (cause !is FirebaseFirestoreException) return@retryWhen false
            Log.w(TAG, "$label unreadable", cause)
            unconfirmed = true
            emit(null)
            delay(RECHECK_MS)
            true
        }
    }

    companion object {
        /** Today's total, read while "Today's screen time" is shared. */
        const val TIME = "usage"

        /** Today's top apps, read while they're shared. */
        const val APPS = "appUsage"

        /** [Progress], in one document, read while it's shared. */
        const val PROGRESS = "progress"
        const val PROGRESS_DOC = "current"
        private const val RECHECK_MS = 30_000L
    }
}
