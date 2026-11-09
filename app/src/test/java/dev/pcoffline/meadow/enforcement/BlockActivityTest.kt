package dev.pcoffline.meadow.enforcement

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.enforcement.BlockActivity.Companion.openIntents
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** The rest screen that covers an app or a website when its time is up. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BlockActivityTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private var controller: ActivityController<BlockActivity>? = null

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
        // The rest screen's sky moves without end unless motion is off, as on the test phones.
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        BlockActivity.showing = null
        BlockActivity.markShown("")
        shadowOf(app).clearNextStartedActivities()
    }

    @After
    fun tearDown() {
        controller?.let { if (!it.get().isDestroyed) it.pause().stop().destroy() }
        BlockActivity.showing = null
    }

    private fun open(pkg: String, browser: String? = null): ActivityController<BlockActivity> {
        val intent = Intent(app, BlockActivity::class.java).putExtra(BlockActivity.EXTRA_PACKAGE, pkg).putExtra(BlockActivity.EXTRA_BROWSER, browser)
        return Robolectric.buildActivity(BlockActivity::class.java, intent).also { controller = it }.setup()
    }

    @Test
    fun `while on screen it says which app it covers, and stops saying so when it leaves`() {
        val before = SystemClock.elapsedRealtime()
        val screen = open(APP)

        assertTrue(BlockActivity.isShowingFor(APP))
        assertFalse(BlockActivity.isShowingFor(OTHER))
        assertTrue(BlockActivity.shownSince(APP, before))
        assertFalse(BlockActivity.shownSince(OTHER, before))

        screen.pause()
        assertFalse(BlockActivity.isShowingFor(APP))
        assertNull(BlockActivity.showing)
    }

    @Test
    fun `a new app's time up moves the open screen to that app`() {
        val screen = open(APP)
        screen.pause()

        screen.newIntent(Intent(app, BlockActivity::class.java).putExtra(BlockActivity.EXTRA_PACKAGE, OTHER)).resume()

        assertTrue(BlockActivity.isShowingFor(OTHER))
        assertEquals(OTHER, screen.get().intent.getStringExtra(BlockActivity.EXTRA_PACKAGE))
    }

    @Test
    fun `back goes Home and closes the screen`() {
        val screen = open(APP)

        screen.get().onBackPressedDispatcher.onBackPressed()

        val home = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, home.action)
        assertTrue(home.hasCategory(Intent.CATEGORY_HOME))
        assertTrue(screen.get().isFinishing)
    }

    @Test
    fun `launch opens the screen over everything, for the app and the browser`() {
        BlockActivity.launch(app, SITE, BROWSER)

        val started = shadowOf(app).nextStartedActivity
        assertEquals(BlockActivity::class.java.name, started.component?.className)
        assertEquals(SITE, started.getStringExtra(BlockActivity.EXTRA_PACKAGE))
        assertEquals(BROWSER, started.getStringExtra(BlockActivity.EXTRA_BROWSER))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun `a website opens again in its browser first, then in any browser`() {
        val intents = app.openIntents(SITE, BROWSER)

        assertEquals(2, intents.size)
        assertEquals(BROWSER, intents[0].`package`)
        assertNull(intents[1].`package`)
        intents.forEach {
            assertEquals(Intent.ACTION_VIEW, it.action)
            assertEquals("https://example.org", it.dataString)
        }
        assertEquals(1, app.openIntents(SITE, null).size)
    }

    @Test
    fun `an app opens again by its launcher entry, and one that is gone has none`() {
        installLauncherApp(APP)

        assertEquals(APP, app.openIntents(APP, null).single().`package`)
        assertTrue(app.openIntents(OTHER, null).isEmpty())
    }

    private fun installLauncherApp(pkg: String) {
        val pm = shadowOf(app.packageManager)
        pm.installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = ApplicationInfo().apply { packageName = pkg } })
        val component = ComponentName(pkg, "$pkg.Main")
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }

    private companion object {
        const val APP = "com.example.feed"
        const val OTHER = "com.example.other"
        const val SITE = "site:example.org"
        const val BROWSER = "com.example.browser"
    }
}
