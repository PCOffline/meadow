package dev.pcoffline.meadow

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** How the main activity handles the links and notifications that open it. Here the app has no backend. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class MainActivityTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        AppGraph.init(app)
        AppGraph.store.clearAll()
    }

    private fun open(intent: Intent) = Robolectric.buildActivity(MainActivity::class.java, intent).setup()

    private fun viewIntent(link: String) = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setClass(app, MainActivity::class.java)

    @Test
    fun `an invite link is kept until the person has a garden`() {
        open(viewIntent("https://demo-meadow.web.app/i/$TOKEN"))
        assertEquals(TOKEN, AppGraph.store.pendingInvite.value)
    }

    @Test
    fun `an invite link in the app's own scheme is kept too`() {
        open(viewIntent("meadow://invite/$TOKEN"))
        assertEquals(TOKEN, AppGraph.store.pendingInvite.value)
    }

    @Test
    fun `a link reopened from recents was already used, so it is ignored`() {
        open(viewIntent("https://demo-meadow.web.app/i/$TOKEN").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertNull(AppGraph.store.pendingInvite.value)
    }

    @Test
    fun `a link that isn't an invite keeps nothing`() {
        open(viewIntent("https://example.com/somewhere"))
        assertNull(AppGraph.store.pendingInvite.value)
    }

    @Test
    fun `an invite that arrives while the app is open is kept`() {
        val activity = open(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "settings"))
        assertNull(AppGraph.store.pendingInvite.value)
        activity.newIntent(viewIntent("meadow://invite/$TOKEN"))
        assertEquals(TOKEN, AppGraph.store.pendingInvite.value)
    }

    @Test
    fun `restored after the system closed it, it doesn't use the first link again`() {
        val controller = open(viewIntent("meadow://invite/$TOKEN"))
        AppGraph.store.setPendingInvite(null)
        controller.recreate()
        assertNull(AppGraph.store.pendingInvite.value)
    }

    private companion object {
        const val TOKEN = "AbCdEfGh12345678"
    }
}
