package dev.pcoffline.meadow.pictures.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.pcoffline.meadow.showcase.PlantSheet
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Test

/**
 * Each theme's five plants, at the size of a garden card, as the time of their apps goes: in full
 * bloom, with little time left, used up, and asleep. Then each other theme in bloom and used up.
 */
class PlantsShowTimeLeftStory : Story("Each theme grows its own plants, and they show the time that is left") {
    private var theme by mutableStateOf(GardenTheme.MEADOW)
    private var mode by mutableStateOf(DarkMode.LIGHT)
    private var bloom by mutableStateOf(1f)
    private var asleep by mutableStateOf(false)

    private fun look(theme: GardenTheme, bloom: Float, mode: DarkMode = DarkMode.LIGHT, asleep: Boolean = false) {
        this.theme = theme
        this.mode = mode
        this.bloom = bloom
        this.asleep = asleep
    }

    @Test
    fun story() {
        show(firstCaption = "Meadow, with all the time left: a cornflower, a corn poppy, a cosmos, a buttercup and an aster in full bloom.") {
            // The page draws its own background, as the app's Scaffold does, so the night page is dark.
            MeadowTheme(theme, mode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { PlantSheet(bloom, asleep) }
            }
        }
        step("Half of the time is left: each is still in full bloom.") { look(GardenTheme.MEADOW, 0.5f) }
        step("A tenth is left: each nods and closes a little, and two petals lie on the ground.") { look(GardenTheme.MEADOW, 0.1f) }
        step("The time is used up: each hangs its head and has lost petals, dried in its own colour. The poppy is a seed capsule.") { look(GardenTheme.MEADOW, 0f) }
        step("Outside its hours, each is a bud in full colour, also when its time is used up.") { look(GardenTheme.MEADOW, 0f, asleep = true) }

        step("Cherry blossom: a cherry, a grape hyacinth, a camellia, a wisteria and a kerria. They grow on brown twigs.") { look(GardenTheme.BLOSSOM, 1f) }
        step("Used up, the cherry keeps two petals, and the others lie on the ground, still pink.") { look(GardenTheme.BLOSSOM, 0f) }
        step("Lavender field: lavender, an allium, a periwinkle, a rock rose and a golden marguerite.") { look(GardenTheme.LAVENDER, 1f) }
        step("Used up, the lavender still stands and is still violet. The allium is a seed head.") { look(GardenTheme.LAVENDER, 0f) }
        step("Sunflower: a sunflower, a coneflower, a marigold, a sage and a blanket flower.") { look(GardenTheme.SUNFLOWER, 1f) }
        step("Used up, the sunflower hangs its head, and its petals are a dried gold.") { look(GardenTheme.SUNFLOWER, 0f) }
        step("Lily pond: a lotus, a blue water lily, a kingcup, a loosestrife and a violet water lily. They stand in water.") { look(GardenTheme.POND, 1f) }
        step("Used up, a lotus or a water lily is a seed head, and its petals float.") { look(GardenTheme.POND, 0f) }
        step("Mushroom forest: a fly agaric, a chanterelle, a penny bun, amethyst deceivers and a parasol.") { look(GardenTheme.MUSHROOM, 1f) }
        step("A mushroom ages. With a tenth of the time left, its cap is flatter.") { look(GardenTheme.MUSHROOM, 0.1f) }
        step("Used up, its cap is flat and faded, and its stalk leans. Asleep, it is not yet open.") { look(GardenTheme.MUSHROOM, 0f) }
        step("Asleep, each mushroom is small and closed.") { look(GardenTheme.MUSHROOM, 1f, asleep = true) }
        step("Lily pond by night, in bloom: each petal stands out from the dark card.") { look(GardenTheme.POND, 1f, DarkMode.DARK) }
        step("Sunflower by night, used up.") { look(GardenTheme.SUNFLOWER, 0f, DarkMode.DARK) }
    }
}
