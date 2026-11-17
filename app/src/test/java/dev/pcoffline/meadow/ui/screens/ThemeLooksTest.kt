package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.runtime.CompositionLocalProvider
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.enforcement.AppBudget
import dev.pcoffline.meadow.ui.home.GardenState
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.showcase.DemoGarden
import dev.pcoffline.meadow.ui.components.LocalSkyHour
import dev.pcoffline.meadow.ui.home.GardenContent
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.MeadowHills
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.components.flora.Soils
import dev.pcoffline.meadow.ui.components.flora.Tone
import dev.pcoffline.meadow.ui.components.flora.plantsOf
import dev.pcoffline.meadow.ui.settings.AppearanceCard
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.gardenColorsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.math.abs

/** What each theme draws: mushrooms in the mushroom forest, and theme previews in their own colours. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeLooksTest : ScreenTest() {
    private fun PixelMap.count(colour: Color, left: Int = 0, top: Int = 0, right: Int = width, bottom: Int = height): Int {
        var n = 0
        for (x in left until right) for (y in top until bottom) {
            val c = this[x, y]
            if (abs(c.red - colour.red) < 0.012f && abs(c.green - colour.green) < 0.012f && abs(c.blue - colour.blue) < 0.012f) n++
        }
        return n
    }

    /** [theme]'s own plant in full bloom, as drawn. */
    private fun flower(theme: GardenTheme): PixelMap {
        show(theme) { BloomFlower(1f, plantsOf(theme).first(), Modifier.size(90.dp, 120.dp), sway = false, growIn = false) }
        return compose.onNode(isRoot()).captureToImage().toPixelMap()
    }

    /** A mushroom's pale stalk. No flower has this colour. */
    private val stalk = Tone(0.93f, 0.03f, 85f).color

    @Test
    fun `the mushroom forest grows mushrooms on pale stalks`() {
        val mushroom = flower(GardenTheme.MUSHROOM)
        assertTrue(mushroom.count(stalk) > 100)
        // A fly agaric's red cap.
        assertTrue(mushroom.count(plantsOf(GardenTheme.MUSHROOM).first().petal.color) > 300)
    }

    @Test
    fun `other gardens grow flowers, with no stalks`() {
        assertEquals(0, flower(GardenTheme.MEADOW).count(stalk))
    }

    @Test
    fun `a flower is in its fresh colour until its time is used up, and then in that colour dried`() {
        val sunflower = plantsOf(GardenTheme.SUNFLOWER).first().petal
        // With a little time left, it has dried a little. With none left, it is fully dried.
        val nearly = sunflower.mix(sunflower.dried(), 0.1f).color
        val dried = sunflower.dried().color
        var bloom by mutableStateOf(1f)
        var asleep by mutableStateOf(false)
        show(GardenTheme.SUNFLOWER) {
            BloomFlower(bloom, plantsOf(GardenTheme.SUNFLOWER).first(), Modifier.size(90.dp, 120.dp), sway = false, growIn = false, asleep = asleep)
        }
        fun drawn(): PixelMap {
            compose.waitForIdle()
            return compose.onNode(isRoot()).captureToImage().toPixelMap()
        }
        val full = drawn()
        assertTrue("fresh petals in bloom: ${full.count(sunflower.color)}", full.count(sunflower.color) > 300)
        assertEquals(0, full.count(dried))

        bloom = 0.02f
        val little = drawn()
        assertTrue("petals with little time left: ${little.count(nearly)}", little.count(nearly) > 300)
        assertEquals(0, little.count(dried))

        bloom = 0f
        val used = drawn()
        assertTrue("dried petals when used up: ${used.count(dried)}", used.count(dried) > 100)
        assertEquals(0, used.count(sunflower.color))

        // Asleep, a used-up flower is a bud in its fresh colour.
        asleep = true
        val bud = drawn()
        assertTrue("fresh petals in the bud: ${bud.count(sunflower.color)}", bud.count(sunflower.color) > 100)
        assertEquals(0, bud.count(dried))
    }

    @Test
    fun `the flowers on the hills are the theme's plants, each on a stem`() {
        show(GardenTheme.LAVENDER) { MeadowHills(Modifier.size(411.dp, 200.dp)) }
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        val stem = Soils.getValue(GardenTheme.LAVENDER).stem.color
        assertTrue("stems on the hills: ${shot.count(stem)}", shot.count(stem) > 10)
        // Lavender, the field's own plant, among them.
        assertTrue(shot.count(plantsOf(GardenTheme.LAVENDER).first().petal.color) > 10)
    }

    @Test
    fun `a section title's daisy is in the colours of the theme's own plant`() {
        var theme by mutableStateOf(GardenTheme.LAVENDER)
        show { MeadowTheme(theme, DarkMode.LIGHT) { SectionTitle("Your garden") } }
        fun drawn(colour: Color) = compose.onNode(isRoot()).captureToImage().toPixelMap().count(colour)
        val lavender = gardenColorsFor(GardenTheme.LAVENDER, dark = false)
        assertTrue("lavender petals: ${drawn(lavender.petals.first())}", drawn(lavender.petals.first()) > 60)
        assertTrue("its middle: ${drawn(lavender.hearts.first())}", drawn(lavender.hearts.first()) > 10)

        theme = GardenTheme.MUSHROOM
        compose.waitForIdle()
        val forest = gardenColorsFor(GardenTheme.MUSHROOM, dark = false)
        assertEquals(0, drawn(lavender.petals.first()))
        assertTrue("a fly agaric's red: ${drawn(forest.petals.first())}", drawn(forest.petals.first()) > 60)
    }

    @Test
    fun `each theme's preview shows that theme's own sun`() {
        AppGraph.store.setAppearance(GardenTheme.MEADOW.key, DarkMode.LIGHT.key)
        show { AppearanceCard() }
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        GardenTheme.entries.forEach { theme ->
            val card = compose.onNode(hasText(text(theme.label), substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val sun = gardenColorsFor(theme, dark = false).sun
            val inCard = shot.count(sun, card.left.toInt(), card.top.toInt(), card.right.toInt(), card.bottom.toInt())
            assertTrue("$theme: $inCard", inCard > 30)
        }
    }

    @Test
    fun `a new theme gives each garden card that theme's plant`() {
        var theme by mutableStateOf(GardenTheme.MEADOW)
        // Apps with no time used yet: each flower is in full bloom, in its exact petal colour.
        val fresh = GardenState(
            budgets = listOf("com.example.notes", "com.example.photos", "com.example.music", "com.example.news").map {
                AppBudget(AppLimit(it, it.substringAfterLast('.'), 60, 0), 0L, 0L, 0, 0)
            },
            loaded = true,
        )
        show { MeadowTheme(theme, DarkMode.LIGHT) { GardenContent(DemoGarden.robin, fresh) } }
        val sunflower = plantsOf(GardenTheme.SUNFLOWER).map { it.petal.color }
        // Meadow's buttercup is nearly a sunflower's yellow, so it isn't counted.
        val meadowOnly = plantsOf(GardenTheme.MEADOW).map { it.petal.color }.filter { m ->
            sunflower.none { abs(it.red - m.red) + abs(it.green - m.green) + abs(it.blue - m.blue) < 0.1f }
        }
        fun shown(colours: List<Color>) = compose.onNode(isRoot()).captureToImage().toPixelMap().let { shot -> colours.sumOf { shot.count(it) } }
        assertTrue("Meadow's petals on its garden", shown(meadowOnly) > 1000)

        theme = GardenTheme.SUNFLOWER
        compose.waitForIdle()

        // A few edge pixels can match by chance; a card still in Meadow's petal has hundreds.
        assertTrue("Meadow's petals left on Sunflower's garden: ${shown(meadowOnly)}", shown(meadowOnly) < 100)
        assertTrue("Sunflower's petals on its garden", shown(sunflower) > 1000)
    }

    @Test
    fun `the sun sits clear below your picture and Settings`() {
        show { CompositionLocalProvider(LocalSkyHour provides 14) { GardenContent(DemoGarden.robin, DemoGarden.garden) } }
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        val sun = gardenColorsFor(GardenTheme.MEADOW, dark = false).sun
        val row = listOf(R.string.action_settings, R.string.profile_open).map {
            compose.onNode(hasContentDescription(text(it))).fetchSemanticsNode().boundsInRoot
        }
        val rowBottom = row.maxOf { it.bottom }
        val sunTop = (0 until shot.height).first { y -> (0 until shot.width).any { x -> shot.count(sun, x, y, x + 1, y + 1) == 1 } }
        // The sun's rays reach 15dp past its disc; they end above the row's bottom edge, not on it.
        val clearance = (sunTop - rowBottom) / compose.density.density
        assertTrue("the sun's disc starts ${clearance}dp below the row", clearance >= 20f)
    }

    /** The middle of the drawn sun disc, in pixels, with the garden's sky pinned to [hour]. */
    private fun sunAt(hour: Int): Float {
        show { CompositionLocalProvider(LocalSkyHour provides hour) { GardenContent(DemoGarden.robin, DemoGarden.garden) } }
        val shot = compose.onNode(isRoot()).captureToImage().toPixelMap()
        val sun = gardenColorsFor(GardenTheme.MEADOW, dark = false).sun
        val xs = (0 until shot.width).filter { x -> shot.count(sun, x, 0, x + 1, shot.height / 2) > 0 }
        return (xs.min() + xs.max()) / 2f / shot.width
    }

    @Test
    fun `the sun is on the left in the morning`() {
        assertTrue(sunAt(8) < 0.4f)
    }

    @Test
    fun `the sun is on the right in the evening`() {
        assertTrue(sunAt(17) > 0.7f)
    }
}
