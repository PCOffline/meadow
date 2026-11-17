package dev.pcoffline.meadow.enforcement

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Schedule
import dev.pcoffline.meadow.data.TimeWindow
import kotlinx.coroutines.job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * What the guardian sees on screen: the app in front (a real screen of it, not a dialog or the
 * system bar), apps beside it in split screen, and floating windows. Only limited apps that are
 * resting are covered.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class GuardianWindowsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var controller: ServiceController<GuardianAccessibilityService>
    private lateinit var service: GuardianAccessibilityService
    private val opened = ArrayList<String>()

    private val shadow: ShadowAccessibilityService
        get() = Shadow.extract(service)

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        controller = Robolectric.buildService(GuardianAccessibilityService::class.java).create()
        service = controller.get()
        service.windowScanMs = 0
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(app).clearNextStartedActivities()
        BlockActivity.markShown("")
        BlockActivity.showing = null
    }

    @After
    fun tearDown() {
        controller.destroy()
        AppGraph.store.clearAll()
    }

    /** Hours far from now, so an app limited to them is resting. */
    private fun asleep() = Schedule((1..7).associateWith { emptyList<TimeWindow>() })

    private fun resting(pkg: String) = AppLimit(pkg, pkg, 30, 0, schedule = asleep())

    private fun window(id: Int, pkg: String?, pip: Boolean = false, type: Int = AccessibilityWindowInfo.TYPE_APPLICATION): AccessibilityWindowInfo =
        AccessibilityWindowInfo().also { w ->
            val s = shadowOf(w)
            s.setId(id)
            s.setType(type)
            s.setPictureInPicture(pip)
            if (pkg != null) s.setRoot(AccessibilityNodeInfo().apply { packageName = pkg })
        }

    private fun windowsChanged() {
        service.onAccessibilityEvent(AccessibilityEvent(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun blockScreensOpened(): List<String> {
        while (true) {
            val intent = shadowOf(app).nextStartedActivity ?: break
            if (intent.component?.className == BlockActivity::class.java.name) opened += intent.getStringExtra(BlockActivity.EXTRA_PACKAGE).orEmpty()
        }
        return opened
    }

    private fun waitUntil(done: () -> Boolean) {
        val main = shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 15_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            main.idle()
            Thread.sleep(10)
        }
        main.idle()
    }

    /** Installs [pkg] with a launcher screen named [pkg].Main. */
    private fun install(pkg: String) {
        val pm = shadowOf(app.packageManager)
        val info = ApplicationInfo().apply { packageName = pkg }
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = info })
        val main = ComponentName(pkg, "$pkg.Main")
        pm.addOrUpdateActivity(ActivityInfo().apply { packageName = pkg; name = main.className; applicationInfo = info })
        pm.addIntentFilterForActivity(main, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    private fun opened(pkg: String, className: String?) {
        service.onAccessibilityEvent(
            AccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
                packageName = pkg
                this.className = className
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a resting app beside another in split screen is covered, and Home leaves it once`() {
        AppGraph.store.setLimits(listOf(resting(SIDE)))
        service.listWindows = {
            listOf(
                window(1, FRONT),
                window(2, SIDE),
                // Meadow's own window, an unlimited app, a window nobody can read and the keyboard don't count.
                window(3, app.packageName),
                window(4, "com.example.free"),
                window(5, null),
                window(6, SIDE, type = AccessibilityWindowInfo.TYPE_INPUT_METHOD),
            )
        }
        service.onForeground(FRONT)
        windowsChanged()

        waitUntil { SIDE in blockScreensOpened() }
        waitUntil { GLOBAL_ACTION_HOME in shadow.globalActionsPerformed }
        assertEquals(listOf(GLOBAL_ACTION_HOME), shadow.globalActionsPerformed)
        assertEquals(listOf(SIDE), blockScreensOpened().distinct())
    }

    @Test
    fun `a floating window of a resting app just left is closed`() {
        AppGraph.store.setLimits(listOf(resting(SIDE)))
        val pip = window(7, SIDE, pip = true)
        shadow.setWindows(listOf(pip))
        service.listWindows = { listOf(window(1, FRONT), pip) }
        // The resting app was in front, then its video went on floating over the next app.
        AppGraph.store.setLimits(emptyList())
        service.onForeground(SIDE)
        AppGraph.store.setLimits(listOf(resting(SIDE)))
        service.onForeground(FRONT)
        windowsChanged()

        waitUntil { SIDE in blockScreensOpened() }
        waitUntil { service.scope.coroutineContext.job.children.none() }
    }

    // Robolectric answers only the older way of asking for a screen (see PackageQueriesTest).
    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun `only a real screen of an app counts as switching to it`() {
        install(FRONT)
        AppGraph.store.setLimits(listOf(resting(FRONT)))

        // The system bar, a window with no screen name and a dialog that isn't a screen of the app.
        opened("com.android.systemui", "com.android.systemui.Bar")
        opened(FRONT, null)
        opened(FRONT, "android.app.Dialog")
        opened(FRONT, "android.app.Dialog")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(blockScreensOpened().isEmpty())

        opened(FRONT, "$FRONT.Main")
        waitUntil { FRONT in blockScreensOpened() }
    }

    @Test
    fun `switching to the same app again changes nothing`() {
        service.onForeground(FRONT)
        service.onForeground(FRONT)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(blockScreensOpened().isEmpty())
    }

    @Test
    fun `with no windows to read, nothing is covered`() {
        AppGraph.store.setLimits(listOf(resting(SIDE)))
        service.listWindows = { null }
        service.onForeground(FRONT)
        windowsChanged()
        assertTrue(blockScreensOpened().isEmpty())
    }

    private companion object {
        const val FRONT = "com.example.notes"
        const val SIDE = "com.example.reels"
    }
}
