package dev.pcoffline.meadow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeeStatsTest {
    private var n = 0

    private fun request(from: String, decision: String?, minutes: Int = 30, granted: Int = minutes, status: RequestStatus = RequestStatus.APPROVED) =
        LimitRequest(
            id = "r${n++}", requesterId = from, requesterName = from.replaceFirstChar { it.uppercase() }, type = RequestType.EXTRA_TIME,
            packageName = "app", appName = "App", minutes = minutes, dailyMinutes = 0, weeklyMinutes = 0, message = "",
            status = if (decision == "no") RequestStatus.DENIED else status, approverIds = emptyList(), decidedByName = null,
            decisionNote = null, dayKey = "2026-10-12", createdAtMillis = 0L, myDecision = decision,
            grantedMinutes = if (decision == "yes") granted else 0,
        )

    @Test
    fun `answers are counted overall and per person`() {
        val stats = BeeStats.from(
            listOf(
                request("ana", "yes"),
                request("ana", "no"),
                request("leo", "yes", minutes = 30, granted = 15),
                request("leo", null, status = RequestStatus.PENDING),
                request("leo", null, status = RequestStatus.EXPIRED),
            ),
        )
        assertEquals(5, stats.asked)
        assertEquals(2, stats.yes)
        assertEquals(1, stats.no)
        assertEquals(1, stats.waiting)
        assertEquals(1, stats.missed)
        assertEquals(45, stats.minutesGranted)
        assertEquals(2f / 3, stats.approvalRate!!, 0.001f)
        // A full yes, a no, and half of what was asked.
        assertEquals(50, stats.leniency)
        assertEquals(BeeStats.Leaning.BALANCED, stats.leaning)
        val leo = stats.people.first { it.uid == "leo" }
        assertEquals(3, leo.asked)
        assertEquals(1f, leo.approvalRate!!, 0.001f)
    }

    @Test
    fun `leniency waits for a few answers`() {
        val stats = BeeStats.from(listOf(request("ana", "yes"), request("ana", "yes")))
        assertNull(stats.leniency)
        assertNull(stats.leaning)
        assertNull(BeeStats.from(emptyList()).approvalRate)
    }
}
