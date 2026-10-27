package dev.pcoffline.meadow.ui.screens

import android.app.Application
import dev.pcoffline.meadow.ui.settings.PRIVACY_SETTLE_MS
import dev.pcoffline.meadow.ui.minutesText
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.annotation.StringRes
import android.text.format.DateFormat
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
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.WeekRule
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.AccountEntry
import dev.pcoffline.meadow.ui.components.LocalAccount
import dev.pcoffline.meadow.ui.circle.People
import dev.pcoffline.meadow.ui.circle.PeopleContent
import dev.pcoffline.meadow.ui.settings.AppearanceCard
import dev.pcoffline.meadow.ui.settings.PrivacyPreviewContent
import dev.pcoffline.meadow.ui.settings.SettingsScreen
import dev.pcoffline.meadow.ui.settings.WeekStartCard
import dev.pcoffline.meadow.ui.stats.StatsContent
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
                    else -> PeopleContent(robin, People())
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
    fun `every theme card is as tall as the rest, whatever the length of its name`() {
        show { AppearanceCard() }
        val heights = GardenTheme.entries.map { theme ->
            compose.onNode(hasText(text(theme.label), substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot.height
        }
        // Within a pixel: sizes in dp land on fractions of a pixel, rounded either way.
        assertTrue(heights.toString(), heights.max() - heights.min() <= 1f)
        val modes = DarkMode.entries.map { node(it.label).fetchSemanticsNode().boundsInRoot.height }
        assertEquals(modes.toString(), 1, modes.distinct().size)
    }

    @Test
    fun `the week can start on Saturday, Sunday or Monday, and weekly limits follow when the week ends`() {
        AppGraph.store.setLimits(listOf(AppLimit("com.example.app", "Example", dailyMinutes = 0, weeklyMinutes = 300)))
        AppGraph.store.setWeekStart(DayOfWeek.MONDAY)
        show { WeekStartCard() }
        val sunday = DayOfWeek.SUNDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        compose.onNodeWithText(sunday).performClick()
        assertEquals(DayOfWeek.SUNDAY, AppGraph.store.weekRule.value.first)
        // This week's limits stay as they are, and the card says until when.
        val from = AppGraph.store.weekRule.value.pendingOn(Days.today())!!
        val date = from.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")))
        node(R.string.settings_week_start_pending, date).assertExists()
        assertAccessible(requireHeading = false)

        // Back to the day in force: nothing waits any more.
        compose.onNodeWithText(DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())).performClick()
        assertEquals(WeekRule(), AppGraph.store.weekRule.value)
        node(R.string.settings_week_start_pending, date).assertDoesNotExist()
    }

    @Test
    fun `with no weekly limit, a new first day needs no explaining`() {
        AppGraph.store.setLimits(emptyList())
        AppGraph.store.setWeekStart(DayOfWeek.MONDAY)
        show { WeekStartCard() }
        compose.onNodeWithText(DayOfWeek.SATURDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault())).performClick()
        assertEquals(WeekRule(DayOfWeek.SATURDAY), AppGraph.store.weekRule.value)
        assertTrue(compose.onAllNodesWithText(text(R.string.settings_week_start_pending, "").substringBefore(",").trim(), substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `privacy offers a look at what Bumblebees see`() {
        var previews = 0
        show { SettingsScreen(robin, onBack = {}, onOpenSetup = {}, onOpenProfile = {}, onOpenLegal = {}, onOpenPrivacyPreview = { previews++ }) }
        node(R.string.settings_privacy_preview).performScrollTo().performClick()
        assertEquals(1, previews)
    }

    private fun preview(privacy: Privacy = DemoGarden.sharesEverything, save: suspend (Privacy) -> Unit = {}) = show {
        PrivacyPreviewContent(robin.copy(privacy = privacy), DemoGarden.today, DemoGarden.progress, DemoGarden.myLimits, save = save)
    }

    private fun switch(@StringRes title: Int) = compose.onNode(isToggleable() and hasText(text(title)))

    /** The progress card's title, not the switch that shares it. */
    private fun progressCard() = compose.onNode(hasText(text(R.string.progress_title)) and !isToggleable())

    @Test
    fun `the preview shows each part that's shared`() {
        preview()
        for (part in listOf(R.string.settings_share_limits, R.string.settings_share_time, R.string.settings_share_apps, R.string.settings_share_progress)) {
            switch(part).assertIsOn()
        }
        node(R.string.friend_screen_time, app.resources.getString(R.string.duration_hours_minutes, 2, 14)).performScrollTo().assertExists()
        compose.onNodeWithText("Maps").performScrollTo().assertExists()
        // A top app with a limit shows its time against it, from the shared limits.
        node(R.string.friend_used_of, app.resources.minutesText(52), app.resources.minutesText(60)).performScrollTo().assertExists()
        progressCard().performScrollTo().assertExists()
        node(R.string.progress_kept, 5, 7).performScrollTo().assertExists()
        node(R.string.friend_limits).performScrollTo().assertExists()
        compose.onNodeWithText(robin.bio).performScrollTo().assertExists()
        node(R.string.friend_nothing_shared, robin.displayName).assertDoesNotExist()
        assertAccessible()
    }

    @Test
    fun `each part comes off the page on its own`() {
        preview(DemoGarden.sharesEverything.copy(shareTime = false, shareLimits = false))
        node(R.string.friend_screen_time, app.resources.getString(R.string.duration_hours_minutes, 2, 14)).assertDoesNotExist()
        node(R.string.friend_limits).assertDoesNotExist()
        // Top apps stay, without the limits that are now private.
        compose.onNodeWithText("Maps").performScrollTo().assertExists()
        node(R.string.friend_used_of, app.resources.minutesText(52), app.resources.minutesText(60)).assertDoesNotExist()
        progressCard().performScrollTo().assertExists()
    }

    @Test
    fun `with nothing shared, the preview says so`() {
        preview(Privacy())
        node(R.string.friend_nothing_shared, robin.displayName).performScrollTo().assertExists()
        node(R.string.friend_limits).assertDoesNotExist()
        compose.onAllNodesWithText("Maps").assertCountEquals(0)
        assertAccessible()
    }

    @Test
    fun `a switch moves at once, and a quick run of changes is saved once`() {
        val saves = mutableListOf<Privacy>()
        preview { saves += it }
        compose.mainClock.autoAdvance = false
        switch(R.string.settings_share_time).performClick()
        compose.mainClock.advanceTimeByFrame()
        // Straight away, before anything is saved: the switch and the page below.
        switch(R.string.settings_share_time).assertIsOff()
        node(R.string.friend_screen_time, app.resources.getString(R.string.duration_hours_minutes, 2, 14)).assertDoesNotExist()
        switch(R.string.settings_share_apps).performClick()
        compose.mainClock.advanceTimeBy(PRIVACY_SETTLE_MS / 2)
        assertEquals(emptyList<Privacy>(), saves)
        compose.mainClock.advanceTimeBy(PRIVACY_SETTLE_MS)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { saves.isNotEmpty() }
        assertEquals(listOf(DemoGarden.sharesEverything.copy(shareTime = false, shareApps = false)), saves)
    }

    @Test
    fun `a change that can't be saved goes back, and says why`() {
        preview { throw IllegalStateException("no network") }
        compose.mainClock.autoAdvance = false
        switch(R.string.settings_share_progress).performClick()
        compose.mainClock.advanceTimeByFrame()
        switch(R.string.settings_share_progress).assertIsOff()
        compose.mainClock.advanceTimeBy(PRIVACY_SETTLE_MS * 2)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.error_generic), substring = true).fetchSemanticsNodes().isNotEmpty() }
        switch(R.string.settings_share_progress).assertIsOn()
    }
}
