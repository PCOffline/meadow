package dev.pcoffline.meadow.data

import dev.pcoffline.meadow.showcase.DemoGarden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `top apps carry no limits, those come only with the limits, while they're shared`() {
        val shared = Sharing.apps(Privacy(shareLimits = false), today).orEmpty().first().toFirestore()
        assertEquals(setOf("packageName", "appName", "minutes"), shared.keys)
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
    fun `a choice never made is the default, and progress waits to be chosen`() {
        assertEquals(Privacy(), Privacy.read { null })
        assertEquals(false, Privacy.read { null }.shareProgress)
        val some = mapOf("shareApps" to false)
        assertEquals(Privacy(shareApps = false), Privacy.read { some[it] })
    }
}
