package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.AppLimit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityService

/**
 * A browser's address bar is read from the screen by its id. With no screen to read, or no
 * address bar on it, no site is found. (Robolectric can't find a view by its id, so reading the
 * address itself is tested through the service's own reader, in SiteMeasureTest.)
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AddressBarReadTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<GuardianAccessibilityService>
    private lateinit var service: GuardianAccessibilityService

    private val shadow: ShadowAccessibilityService
        get() = Shadow.extract(service)

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        AppGraph.sites.left()
        AppGraph.store.setLimits(listOf(AppLimit(SITE, "example.com", 30, 0)))
        controller = Robolectric.buildService(GuardianAccessibilityService::class.java).create()
        service = controller.get()
        service.listWindows = { emptyList() }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        controller.destroy()
        AppGraph.sites.left()
        AppGraph.store.clearAll()
    }

    private fun waitUntil(done: () -> Boolean) {
        val main = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 10_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            main.idle()
            Thread.sleep(10)
        }
    }

    /** Waits for the reads the service started to end. */
    private fun settle() = waitUntil { service.scope.coroutineContext[kotlinx.coroutines.Job]!!.children.none() }

    @Test
    fun `an address bar that isn't there, or a screen that can't be read, finds no site`() {
        shadow.setRootInActiveWindow(AccessibilityNodeInfo())
        service.onForeground(CHROME)
        settle()
        assertFalse(service.measureSite(SITE))

        service.onForeground("com.example.notes")
        shadow.setRootInActiveWindow(null)
        service.onForeground(CHROME)
        settle()
        assertFalse(service.measureSite(SITE))
    }

    @Test
    fun `an app that isn't a browser has no address bar to read`() {
        service.onForeground("com.example.notes")
        settle()
        assertEquals(null, service.browserInFront())
    }

    private companion object {
        const val CHROME = "com.android.chrome"
        const val SITE = "site:example.com"
    }
}
