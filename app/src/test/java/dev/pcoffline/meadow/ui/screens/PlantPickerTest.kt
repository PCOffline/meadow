package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.ui.components.flora.namedPlace
import dev.pcoffline.meadow.ui.components.flora.plantsOf
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.limits.LimitEditor
import dev.pcoffline.meadow.ui.limits.LimitEditorState
import dev.pcoffline.meadow.ui.theme.GardenTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Choosing the plant of a limit in the limit editor. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PlantPickerTest : ScreenTest() {
    private val pkg = "com.instagram.android"
    private val named = namedPlace(pkg)
    private val other = (named + 1) % 5
    private val saved = mutableListOf<AppLimit>()

    private fun editor(
        existing: AppLimit? = null,
        bees: Int = 0,
        theme: GardenTheme = GardenTheme.MEADOW,
        start: AppLimit? = null,
        suggest: Boolean = false,
    ) = show(theme) {
        Column {
            BackTopBar(text(R.string.limit_editor_existing), onBack = {})
            LimitEditor(
                pkg,
                LimitEditorState(existing = existing, appName = "Instagram", loaded = true, bumblebeeCount = bees),
                onDone = {},
                start = start,
                sendTo = if (suggest) ({ saved += it }) else null,
                save = { limit, _, _ ->
                    saved += limit
                    ChangeOutcome.APPLIED
                },
            )
        }
    }

    private fun plant(place: Int, theme: GardenTheme = GardenTheme.MEADOW) =
        compose.onNodeWithContentDescription(plantsOf(theme)[place].name)

    @Test
    fun `the editor offers the theme's five plants, with the app's own chosen`() {
        editor(theme = GardenTheme.POND)
        node(R.string.limit_editor_plant_title).performScrollTo()
        for (place in 0 until 5) {
            val choice = plant(place, GardenTheme.POND).performScrollTo()
            if (place == named) choice.assertIsSelected() else choice.assertIsNotSelected()
        }
        assertAccessible()
    }

    @Test
    fun `a new limit is planted with the plant chosen`() {
        editor()
        plant(other).performScrollTo().performClick()
        plant(other).assertIsSelected()
        plant(named).assertIsNotSelected()
        node(R.string.limit_editor_plant).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(other, saved.single().plant)
    }

    @Test
    fun `the saved plant shows chosen, and another plant alone is a change to save`() {
        val limit = AppLimit(pkg, "Instagram", 30, 0, plant = other)
        editor(existing = limit, bees = 1)
        plant(other).performScrollTo().assertIsSelected()
        plant(named).performClick()
        // A plant holds nothing back: with a Bumblebee it saves without asking.
        node(R.string.action_save).performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        // The app's own plant is saved as no choice, so it follows the app's name.
        assertEquals(limit.copy(plant = null), saved.single())
        compose.onNodeWithText(text(R.string.limit_editor_ask)).assertDoesNotExist()
    }

    @Test
    fun `a copied limit keeps the plant of the limit here`() {
        val here = AppLimit(pkg, "Instagram", 30, 0, plant = other)
        editor(existing = here, start = AppLimit(pkg, "Instagram", 20, 0, plant = named))
        plant(other).performScrollTo().assertIsSelected()
    }

    @Test
    fun `a suggestion for someone else has no plant to choose`() {
        editor(suggest = true)
        node(R.string.limit_editor_plant_title).assertDoesNotExist()
        plant(other).assertDoesNotExist()
        node(R.string.limit_suggest_send).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(null, saved.single().plant)
    }
}
