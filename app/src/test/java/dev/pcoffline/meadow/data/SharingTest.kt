package dev.pcoffline.meadow.data

import dev.pcoffline.meadow.showcase.DemoGarden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One place decides what Bumblebees see, part by part; the upload and the preview both ask it. */
class SharingTest {
    private val robin = DemoGarden.robin
    private val today = DemoGarden.today
    private val all = DemoGarden.sharesEverything

    private fun page(privacy: Privacy) = Sharing.page(robin.copy(privacy = privacy), { today }, { DemoGarden.progress }, DemoGarden.myLimits)

    @Test
    fun `the page carries only the name card, never the private parts of the profile`() {
        val page = page(all)
        assertEquals(UserProfile(robin.uid, robin.displayName, robin.avatar, robin.bio), page.profile)
        assertEquals(today.totalMinutes, page.minutes)
        assertEquals(today.apps, page.apps)
        assertEquals(DemoGarden.progress, page.progress)
        assertEquals(listOf("Instagram", "YouTube"), page.limits?.map { it.appName })
    }

    @Test
    fun `each part is shared on its own`() {
        assertNull(page(all.copy(shareTime = false)).minutes)
        assertEquals(today.apps.size, page(all.copy(shareTime = false)).apps?.size)
        assertNull(page(all.copy(shareApps = false)).apps)
        assertEquals(today.totalMinutes, page(all.copy(shareApps = false)).minutes)
        assertNull(page(all.copy(shareProgress = false)).progress)
        assertEquals(DemoGarden.progress, page(Privacy(shareProgress = true)).progress)
        assertNull(page(all.copy(shareLimits = false)).limits)
    }

    @Test
    fun `top apps carry no limits, those come only with the limits, while they're shared`() {
        val shared = Sharing.apps(all.copy(shareLimits = false), today).orEmpty().first().toFirestore()
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
    fun `nothing is shared until it's chosen`() {
        assertEquals(Privacy(shareLimits = false, shareTime = false, shareApps = false, shareProgress = false), Privacy())
        assertEquals(Privacy(), Privacy.read { null })
        val some = mapOf("shareApps" to true)
        assertEquals(Privacy(shareApps = true), Privacy.read { some[it] })
        val nothing = page(Privacy())
        assertNull(nothing.minutes)
        assertNull(nothing.apps)
        assertNull(nothing.progress)
        assertNull(nothing.limits)
    }
}
