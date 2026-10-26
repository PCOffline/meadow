package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.requests.RequestsContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Requests tab's own button: asking for extra time from there. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class RequestsFlowTest : ScreenTest() {
    private fun requests() = show { RequestsContent(DemoGarden.robin, incoming = emptyList(), outgoing = emptyList()) }

    @Test
    fun `the ask button has a name`() {
        requests()
        assertAccessible()
    }

    // Opening it is checked on a phone (AskJourneyTest): Robolectric never settles with a dialog open.
}
