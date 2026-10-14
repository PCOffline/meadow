package dev.pcoffline.meadow.enforcement

import org.junit.Assert.assertEquals
import org.junit.Test

/** Each window's app is asked for once, not on every window change. */
class WindowPackagesTest {
    private data class Window(val id: Int, val pkg: String?)

    private val packages = WindowPackages()
    private val asked = ArrayList<Int>()

    private fun scan(vararg windows: Window): List<String> =
        packages.of(windows.toList(), { it.id }) { w -> asked += w.id; w.pkg }

    @Test
    fun `windows already seen are not asked again`() {
        assertEquals(listOf("app.a", "app.b"), scan(Window(1, "app.a"), Window(2, "app.b")))
        assertEquals(listOf(1, 2), asked)

        asked.clear()
        assertEquals(listOf("app.a", "app.b", "app.c"), scan(Window(1, "app.a"), Window(2, "app.b"), Window(3, "app.c")))
        assertEquals(listOf(3), asked)
    }

    @Test
    fun `closed windows are forgotten`() {
        scan(Window(1, "app.a"), Window(2, "app.b"))
        scan(Window(2, "app.b"))
        asked.clear()

        assertEquals(listOf("app.a", "app.b"), scan(Window(1, "app.a"), Window(2, "app.b")))
        assertEquals(listOf(1), asked)
    }

    @Test
    fun `a window whose app couldn't be told is asked again`() {
        assertEquals(emptyList<String>(), scan(Window(1, null)))
        asked.clear()

        assertEquals(listOf("app.a"), scan(Window(1, "app.a")))
        assertEquals(listOf(1), asked)
    }
}
