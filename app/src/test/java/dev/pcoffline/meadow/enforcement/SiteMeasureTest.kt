package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * A website's time counts only while it's the one in front. A watcher that measured it a moment
 * after it was left (covered by the rest screen, or gone Home) opened the visit again, and it
 * went on counting with nothing on screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SiteMeasureTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<GuardianAccessibilityService>
    private lateinit var service: GuardianAccessibilityService

    @Before
    fun setUp() {
        AppGraph.init(app)
        controller = Robolectric.buildService(GuardianAccessibilityService::class.java).create()
        service = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        AppGraph.sites.left()
    }

    @After
    fun tearDown() {
        AppGraph.sites.left()
        controller.destroy()
    }

    @Test
    fun `the site in front is measured`() {
        service.setSite(SITE)

        assertTrue(service.measureSite(SITE))
        assertEquals(SITE, AppGraph.sites.current())
    }

    @Test
    fun `a site just left isn't measured again, so its visit stays closed`() {
        service.setSite(SITE)
        service.measureSite(SITE)
        service.setSite(null)

        assertFalse(service.measureSite(SITE))
        assertNull(AppGraph.sites.current())
    }

    @Test
    fun `another site in front doesn't count for this one`() {
        service.setSite("site:example.com")

        assertFalse(service.measureSite(SITE))
        assertNull(AppGraph.sites.current())
    }

    private companion object {
        const val SITE = "site:example.org"
    }
}
