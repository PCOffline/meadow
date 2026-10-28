package dev.pcoffline.meadow.ui

import android.app.Application
import android.content.res.Resources
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.InviteLinks
import dev.pcoffline.meadow.data.LimitRequest
import dev.pcoffline.meadow.data.RequestStatus
import dev.pcoffline.meadow.data.RequestType
import dev.pcoffline.meadow.data.ProfileRepository
import dev.pcoffline.meadow.data.TimeCap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Words and numbers people read: durations, limits, request summaries, invite links, exports. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FormatTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun `durations read naturally`() {
        assertEquals("0m", res.minutesText(0))
        assertEquals("0m", res.minutesText(-5))
        assertEquals("45m", res.minutesText(45))
        assertEquals("2h", res.minutesText(120))
        assertEquals("1h 30m", res.minutesText(90))
        assertEquals("<1m", res.durationText(20_000))
        assertEquals("0m", res.durationText(0))
        assertEquals("1m", res.durationText(119_000))
        // Past a day, minutes are noise: days and hours.
        assertEquals("23h 59m", res.durationText(1439 * 60_000L))
        assertEquals("1d", res.durationText(24 * 3_600_000L))
        assertEquals("49d 4h", res.durationText((1180 * 60 + 55) * 60_000L))
    }

    @Test
    fun `limits describe days and weeks`() {
        assertEquals(res.getString(dev.pcoffline.meadow.R.string.limit_none), res.limitText(0, 0))
        assertEquals(true, res.limitText(30, 0).contains("30m"))
        assertEquals(true, res.limitText(0, 180).contains("3h"))
        res.limitText(30, 180).let { assertEquals(true, "30m" in it && "3h" in it) }
    }

    @Test
    fun `limits describe days with their own limit and caps within hours`() {
        val limit = AppLimit(
            "pkg", "App", 30, 0,
            dayMinutes = mapOf(7 to 120, 6 to 120, 3 to 0),
            caps = listOf(TimeCap(listOf(1, 2, 3, 4, 5), 600, 960, 20)),
        )
        val text = res.limitText(limit)
        assertEquals(text, true, text.startsWith("30m a day, no limit on Wed, 2h at weekends, up to 20m "))
        assertEquals(text, true, text.endsWith(" on weekdays"))
        assertEquals("every day", res.daysText((1..7).toList()))
        assertEquals("on Mon, Thu", res.daysText(listOf(4, 1, 1)))
        assertEquals("at weekends", res.daysText(listOf(7, 6)))
        // Only a cap still reads as a limit.
        assertEquals(true, res.limitText(AppLimit("pkg", "App", 0, 0, caps = limit.caps)).startsWith("up to 20m"))
    }

    private fun request(type: RequestType, minutes: Int = 0, app: String? = "Instagram") = LimitRequest(
        "r", "me", "Robin", type, "pkg", app, minutes, 30, 0, "", RequestStatus.PENDING, emptyList(), null, null, "2026-10-12", 0L,
    )

    @Test
    fun `requests are summed up in one phrase`() {
        assertEquals(true, res.requestSummary(request(RequestType.EXTRA_TIME, 15)).let { "15" in it && "Instagram" in it })
        assertEquals(true, res.requestSummary(request(RequestType.EXTRA_TIME, 1)).contains("1"))
        assertEquals(true, res.requestSummary(request(RequestType.CHANGE_LIMIT)).let { "Instagram" in it && "30m" in it })
        assertEquals(true, res.requestSummary(request(RequestType.REMOVE_LIMIT)).contains("Instagram"))
        assertEquals(true, res.requestSummary(request(RequestType.REMOVE_LIMIT, app = null)).isNotBlank())
        assertEquals(true, res.requestSummary(request(RequestType.DISABLE_STRICT_MODE)).isNotBlank())
    }

    @Test
    fun `invite links are recognised in both forms and nothing else`() {
        val token = "AbCdEfGh12345678"
        assertEquals(token, InviteLinks.tokenFrom(Uri.parse("https://meadow-demo.web.app/i/$token")))
        assertEquals(token, InviteLinks.tokenFrom(Uri.parse("meadow://invite/$token")))
        assertNull(InviteLinks.tokenFrom(null))
        assertNull(InviteLinks.tokenFrom(Uri.parse("https://meadow-demo.web.app/x/$token")))
        assertNull(InviteLinks.tokenFrom(Uri.parse("https://meadow-demo.web.app/i/$token/extra")))
        assertNull(InviteLinks.tokenFrom(Uri.parse("http://meadow-demo.web.app/i/$token")))
        assertNull(InviteLinks.tokenFrom(Uri.parse("meadow://other/$token")))
        assertNull(InviteLinks.tokenFrom(Uri.parse("https://meadow-demo.web.app/i/short")))
        assertNull(InviteLinks.tokenFrom(Uri.parse("https://meadow-demo.web.app/i/AbCdEfGh1234567!")))
    }

    @Test
    fun `counts agree with their nouns`() {
        assertEquals("No record for 1 day", res.getQuantityString(R.plurals.stats_missing_days, 1, 1))
        assertEquals("No record for 4 days", res.getQuantityString(R.plurals.stats_missing_days, 4, 4))
        assertEquals(true, res.getQuantityString(R.plurals.emergency_dialog_used_up, 1, 1).contains("your 1 emergency unlock for"))
        assertEquals(true, res.getQuantityString(R.plurals.emergency_dialog_used_up, 3, 3).contains("all 3 emergency unlocks"))
        assertEquals("Answer 1 request to see how you answer.", res.getQuantityString(R.plurals.bee_stats_leniency_unknown, 1, 1))
        assertEquals("2 of 3 left", res.getQuantityString(R.plurals.settings_emergency_left, 2, 2, 3))
        assertEquals("1 of 4 ready", res.getQuantityString(R.plurals.setup_progress, 1, 1, 4))
    }

    @Test
    fun `exports turn backend values into plain JSON`() {
        val json = ProfileRepository.toJson(mapOf("name" to "Robin", "n" to 3L, "ok" to true, "none" to null, "list" to listOf(1, "a")))
        assertEquals("""{"name":"Robin","n":3,"ok":true,"none":null,"list":[1,"a"]}""", json.toString())
    }
}
