package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.enforcement.ProtectionState
import dev.pcoffline.meadow.ui.setup.PushTest
import dev.pcoffline.meadow.ui.setup.TroubleshootContent
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Troubleshoot says why chats and requests might not reach this phone, and can prove they do. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class TroubleshootTest : ScreenTest() {
    private val ready = ProtectionState(true, true, true, true, true, batteryUnrestricted = true, notificationListener = true)

    @Test
    fun `notifications turned off are named, with a way to turn them on and no test`() {
        var silent by mutableStateOf<List<String>?>(null)
        var opened = 0
        show { TroubleshootContent(ready, emptyList(), silentChannels = silent, onNotificationSettings = { opened++ }) }
        node(R.string.trouble_notify_off).assertExists()
        node(R.string.trouble_notify_test).assertDoesNotExist()
        node(R.string.trouble_notify_settings).performClick()
        assertEquals(1, opened)
        assertAccessible()

        silent = listOf("Chat messages", "New requests")
        compose.waitForIdle()
        node(R.string.trouble_notify_silent, "Chat messages, New requests").assertExists()
        node(R.string.trouble_notify_settings).assertExists()
    }

    @Test
    fun `a test says it's sending, then what happened`() {
        val answer = CompletableDeferred<PushTest>()
        show { TroubleshootContent(ready, emptyList(), onTestPush = { answer.await() }) }
        node(R.string.trouble_notify_ok).assertExists()
        node(R.string.trouble_notify_test).performClick()
        node(R.string.trouble_notify_sending).assertExists()
        answer.complete(PushTest.Sent)
        compose.waitForIdle()
        node(R.string.trouble_notify_sent).assertExists()
        node(R.string.trouble_notify_test).assertExists()
        assertAccessible()
    }

    @Test
    fun `a phone that can't be reached says why`() {
        var next: PushTest = PushTest.NoPhone
        show { TroubleshootContent(ready, emptyList(), onTestPush = { next }) }
        node(R.string.trouble_notify_test).performClick()
        node(R.string.trouble_notify_no_phone).performScrollTo().assertExists()

        next = PushTest.Undelivered
        node(R.string.trouble_notify_test).performClick()
        node(R.string.trouble_notify_undelivered).assertExists()

        next = PushTest.Failed("You seem to be offline.")
        node(R.string.trouble_notify_test).performClick()
        compose.onNodeWithText("You seem to be offline.").assertExists()
    }

    @Test
    fun `a test counts as sent when any phone got it`() {
        assertEquals(PushTest.Sent, PushTest.of(phones = 2, delivered = 1))
        assertEquals(PushTest.NoPhone, PushTest.of(phones = 0, delivered = 0))
        assertEquals(PushTest.Undelivered, PushTest.of(phones = 2, delivered = 0))
    }
}
