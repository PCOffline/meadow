package dev.pcoffline.meadow.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A failed listener must listen again, with pauses that grow but stay short enough to recover. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenerRetryTest {
    @Test
    fun `pauses double from a second up to a minute`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), (0L..7L).map(::backoffMs))
        assertEquals(MAX_BACKOFF_MS, backoffMs(1_000))
        assertEquals(MAX_BACKOFF_MS, backoffMs(Long.MAX_VALUE))
    }

    @Test
    fun `a failed listener listens again after a pause and delivers`() = runTest {
        var listens = 0
        val failures = mutableListOf<Pair<String?, Long>>()
        val listener = flow {
            listens++
            if (listens <= 2) throw IllegalStateException("dropped $listens")
            emit("snapshot")
        }
        val got = listener.retryWithBackoff { e, waitMs -> failures += e.message to waitMs }.toList()
        assertEquals(listOf("snapshot"), got)
        assertEquals(3, listens)
        assertEquals(listOf("dropped 1" to 1_000L, "dropped 2" to 2_000L), failures)
        assertEquals(3_000L, currentTime)
    }

    @Test
    fun `a listener the SDK cancels with its error is retried too`() = runTest {
        // Firestore's snapshots() cancels its callbackFlow with the error as the cause.
        var listens = 0
        val denied = IllegalStateException("PERMISSION_DENIED")
        val failures = mutableListOf<Throwable>()
        val listener = callbackFlow {
            listens++
            if (listens == 1) cancel("Error getting snapshot", denied) else trySend("snapshot")
            awaitClose()
        }
        val got = listener.retryWithBackoff { e, _ -> failures += e }.take(1).toList()
        assertEquals(listOf("snapshot"), got)
        assertEquals(2, listens)
        assertTrue(generateSequence(failures.single()) { it.cause }.any { it === denied })
    }

    @Test
    fun `a listener that can't recover asks once a minute, not in a tight loop`() = runTest {
        var listens = 0
        val job = launch { flow<Unit> { listens++; throw IllegalStateException("signed out") }.retryWithBackoff().collect() }
        runCurrent()
        assertEquals(1, listens)
        // 1 + 2 + 4 + 8 + 16 + 32 s, then a minute at a time.
        advanceTimeBy(63_000L + 1)
        assertEquals(7, listens)
        advanceTimeBy(10 * 60_000L)
        assertEquals(17, listens)
        job.cancel()
        advanceTimeBy(10 * 60_000L)
        assertEquals(17, listens)
    }

    @Test
    fun `a failure in the collector is not swallowed`() = runTest {
        var listens = 0
        try {
            flow { listens++; emit(1) }.retryWithBackoff().collect { throw IllegalArgumentException("bug") }
            fail("The collector's failure was swallowed")
        } catch (e: IllegalArgumentException) {
            assertEquals("bug", e.message)
        }
        assertEquals(1, listens)
    }
}
