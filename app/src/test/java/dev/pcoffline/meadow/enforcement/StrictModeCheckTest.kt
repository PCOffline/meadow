package dev.pcoffline.meadow.enforcement

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityNodeInfo
import org.robolectric.shadows.ShadowAccessibilityService

/** In strict mode the screens that could switch Meadow off are left, read off the main thread. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class StrictModeCheckTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<GuardianAccessibilityService>
    private lateinit var service: GuardianAccessibilityService

    private val shadow: ShadowAccessibilityService
        get() = Shadow.extract(service)

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.setStrictMode(true)
        controller = Robolectric.buildService(GuardianAccessibilityService::class.java).create()
        service = controller.get()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        controller.destroy()
        AppGraph.store.setStrictMode(false)
    }

    @Test
    fun `Meadow's app info is left, without reading it on the main thread`() {
        shadow.setRootInActiveWindow(screen("Meadow", "Force stop", "Uninstall"))

        service.onAccessibilityEvent(event(SETTINGS))
        // Nothing happened while the event was handled: the screen is read in the background.
        assertEquals(emptyList<Int>(), shadow.globalActionsPerformed)

        settle()
        assertEquals(LEFT, shadow.globalActionsPerformed)
    }

    @Test
    fun `other settings screens are left alone`() {
        shadow.setRootInActiveWindow(screen("Network & internet", "Bluetooth"))

        service.onAccessibilityEvent(event(SETTINGS))
        settle()

        assertEquals(emptyList<Int>(), shadow.globalActionsPerformed)
    }

    @Test
    fun `a screen that changes during a read is read again once it ends`() {
        shadow.setRootInActiveWindow(screen("Network & internet", "Bluetooth"))
        service.onAccessibilityEvent(event(SETTINGS))
        // The first read is done once it hands its answer to the main thread.
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (shadowOf(Looper.getMainLooper()).isIdle && System.currentTimeMillis() < deadline) Thread.sleep(5)

        shadow.setRootInActiveWindow(screen("Meadow", "Force stop"))
        service.onAccessibilityEvent(event(SETTINGS))
        settle()

        assertEquals(LEFT, shadow.globalActionsPerformed)
    }

    /** Runs the main thread until the service's background work is done. */
    private fun settle() {
        val main = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        do {
            main.idle()
            Thread.sleep(5)
        } while (service.scope.coroutineContext.job.children.any() && System.currentTimeMillis() < deadline)
        main.idle()
    }

    @Suppress("DEPRECATION") // The constructor needs API 30; obtain() works on every version.
    private fun event(pkg: String): AccessibilityEvent =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply { packageName = pkg }

    @Suppress("DEPRECATION")
    private fun screen(vararg lines: String): AccessibilityNodeInfo {
        val root = AccessibilityNodeInfo.obtain()
        val shadowRoot = Shadow.extract<ShadowAccessibilityNodeInfo>(root)
        for (line in lines) shadowRoot.addChild(AccessibilityNodeInfo.obtain().apply { text = line })
        return root
    }

    private companion object {
        const val SETTINGS = "com.android.settings"
        const val TIMEOUT_MS = 5_000L
        val LEFT = listOf(AccessibilityService.GLOBAL_ACTION_BACK, AccessibilityService.GLOBAL_ACTION_HOME)
    }
}
