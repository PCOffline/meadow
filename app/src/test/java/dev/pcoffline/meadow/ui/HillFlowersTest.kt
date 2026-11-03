package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.ui.components.HILL_FLOWERS
import dev.pcoffline.meadow.ui.components.SMALL_BUTTERFLY_DP
import dev.pcoffline.meadow.ui.components.hillFlowerWidthDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The little flowers on the hills: as many as before, spread over both hills, a little larger (#87). */
class HillFlowersTest {
    @Test
    fun `there are as many as before`() = assertEquals(6, HILL_FLOWERS.size)

    @Test
    fun `they spread from the back hill to the front one`() {
        val downs = HILL_FLOWERS.map { it.down }
        // They were all in a band 0.14 of the height deep.
        assertTrue("spread ${downs.max() - downs.min()}", downs.max() - downs.min() >= 0.25f)
        // Next to each other, one is on the back hill and the next on the front one.
        HILL_FLOWERS.zipWithNext().forEach { (a, b) -> assertTrue("$a then $b", (a.down < 0.75f) != (b.down < 0.75f)) }
    }

    @Test
    fun `each is larger than before and smaller than a butterfly`() {
        HILL_FLOWERS.forEach {
            val width = hillFlowerWidthDp(it)
            assertTrue("$width dp is larger than the old 6.4 dp", width > 6.4f)
            assertTrue("$width dp is smaller than the small butterfly", width < SMALL_BUTTERFLY_DP)
        }
    }

    @Test
    fun `the ones further back are smaller`() {
        val back = HILL_FLOWERS.filter { it.down < 0.75f }.maxOf { hillFlowerWidthDp(it) }
        val front = HILL_FLOWERS.filter { it.down >= 0.75f }.minOf { hillFlowerWidthDp(it) }
        assertTrue(back < front)
    }
}
