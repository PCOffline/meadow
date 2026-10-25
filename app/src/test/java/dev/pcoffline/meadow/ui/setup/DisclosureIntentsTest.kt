package dev.pcoffline.meadow.ui.setup

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Where each setup step sends you in Settings. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [34])
class DisclosureIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun actions(d: Disclosure) = d.intents(context).map { it.action }

    @Test
    fun `usage access opens Meadow's own page first, then the list`() {
        val intents = Disclosure.USAGE.intents(context)
        assertEquals(listOf(Settings.ACTION_USAGE_ACCESS_SETTINGS, Settings.ACTION_USAGE_ACCESS_SETTINGS), intents.map { it.action })
        assertEquals("package:${context.packageName}", intents.first().dataString)
    }

    @Test
    fun `the garden keeper opens the accessibility list, the only page an app may open`() {
        assertEquals(listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS), actions(Disclosure.KEEPER))
    }

    @Test
    fun `notification access opens Meadow's own page first, then the list`() {
        assertEquals(
            listOf(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            actions(Disclosure.LISTENER),
        )
        val component = Disclosure.LISTENER.intents(context).first().getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME)
        assertEquals(true, component?.startsWith(context.packageName))
    }
}
