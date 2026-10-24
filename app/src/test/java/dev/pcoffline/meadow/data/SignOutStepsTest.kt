package dev.pcoffline.meadow.data

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Signing out stops this phone's pushes even when the server can't be reached. */
class SignOutStepsTest {
    @Test
    fun `a server write that never finishes doesn't stop the steps on the phone`() = runTest {
        val done = mutableListOf<String>()
        runEachBriefly(
            4_000L,
            { done += "id" },
            { awaitCancellation() }, // Offline: the write is never acknowledged.
            { done += "unregister" },
            { done += "delete installation" },
        )
        assertEquals(listOf("id", "unregister", "delete installation"), done)
    }

    @Test
    fun `a failing step doesn't stop the ones after it`() = runTest {
        val done = mutableListOf<String>()
        runEachBriefly(
            4_000L,
            { error("no network") },
            { done += "unregister" },
            { done += "delete installation" },
        )
        assertEquals(listOf("unregister", "delete installation"), done)
    }
}
