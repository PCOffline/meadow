package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.ShareCommitment
import dev.pcoffline.meadow.ui.components.ShareCommitmentContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Sharing a commitment: in a Meadow chat with someone in the circle, or through other apps. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ShareCommitmentTest : ScreenTest() {
    private val words = "I'm keeping Pebbles to 45m a day with Meadow 🌱"

    @Test
    fun `the words are shown, and tapping someone sends them to their chat`() {
        val sent = mutableListOf<Pair<String, String>>()
        var others = 0
        show {
            ShareCommitmentContent(words, listOf(DemoGarden.maya, DemoGarden.theo), onSend = { p, m -> sent += p.uid to m }, onOtherApps = { others++ })
        }
        compose.onNodeWithText(words).assertExists()
        node(R.string.share_commitment_in_meadow).assertExists()
        compose.onNode(hasText(DemoGarden.maya.displayName) and hasClickAction()).performScrollTo().performClick()
        assertEquals(listOf(DemoGarden.maya.uid to text(R.string.share_commitment_sent, DemoGarden.maya.displayName)), sent)
        node(R.string.share_commitment_other_apps).performScrollTo().performClick()
        assertEquals(1, others)
        assertAccessible()
    }

    @Test
    fun `with nobody in the circle, only other apps are offered`() {
        show { ShareCommitmentContent(words, emptyList(), onSend = { _, _ -> }, onOtherApps = {}) }
        node(R.string.share_commitment_in_meadow).assertDoesNotExist()
        node(R.string.share_commitment_other_apps).assertExists()
        assertAccessible()
    }

    @Test
    fun `while sending, nothing else can be tapped`() {
        show { ShareCommitmentContent(words, listOf(DemoGarden.maya), busy = true, onSend = { _, _ -> }, onOtherApps = {}) }
        compose.onNode(hasText(DemoGarden.maya.displayName) and hasClickAction()).assertIsNotEnabled()
        node(R.string.share_commitment_other_apps).assertIsNotEnabled()
    }

    @Test
    fun `opening and closing the sheet holds the words until it's done`() {
        ShareCommitment.open(words)
        assertEquals(words, ShareCommitment.text.value)
        ShareCommitment.close()
        assertNull(ShareCommitment.text.value)
    }
}
