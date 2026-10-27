package dev.pcoffline.meadow.data

import android.os.SystemClock
import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import dev.pcoffline.meadow.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * A step-by-step log of everything the app asks of Firebase and what came back, for debugging.
 * Only debug builds made with `-Pmeadow.trace=true` have it; read it with
 * `adb logcat -s MeadowTrace Firestore`. It includes people's data, so release builds never trace.
 */
object Trace {
    const val TAG = "MeadowTrace"
    const val enabled = BuildConfig.TRACE

    inline fun log(message: () -> String) {
        if (enabled) Log.d(TAG, message())
    }

    fun failed(message: String, error: Throwable) {
        if (enabled) Log.w(TAG, message, error)
    }

    /** Runs one request, logging what was asked, how long it took and what came back. */
    suspend fun <T> request(label: String, describe: (T) -> String = { it.toString() }, block: suspend () -> T): T {
        if (!enabled) return block()
        Log.d(TAG, "→ $label")
        val started = SystemClock.elapsedRealtime()
        try {
            return block().also { Log.d(TAG, "← $label in ${SystemClock.elapsedRealtime() - started} ms: ${describe(it)}") }
        } catch (e: CancellationException) {
            Log.d(TAG, "✗ $label cancelled after ${SystemClock.elapsedRealtime() - started} ms")
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "✗ $label failed after ${SystemClock.elapsedRealtime() - started} ms", e)
            throw e
        }
    }
}

/** The document's live snapshots, with each one (and the listener's start, end and failure) traced. */
fun DocumentReference.tracedSnapshots(metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE): Flow<DocumentSnapshot> =
    if (!Trace.enabled) snapshots(metadataChanges) else snapshots(metadataChanges).traced("doc $path") { doc ->
        "exists=${doc.exists()} fromCache=${doc.metadata.isFromCache} pendingWrites=${doc.metadata.hasPendingWrites()} data=${doc.data}"
    }

/** The query's live results, traced like [tracedSnapshots]. */
fun Query.tracedSnapshots(metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE): Flow<QuerySnapshot> {
    if (!Trace.enabled) return snapshots(metadataChanges)
    // Filtered queries don't expose their path; the changes listed show where they read from.
    val label = (this as? CollectionReference)?.let { "collection $path" } ?: "query"
    return snapshots(metadataChanges).traced(label) { snapshot ->
        "${snapshot.size()} docs fromCache=${snapshot.metadata.isFromCache} pendingWrites=${snapshot.metadata.hasPendingWrites()} " +
            "changes=${snapshot.documentChanges.joinToString { "${it.type} ${it.document.reference.path}" }}"
    }
}

private fun <T> Flow<T>.traced(label: String, describe: (T) -> String): Flow<T> = this
    .onStart { Log.d(Trace.TAG, "⇢ listen $label") }
    .onEach { Log.d(Trace.TAG, "⇠ $label: ${describe(it)}") }
    .onCompletion { cause ->
        when (cause) {
            null, is CancellationException -> Log.d(Trace.TAG, "⇥ stop listening $label")
            else -> Log.w(Trace.TAG, "✗ listener $label failed", cause)
        }
    }
