package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.AccountEntry
import dev.pcoffline.meadow.ui.components.LocalAccount
import dev.pcoffline.meadow.ui.requests.RequestsContent
import dev.pcoffline.meadow.ui.settings.PrivacyPreviewContent
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The way to one's profile from every tab, the look in Settings, and the preview of what Bumblebees see. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ProfileAndPrivacyTest : ScreenTest() {
    private val robin = DemoGarden.robin

    @Test
    fun `each tab has the way to the profile, in the same corner`() {
        var opened = 0
        var tab by mutableIntStateOf(0)
        show {
            CompositionLocalProvider(LocalAccount provides AccountEntry(robin.avatar) { opened++ }) {
                when (tab) {
                    0 -> StatsContent(DemoGarden.stats)
                    else -> RequestsContent(robin, incoming = emptyList(), outgoing = emptyList())
                }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.profile_open)).performClick()
        assertAccessible()
        tab = 1
        compose.onNodeWithContentDescription(text(R.string.profile_open)).performClick()
        assertEquals(2, opened)
    }

    @Test
    fun `without anyone signed in there's no profile button`() {
        show { StatsContent(DemoGarden.stats) }
        compose.onAllNodesWithContentDescription(text(R.string.profile_open)).assertCountEquals(0)
    }

    @Test
    fun `the theme and night mode are in Settings`() {
        show { SettingsScreen(robin, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}) }
        node(R.string.settings_appearance_title).performScrollTo().assertExists()
        compose.onNode(hasText(text(GardenTheme.LAVENDER.label), substring = true) and hasClickAction()).performScrollTo().assertExists()
        node(R.string.dark_mode_dark).performScrollTo().assertExists()
        assertAccessible()
    }

    @Test
    fun `privacy offers a look at what Bumblebees see`() {
        var previews = 0
        show { SettingsScreen(robin, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}, onOpenPrivacyPreview = { previews++ }) }
        node(R.string.settings_privacy_preview).performScrollTo().performClick()
        assertEquals(1, previews)
    }

    @Test
    fun `the preview shows today's time and limits while it's shared`() {
        show { PrivacyPreviewContent(robin, DemoGarden.today, DemoGarden.myLimits) }
        compose.onNode(isToggleable() and hasText(text(R.string.settings_share_usage))).assertExists()
        node(R.string.friend_screen_time, app.resources.getString(R.string.duration_hours_minutes, 2, 14)).performScrollTo().assertExists()
        compose.onNodeWithText("Maps").performScrollTo().assertExists()
        compose.onNodeWithText(robin.bio).performScrollTo().assertExists()
        assertAccessible()
    }

    @Test
    fun `with sharing off, the preview shows what Bumblebees get instead`() {
        show { PrivacyPreviewContent(robin.copy(privacy = Privacy(shareUsage = false)), DemoGarden.today, DemoGarden.myLimits) }
        node(R.string.friend_no_usage_shared, robin.displayName).performScrollTo().assertExists()
        compose.onAllNodesWithText("Maps").assertCountEquals(0)
        // Limits are always theirs to see.
        compose.onNodeWithText("Instagram").performScrollTo().assertExists()
    }
}
