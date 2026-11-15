package dev.pcoffline.meadow.data

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import kotlinx.coroutines.tasks.await

/**
 * Runs a Firebase Auth [call], and runs it once more when Android sent it on a connection that the
 * server had already closed.
 *
 * Android keeps a connection open for the next request. A server closes one that stays quiet: the
 * Firebase emulators after a few seconds, a phone network after a longer time. A request on such a
 * connection fails with "unexpected end of stream" before the server reads it, so a second try is
 * safe. A person would tap again; this does it for them, once.
 */
internal suspend fun <T> withFreshConnection(call: () -> Task<T>): T =
    try {
        call().await()
    } catch (e: FirebaseException) {
        if (!sentOnClosedConnection(e)) throw e
        call().await()
    }

/** Whether [e], or what caused it, says the request went out on a connection that was closed. */
internal fun sentOnClosedConnection(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(MAX_CAUSES).any { it.message?.contains(CLOSED, ignoreCase = true) == true }

private const val CLOSED = "unexpected end of stream"
private const val MAX_CAUSES = 8
