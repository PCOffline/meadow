package dev.pcoffline.meadow.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** A screen opened after a change made elsewhere acts on the server's copy, not the cached one. */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferServerTest {
    /** A snapshot: what it says, and whether it came from the phone's cache. */
    private data class Snap(val value: String, val cached: Boolean)

    private fun listener(vararg steps: Pair<Long, Snap>) = flow {
        steps.forEach { (after, snap) ->
            delay(after)
            emit(snap)
        }
    }

    @Test
    fun `online, the stale cached copy is replaced by the server's before it shows`() = runTest {
        val got = listener(0L to Snap("waiting", cached = true), 300L to Snap("answered", cached = false))
            .preferServer({ it.cached }, online = { true })
            .toList()
        assertEquals(listOf("answered"), got.map { it.value })
    }

    @Test
    fun `offline, the cached copy shows at once`() = runTest {
        val shown = mutableListOf<Long>()
        val got = listener(0L to Snap("waiting", cached = true))
            .preferServer({ it.cached }, online = { false })
            .onEach { shown += currentTime }
            .toList()
        assertEquals(listOf("waiting"), got.map { it.value })
        assertEquals(listOf(0L), shown)
    }

    @Test
    fun `with a server slow to answer, the cached copy shows after the wait`() = runTest {
        val shown = mutableListOf<Long>()
        val got = listener(0L to Snap("waiting", cached = true))
            .preferServer({ it.cached }, online = { true }, waitMs = 2_000L)
            .onEach { shown += currentTime }
            .toList()
        assertEquals(listOf("waiting"), got.map { it.value })
        assertEquals(listOf(2_000L), shown)
    }

    @Test
    fun `a server that only confirms the cached copy lets it show as soon as it answers`() = runTest {
        // What a listener with metadata changes hears when nothing changed meanwhile.
        val shown = mutableListOf<Long>()
        val got = listener(0L to Snap("waiting", cached = true), 150L to Snap("waiting", cached = false))
            .preferServer({ it.cached }, online = { true }, waitMs = 2_000L)
            .onEach { shown += currentTime }
            .toList()
        assertEquals(listOf("waiting"), got.map { it.value })
        assertEquals(listOf(150L), shown)
    }

    @Test
    fun `the server's copies show as they come`() = runTest {
        val got = listener(0L to Snap("one", cached = false), 10L to Snap("two", cached = false))
            .preferServer({ it.cached }, online = { true })
            .toList()
        assertEquals(listOf("one", "two"), got.map { it.value })
    }
}
