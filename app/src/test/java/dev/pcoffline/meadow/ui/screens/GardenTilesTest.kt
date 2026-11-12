package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.home.GardenState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The flower cards side by side in the garden are the same height (#75). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h1600dp-xxhdpi")
class GardenTilesTest : ScreenTest() {
    private fun budget(pkg: String, name: String, bonus: Int = 0) = AppBudget(
        AppLimit(pkg, name, dailyMinutes = 60, weeklyMinutes = 0),
        usedTodayMs = 10 * 60_000L, usedWeekMs = 10 * 60_000L, bonusTodayMinutes = bonus, bonusWeekMinutes = 0,
    )

    private fun height(name: String) = compose.onNode(hasClickAction() and hasText(name, substring = true))
        .performScrollTo().fetchSemanticsNode().boundsInRoot.height

    @Test
    fun `with a photo, the greeting ends with your emoji`() {
        val robin = DemoGarden.robin.copy(avatar = DemoGarden.robin.avatar.copy(photoUrl = "https://example.invalid/robin.jpg"))
        show { GardenContent(robin, GardenState(loaded = true)) }
        compose.onNode(hasText("${robin.displayName} ${robin.avatar.emoji}", substring = true)).assertExists()
    }

    @Test
    fun `without a photo, the greeting has only your name`() {
        show { GardenContent(DemoGarden.robin, GardenState(loaded = true)) }
        compose.onNode(hasText("${DemoGarden.robin.displayName} ${DemoGarden.robin.avatar.emoji}", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `a card with more to say is no taller than the one beside it`() {
        // Extra time adds a line to the first card only.
        val budgets = listOf(budget("demo.reader", "Reader", bonus = 15), budget("demo.tiktok", "TikTok"))
        show { GardenContent(DemoGarden.robin, GardenState(budgets = budgets, loaded = true)) }
        assertEquals(height("Reader"), height("TikTok"), 0.5f)
    }
}
