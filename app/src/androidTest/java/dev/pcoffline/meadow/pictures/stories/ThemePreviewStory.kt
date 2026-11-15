package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.settings.AppearanceCard
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Test

/**
 * A theme's preview shows the flowers that its garden grows. Each theme grows five real flowers,
 * each with the centre that the real flower has, and leaves that stay on the stem as it nods.
 */
class ThemePreviewStory : Story("Each theme's preview shows the flowers its garden grows") {
    private var garden by mutableStateOf<GardenTheme?>(null)
    private var night by mutableStateOf(false)

    /**
     * Shows [theme]'s garden, and waits until it's on screen. The first garden after the previews
     * takes longer to draw than the step waits, so its picture showed the previews.
     */
    private fun onScreen(theme: GardenTheme, night: Boolean = false) {
        val first = garden == null
        compose.runOnUiThread {
            garden = theme
            this.night = night
        }
        val title = compose.activity.getString(R.string.garden_title)
        compose.waitUntil(GARDEN_MS) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        // The emulator draws in software: the frame reaches the screen a moment after the tree.
        if (first) Thread.sleep(GARDEN_MS / 5)
    }

    private companion object {
        const val GARDEN_MS = 10_000L
    }

    @Test
    fun story() {
        show(theme = GardenTheme.POND, firstCaption = "Each preview has three flowers, in the petals that its garden gives apps.") {
            val theme = garden
            if (theme == null) {
                Box(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) { AppearanceCard() }
            } else {
                MeadowTheme(theme, if (night) DarkMode.DARK else DarkMode.LIGHT) {
                    // On this theme's own page, as the app draws it: by night, the page is dark too.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        CompositionLocalProvider(LocalSkyHour provides if (night) 22 else 10) {
                            GardenContent(DemoGarden.robin, DemoGarden.garden)
                        }
                    }
                }
            }
        }
        step("Meadow: cosmos, aster, buttercup, poppy and forget-me-not. A nodding flower keeps its leaves on its stem.") {
            onScreen(GardenTheme.MEADOW)
        }
        step("Cherry blossom: cherry, plum blossom, peony, wisteria and kerria.") {
            onScreen(GardenTheme.BLOSSOM)
        }
        step("Lavender field: lavender, periwinkle, iris, sweet pea and a yellow pansy.") {
            onScreen(GardenTheme.LAVENDER)
        }
        step("Sunflower: sunflower, marigold, zinnia, cornflower and blanket flower.") {
            onScreen(GardenTheme.SUNFLOWER)
        }
        step("Lily pond: pink and blue water lilies, water hyacinth, flag iris and loosestrife.") {
            onScreen(GardenTheme.POND)
        }
        step("Mushroom forest: the caps are as before. A toadstool has no centre.") {
            onScreen(GardenTheme.MUSHROOM)
        }
        step("Lily pond by night: each petal still stands out from the dark page.") {
            onScreen(GardenTheme.POND, night = true)
        }
    }
}
