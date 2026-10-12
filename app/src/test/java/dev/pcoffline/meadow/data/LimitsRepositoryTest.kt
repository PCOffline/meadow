package dev.pcoffline.meadow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Must agree with isStricter() in firestore.rules: anything looser needs a Bumblebee's yes. */
class LimitsRepositoryTest {
    private fun limit(daily: Int, weekly: Int) = AppLimit("app", "App", daily, weekly)

    @Test
    fun `less time is never looser`() {
        assertFalse(LimitsRepository.isLooser(limit(30, 0), limit(20, 0)))
        assertFalse(LimitsRepository.isLooser(limit(30, 300), limit(30, 200)))
        assertFalse(LimitsRepository.isLooser(limit(30, 0), limit(30, 0)))
    }

    @Test
    fun `more time is looser`() {
        assertTrue(LimitsRepository.isLooser(limit(30, 0), limit(45, 0)))
        assertTrue(LimitsRepository.isLooser(limit(0, 300), limit(0, 400)))
    }

    @Test
    fun `dropping a limit is looser, adding one is not`() {
        assertTrue(LimitsRepository.isLooser(limit(30, 300), limit(30, 0)))
        assertTrue(LimitsRepository.isLooser(limit(30, 300), limit(0, 300)))
        assertFalse(LimitsRepository.isLooser(limit(30, 0), limit(30, 300)))
        assertFalse(LimitsRepository.isLooser(limit(0, 300), limit(60, 300)))
    }
}
