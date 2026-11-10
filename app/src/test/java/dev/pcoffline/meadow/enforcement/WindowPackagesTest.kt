package dev.pcoffline.meadow.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each window's app is asked for once, not on every window change. */
class WindowPackagesTest {
    private data class Window(val id: Int, val pkg: String?)

    private val packages = WindowPackages()
    private val asked = ArrayList<Int>()

    private var now = 0L

    private fun scan(vararg windows: Window): List<String> =
        packages.of(windows.toList(), { it.id }, now) { w -> asked += w.id; w.pkg }

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
    fun `a window whose app couldn't be told is asked again, after a moment`() {
        assertEquals(emptyList<String>(), scan(Window(1, null)))
        asked.clear()

        now += WindowPackages.FIRST_WAIT_MS
        assertEquals(listOf("app.a"), scan(Window(1, "app.a")))
        assertEquals(listOf(1), asked)
    }

    @Test
    fun `a window that stays untold isn't asked at every look`() {
        // Windows change several times a second while a video plays. Each ask reaches into the
        // window's app, so 40 looks in two seconds ask only a few times.
        repeat(40) {
            scan(Window(1, null))
            now += 50
        }
        assertTrue("asked ${asked.size} times", asked.size in 2..4)
    }

    @Test
    fun `a window untold for long is still asked now and then`() {
        repeat(200) {
            scan(Window(1, null))
            now += 1_000
        }
        asked.clear()

        now += WindowPackages.LONGEST_WAIT_MS
        assertEquals(listOf("app.a"), scan(Window(1, "app.a")))
        assertEquals(listOf(1), asked)
    }
}
