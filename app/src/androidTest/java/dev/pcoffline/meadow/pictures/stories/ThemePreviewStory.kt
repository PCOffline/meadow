package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.settings.AppearanceCard
import dev.pcoffline.meadow.ui.theme.GardenTheme
import org.junit.Test

/** A theme's preview shows the flowers that its garden grows. */
class ThemePreviewStory : Story("Each theme's preview shows the flowers its garden grows") {
    private val garden = mutableStateOf(false)

    @Test
    fun story() {
        show(theme = GardenTheme.POND, firstCaption = "Each preview has three flowers, in the petals that its garden gives apps.") {
            if (garden.value) {
                GardenContent(DemoGarden.robin, DemoGarden.garden)
            } else {
                Box(Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) { AppearanceCard() }
            }
        }
        step("The Lily pond garden: each flower stands out from its card as much as the preview's.") {
            garden.value = true
        }
    }
}
