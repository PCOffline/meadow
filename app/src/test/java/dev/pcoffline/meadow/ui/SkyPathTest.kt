package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.ui.components.arcAt
import dev.pcoffline.meadow.ui.components.skyProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sun crosses the sky with the day, and the moon with the night. */
class SkyPathTest {
    @Test
    fun `the sun rises at five, is halfway at one and sets at nine`() {
        assertEquals(0f, skyProgress(5, 0), 1e-4f)
        assertEquals(0.5f, skyProgress(13, 0), 1e-4f)
        assertEquals(1f, skyProgress(20, 60), 1e-4f)
        assertTrue(skyProgress(9, 30) > skyProgress(9, 0))
    }

    @Test
    fun `the moon rises at nine, is halfway at one and sets at five`() {
        assertEquals(0f, skyProgress(21, 0), 1e-4f)
        assertEquals(0.5f, skyProgress(1, 0), 1e-4f)
        assertEquals(1f, skyProgress(4, 60), 1e-4f)
        assertTrue(skyProgress(23, 0) < skyProgress(0, 30))
    }

    @Test
    fun `it climbs from the left, is highest in the middle, and sinks to the right`() {
        val morning = arcAt(skyProgress(7, 0))
        val noon = arcAt(skyProgress(13, 0))
        val evening = arcAt(skyProgress(19, 0))
        assertTrue(morning.x < noon.x && noon.x < evening.x)
        assertEquals(1f, noon.y, 1e-4f)
        assertTrue(morning.y < noon.y && evening.y < noon.y)
        assertEquals(0.1f, arcAt(0f).x, 1e-4f)
        assertEquals(0.9f, arcAt(1f).x, 1e-4f)
        assertEquals(0f, arcAt(1f).y, 1e-4f)
    }
}
