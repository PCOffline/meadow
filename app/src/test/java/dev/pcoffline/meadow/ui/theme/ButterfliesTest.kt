package dev.pcoffline.meadow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Every garden has butterflies of its own. */
class ButterfliesTest {
    @Test
    fun `each theme has two butterflies, unlike each other`() {
        GardenTheme.entries.forEach { theme ->
            val (one, two) = butterfliesFor(theme).also { assertEquals(theme.name, 2, it.size) }
            assertNotEquals(theme.name, one.shape to one.marks, two.shape to two.marks)
            assertNotEquals(theme.name, one.wing, two.wing)
        }
    }

    @Test
    fun `no two themes share a butterfly`() {
        val all = GardenTheme.entries.flatMap(::butterfliesFor)
        assertEquals(all.size, all.distinct().size)
        assertEquals(all.size, all.map { it.wing }.distinct().size)
    }

    @Test
    fun `every wing shape flies somewhere`() {
        assertEquals(WingShape.entries.toSet(), GardenTheme.entries.flatMap(::butterfliesFor).map { it.shape }.toSet())
    }
}
