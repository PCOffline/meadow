package dev.pcoffline.meadow.data

import dev.pcoffline.meadow.showcase.DemoGarden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One place decides what Bumblebees see, part by part; the upload and the preview both ask it. */
class SharingTest {
    private val robin = DemoGarden.robin
    private val today = DemoGarden.today

    private fun page(privacy: Privacy) = Sharing.page(robin.copy(privacy = privacy), { today }, { DemoGarden.progress }, DemoGarden.myLimits)

    @Test
    fun `the page carries only the name card, never the private parts of the profile`() {
        val page = page(Privacy(shareProgress = true))
        assertEquals(UserProfile(robin.uid, robin.displayName, robin.avatar, robin.bio), page.profile)
        assertEquals(today.totalMinutes, page.minutes)
        assertEquals(today.apps, page.apps)
        assertEquals(DemoGarden.progress, page.progress)
        assertEquals(listOf("Instagram", "YouTube"), page.limits?.map { it.appName })
    }

    @Test
    fun `each part is shared on its own`() {
        assertNull(page(Privacy(shareTime = false)).minutes)
        assertEquals(today.apps.size, page(Privacy(shareTime = false)).apps?.size)
        assertNull(page(Privacy(shareApps = false)).apps)
        assertEquals(today.totalMinutes, page(Privacy(shareApps = false)).minutes)
        assertNull(page(Privacy()).progress)
        assertEquals(DemoGarden.progress, page(Privacy(shareProgress = true)).progress)
        assertNull(page(Privacy(shareLimits = false)).limits)
    }

    @Test
    fun `with limits private, top apps don't give them away`() {
        val apps = page(Privacy(shareLimits = false)).apps.orEmpty()
        assertTrue(today.apps.any { it.limitMinutes > 0 })
        assertTrue(apps.isNotEmpty() && apps.all { it.limitMinutes == 0 })
    }

    @Test
    fun `what isn't shared isn't even read`() {
        val page = Sharing.page(
            robin.copy(privacy = Privacy(shareTime = false, shareApps = false, shareProgress = false)),
            { error("today read though it isn't shared") },
            { error("progress read though it isn't shared") },
            DemoGarden.myLimits,
        )
        assertNull(page.minutes)
        assertNull(page.apps)
        assertNull(page.progress)
    }

    @Test
    fun `choices from before the split still hold for today's time and apps, and progress waits to be chosen`() {
        val old = mapOf("shareUsage" to false)
        assertEquals(Privacy(shareLimits = true, shareTime = false, shareApps = false, shareProgress = false), Privacy.read { old[it] })
        assertEquals(false, Privacy.read { mapOf("shareUsage" to true)[it] }.shareProgress)
        assertEquals(Privacy(), Privacy.read { null })
        // A newer choice wins over the old one.
        val mixed = mapOf("shareUsage" to false, "shareApps" to true)
        assertEquals(true, Privacy.read { mixed[it] }.shareApps)
    }
}
