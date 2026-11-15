package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    @Test
    fun story() {
        show(theme = GardenTheme.POND, firstCaption = "Each preview has three flowers, in the petals that its garden gives apps.") {
            val theme = garden
            if (theme == null) {
                Box(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) { AppearanceCard() }
            } else {
                MeadowTheme(theme, if (night) DarkMode.DARK else DarkMode.LIGHT) {
                    CompositionLocalProvider(LocalSkyHour provides if (night) 22 else 10) {
                        GardenContent(DemoGarden.robin, DemoGarden.garden)
                    }
                }
            }
        }
        step("Meadow: cosmos, aster, buttercup, poppy and forget-me-not. A nodding flower keeps its leaves on its stem.") {
            garden = GardenTheme.MEADOW
        }
        step("Cherry blossom: cherry, plum blossom, peony, wisteria and kerria.") {
            garden = GardenTheme.BLOSSOM
        }
        step("Lavender field: lavender, periwinkle, iris, sweet pea and a yellow pansy.") {
            garden = GardenTheme.LAVENDER
        }
        step("Sunflower: sunflower, marigold, zinnia, cornflower and blanket flower.") {
            garden = GardenTheme.SUNFLOWER
        }
        step("Lily pond: pink and blue water lilies, water hyacinth, flag iris and loosestrife.") {
            garden = GardenTheme.POND
        }
        step("Mushroom forest: the caps are as before. A toadstool has no centre.") {
            garden = GardenTheme.MUSHROOM
        }
        step("Lily pond by night: each petal still stands out from the dark page.") {
            garden = GardenTheme.POND
            night = true
        }
    }
}
