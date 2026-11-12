package dev.pcoffline.meadow.ui.screens

import android.app.Application
import dev.pcoffline.meadow.ui.components.ShareCommitment
import dev.pcoffline.meadow.R
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsActions
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
    fun `a card with more to say is no taller than the one beside it`() {
        // Extra time adds a line to the first card only.
        val budgets = listOf(budget("demo.reader", "Reader", bonus = 15), budget("demo.tiktok", "TikTok"))
        show { GardenContent(DemoGarden.robin, GardenState(budgets = budgets, loaded = true)) }
        assertEquals(height("Reader"), height("TikTok"), 0.5f)
    }

    @Test
    fun `a long press on a card offers to share its saved limit, or to change it`() {
        val reader = budget("demo.reader", "Reader")
        val opened = mutableListOf<String>()
        show { GardenContent(DemoGarden.robin, GardenState(budgets = listOf(reader), loaded = true), onOpenLimit = { opened += it }) }
        try {
            compose.onNode(hasClickAction() and hasText("Reader", substring = true)).performScrollTo().performTouchInput { longClick() }
            node(R.string.limit_share).performClick()
            assertEquals(reader.limit, ShareCommitment.shared.value?.limit)
        } finally {
            ShareCommitment.close()
        }
        compose.onNode(hasClickAction() and hasText("Reader", substring = true)).performTouchInput { longClick() }
        node(R.string.limit_editor_existing).performClick()
        assertEquals(listOf("demo.reader"), opened)
        assertAccessible()
    }

    @Test
    fun `a screen reader can share a card's limit straight away`() {
        val reader = budget("demo.reader", "Reader")
        show { GardenContent(DemoGarden.robin, GardenState(budgets = listOf(reader), loaded = true)) }
        try {
            val card = compose.onNode(hasClickAction() and hasText("Reader", substring = true)).fetchSemanticsNode()
            val share = card.config[SemanticsActions.CustomActions].single { it.label == text(R.string.limit_share) }
            compose.runOnUiThread { share.action() }
            assertEquals(reader.limit, ShareCommitment.shared.value?.limit)
        } finally {
            ShareCommitment.close()
        }
    }
}
