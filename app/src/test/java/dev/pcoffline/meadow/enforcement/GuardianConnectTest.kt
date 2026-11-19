package dev.pcoffline.meadow.enforcement

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** Turning the guardian on and off: it starts its companion service, and says whether it runs. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class GuardianConnectTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<GuardianAccessibilityService>
    private lateinit var service: GuardianAccessibilityService

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        controller = Robolectric.buildService(GuardianAccessibilityService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        controller.destroy()
        AppGraph.store.clearAll()
    }

    /** Android calls this once the person turns the service on; it's protected, so the test reaches it like Android. */
    private fun connect() {
        GuardianAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
    }

    @Test
    fun `once on, it runs with its companion service, and off, it stops`() {
        connect()
        assertTrue(GuardianAccessibilityService.running)
        assertEquals(GuardianService::class.java.name, shadowOf(app).nextStartedService?.component?.className)

        service.onUnbind(null)
        assertFalse(GuardianAccessibilityService.running)
    }
}
