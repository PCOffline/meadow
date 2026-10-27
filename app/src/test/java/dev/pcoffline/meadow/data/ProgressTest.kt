package dev.pcoffline.meadow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Progress: comparisons that are fair, or not made at all. */
class ProgressTest {
    private val min = 60_000L
    private val monday = LocalDate.of(2026, 10, 26)

    @Test
    fun `the week's change is a percentage of last week, and only with something to compare`() {
        assertEquals(-25, Progress.change(300 * min, 400 * min))
        assertEquals(50, Progress.change(600 * min, 400 * min))
        assertNull(Progress.change(300 * min, null))
        assertNull(Progress.change(300 * min, 0L))
    }

    @Test
    fun `calmer weeks count back while each week had less than the one before`() {
        // Last week first: 300 < 400 < 500 < 650, then 650 isn't less than 600.
        assertEquals(3, Progress.calmerWeeks(listOf(300L, 400L, 500L, 650L, 600L)))
        assertEquals(0, Progress.calmerWeeks(listOf(500L, 400L)))
        // A week the phone has no full record of ends the run: it can't be compared.
        assertEquals(1, Progress.calmerWeeks(listOf(300L, 400L, null, 600L)))
        assertEquals(0, Progress.calmerWeeks(listOf(null, 400L)))
        assertEquals(0, Progress.calmerWeeks(emptyList()))
    }

    @Test
    fun `a day keeps its limits when every daily limit held, counting extra time given`() {
        val limits = listOf(AppLimit("insta", "Instagram", 30, 0), AppLimit("tok", "TikTok", 0, 120))
        val days = listOf(
            monday to mapOf("insta" to 25 * min, "tok" to 300 * min),
            monday.plusDays(1) to mapOf("insta" to 40 * min),
            monday.plusDays(2) to mapOf("insta" to 40 * min),
            monday.plusDays(3) to null,
        )
        // 15 extra minutes on Wednesday only.
        val (kept, judged) = Progress.keptDays(days, limits) { _, day -> if (day == monday.plusDays(2)) 15 else 0 }
        // Monday kept (TikTok has no daily limit), Tuesday didn't, Wednesday did with the extra time;
        // Thursday has no record, so it isn't judged.
        assertEquals(2, kept)
        assertEquals(3, judged)
    }

    @Test
    fun `without daily limits there's nothing to keep`() {
        val (kept, judged) = Progress.keptDays(listOf(monday to emptyMap()), listOf(AppLimit("tok", "TikTok", 0, 120))) { _, _ -> 0 }
        assertEquals(0 to 0, kept to judged)
    }

    @Test
    fun `progress is empty until there's something to show`() {
        assertTrue(Progress(null, 0, 0, 0).isEmpty)
        assertFalse(Progress(-10, 0, 0, 0).isEmpty)
        assertFalse(Progress(null, 0, 0, 3).isEmpty)
    }
}
