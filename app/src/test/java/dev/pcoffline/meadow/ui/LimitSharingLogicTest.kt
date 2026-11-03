package dev.pcoffline.meadow.ui

import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.LimitCardMode
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeCap
import dev.pcoffline.meadow.data.TimeWindow
import dev.pcoffline.meadow.data.LimitsRepository
import dev.pcoffline.meadow.data.limitOrNull
import dev.pcoffline.meadow.ui.limits.ApplyStep
import dev.pcoffline.meadow.ui.limits.LimitTemplate
import dev.pcoffline.meadow.ui.limits.onThisPhone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Limit cards, templates and Apply's first step (#93). */
class LimitSharingLogicTest {
    private val full = AppLimit(
        "com.instagram.android", "Instagram", 45, 300,
        schedule = Schedule.everyDay(TimeWindow(9 * 60, 21 * 60)),
        dayMinutes = mapOf(6 to 90),
        caps = listOf(TimeCap(listOf(1, 2, 3, 4, 5), 17 * 60, 22 * 60, 30)),
    )

    @Test
    fun `a template carries the whole limit through navigation`() {
        assertEquals(full, LimitTemplate.decode(LimitTemplate.encode(full)))
        assertNull(LimitTemplate.decode(null))
        assertNull(LimitTemplate.decode("not json"))
    }

    @Test
    fun `a copy moved to another app keeps its limit and takes the app's name`() {
        val moved = LimitTemplate.forApp(full, "com.zhiliaoapp.musically", "TikTok")
        assertEquals("com.zhiliaoapp.musically", moved.packageName)
        assertEquals("TikTok", moved.appName)
        assertEquals(full.copy(packageName = moved.packageName, appName = "TikTok"), moved)
    }

    @Test
    fun `a limit card reads back the limit it was sent with`() {
        assertEquals(full, limitOrNull(LimitsRepository.limitFields(full)))
        assertNull(limitOrNull(null))
        assertNull(limitOrNull(mapOf("appName" to "No package")))
        assertEquals(AppLimit("a.b", "a.b", 0, 0), limitOrNull(mapOf("packageName" to "a.b")))
    }

    @Test
    fun `card kinds read from their wire names`() {
        assertEquals(LimitCardMode.SHARED, LimitCardMode.fromWire("shared"))
        assertEquals(LimitCardMode.SUGGESTED, LimitCardMode.fromWire("suggested"))
        assertNull(LimitCardMode.fromWire("other"))
    }

    @Test
    fun `apply confirms a change, plants a new limit at once, and needs the app`() {
        val current = full.copy(dailyMinutes = 30)
        assertEquals(ApplyStep.Confirm(current), ApplyStep.of(full, current, onPhone = true))
        assertEquals(ApplyStep.Apply, ApplyStep.of(full, null, onPhone = true))
        assertEquals(ApplyStep.NotOnPhone, ApplyStep.of(full, current, onPhone = false))
    }

    @Test
    fun `a website is always on the phone, an app only when installed`() {
        assertTrue(onThisPhone("site:youtube.com", emptySet()))
        assertTrue(onThisPhone("com.instagram.android", setOf("com.instagram.android")))
        assertFalse(onThisPhone("com.instagram.android", setOf("com.other")))
    }
}
