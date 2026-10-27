package dev.pcoffline.meadow.data

import dev.pcoffline.meadow.showcase.DemoGarden
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One place decides what Bumblebees see; the upload and the preview both ask it. */
class SharingTest {
    private val robin = DemoGarden.robin

    @Test
    fun `today's time is shared only when chosen, and not even read otherwise`() {
        assertEquals(DemoGarden.today, Sharing.usage(Privacy(shareUsage = true)) { DemoGarden.today })
        assertNull(Sharing.usage(Privacy(shareUsage = false)) { error("read though it isn't shared") })
    }

    @Test
    fun `the page carries only the name card, never the private parts of the profile`() {
        val page = Sharing.page(robin, { DemoGarden.today }, DemoGarden.myLimits)
        assertEquals(UserProfile(robin.uid, robin.displayName, robin.avatar, robin.bio), page.profile)
        assertEquals(DemoGarden.today, page.usage)
        assertEquals(listOf("Instagram", "YouTube"), page.limits.map { it.appName })
    }

    @Test
    fun `turning sharing off takes today's time off the page`() {
        val page = Sharing.page(robin.copy(privacy = Privacy(shareUsage = false)), { DemoGarden.today }, DemoGarden.myLimits)
        assertNull(page.usage)
        assertEquals(2, page.limits.size)
    }
}
