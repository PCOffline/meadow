package dev.pcoffline.meadow.ui.flora

import androidx.compose.ui.graphics.Color
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.PLANTS_PER_THEME
import dev.pcoffline.meadow.usage.IconColour
import dev.pcoffline.meadow.ui.components.flora.Form
import dev.pcoffline.meadow.ui.components.flora.LITTLE_LEFT
import dev.pcoffline.meadow.ui.components.flora.Look
import dev.pcoffline.meadow.ui.components.flora.PLENTY_LEFT
import dev.pcoffline.meadow.ui.components.flora.Piece
import dev.pcoffline.meadow.ui.components.flora.Plant
import dev.pcoffline.meadow.ui.components.flora.Sketch
import dev.pcoffline.meadow.ui.components.flora.Tone
import dev.pcoffline.meadow.ui.components.flora.defaultPlace
import dev.pcoffline.meadow.ui.components.flora.namedPlace
import dev.pcoffline.meadow.ui.components.flora.plantFor
import dev.pcoffline.meadow.ui.components.flora.plantsOf
import dev.pcoffline.meadow.ui.components.flora.sketch
import dev.pcoffline.meadow.ui.components.flora.vigour
import dev.pcoffline.meadow.ui.components.looks
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.contrast
import dev.pcoffline.meadow.ui.theme.gardenColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/** The plants that each theme grows: their shapes, their colours and what they show of the time left. */
class FloraTest {
    private val everyLook = listOf(Look.Open(1f), Look.Open(0.5f), Look.Open(0f), Look.Spent, Look.Asleep)
    private val everyPlant = GardenTheme.entries.flatMap { theme -> plantsOf(theme).map { theme to it } }

    private fun assertNone(problems: List<String>) = assertTrue(problems.joinToString("\n"), problems.isEmpty())

    // ---------- time left ----------

    @Test
    fun `a flower is in full bloom while half or more of its time is left`() {
        assertEquals(1f, vigour(1f))
        assertEquals(1f, vigour(PLENTY_LEFT))
        assertTrue(vigour(PLENTY_LEFT - 0.05f) < 1f)
    }

    @Test
    fun `it is at its least lively with an eighth left, and not before`() {
        assertEquals(0f, vigour(LITTLE_LEFT))
        assertEquals(0f, vigour(0f))
        assertTrue(vigour(LITTLE_LEFT + 0.05f) > 0f)
    }

    @Test
    fun `less time left never looks livelier`() {
        (0..100).map { vigour(it / 100f) }.zipWithNext().forEach { (less, more) -> assertTrue(less <= more) }
    }

    @Test
    fun `at rest a plant has one look`() {
        assertEquals(listOf(Look.Open(0.4f) to 1f), looks(0.4f, spent = 0f, sleep = 0f))
        assertEquals(listOf(Look.Spent to 1f), looks(0f, spent = 1f, sleep = 0f))
    }

    @Test
    fun `asleep, a used-up plant is a bud, and never looks used up`() {
        assertEquals(listOf(Look.Asleep to 1f), looks(0f, spent = 1f, sleep = 1f))
    }

    @Test
    fun `a plant fades from one look to the next`() {
        val shares = looks(0f, spent = 0.25f, sleep = 0f)
        assertEquals(listOf<Look>(Look.Open(0f), Look.Spent), shares.map { it.first })
        assertEquals(1f, shares.sumOf { it.second.toDouble() }.toFloat(), 0.001f)
    }

    // ---------- the plants ----------

    @Test
    fun `each theme grows five plants`() = GardenTheme.entries.forEach {
        assertEquals(5, plantsOf(it).size)
        assertEquals(PLANTS_PER_THEME, plantsOf(it).size)
    }

    @Test
    fun `only the mushroom forest grows mushrooms`() = everyPlant.forEach { (theme, plant) ->
        assertEquals(plant.name, theme == GardenTheme.MUSHROOM, plant.form == Form.MUSHROOM)
    }

    @Test
    fun `an app keeps its place in each theme's five`() {
        for (app in listOf("com.example.notes", "com.example.photos", "org.example.music", "x")) {
            val places = GardenTheme.entries.map { plantsOf(it).indexOf(plantFor(app, it)) }
            assertEquals(app, 1, places.distinct().size)
        }
    }

    @Test
    fun `a chosen plant keeps its place in each theme's five`() {
        for (place in 0 until PLANTS_PER_THEME) {
            GardenTheme.entries.forEach { assertEquals(plantsOf(it)[place], plantFor("com.example.notes", it, place)) }
            val limit = AppLimit("com.example.notes", "Notes", 30, 0, plant = place)
            assertEquals(plantsOf(GardenTheme.POND)[place], plantFor(limit, GardenTheme.POND))
        }
    }

    @Test
    fun `with no plant chosen, or one out of range, an app's name gives its plant`() {
        val app = "com.example.notes"
        val named = plantsOf(GardenTheme.MEADOW)[namedPlace(app)]
        assertEquals(named, plantFor(app, GardenTheme.MEADOW))
        assertEquals(named, plantFor(AppLimit(app, "Notes", 30, 0), GardenTheme.MEADOW))
        assertEquals(named, plantFor(app, GardenTheme.MEADOW, PLANTS_PER_THEME))
        assertEquals(named, plantFor(app, GardenTheme.MEADOW, -1))
    }

    @Test
    fun `with no plant chosen, the plant is the one nearest in colour to the app's icon`() {
        val red = IconColour.of(IntArray(10) { 0xFFFF0000.toInt() })
        val blue = IconColour.of(IntArray(10) { 0xFF1877F2.toInt() })
        val yellow = IconColour.of(IntArray(10) { 0xFFFFD000.toInt() })
        val app = "com.example.notes"
        assertEquals("Corn poppy", plantFor(app, GardenTheme.MEADOW, icon = red).name)
        assertEquals("Cornflower", plantFor(app, GardenTheme.MEADOW, icon = blue).name)
        assertEquals("Buttercup", plantFor(app, GardenTheme.MEADOW, icon = yellow).name)
        assertEquals("Blue water lily", plantFor(app, GardenTheme.POND, icon = blue).name)
        assertEquals("Sunflower", plantFor(app, GardenTheme.SUNFLOWER, icon = yellow).name)
        assertEquals("Fly agaric", plantFor(app, GardenTheme.MUSHROOM, icon = red).name)
    }

    @Test
    fun `a chosen plant wins over the icon's colour`() {
        val red = IconColour.of(IntArray(10) { 0xFFFF0000.toInt() })
        val cornflower = plantsOf(GardenTheme.MEADOW).indexOfFirst { it.name == "Cornflower" }
        assertEquals("Cornflower", plantFor("com.example.notes", GardenTheme.MEADOW, cornflower, red).name)
    }

    @Test
    fun `a grey icon, or none, gives the plant of the app's name`() {
        val app = "com.example.notes"
        val named = plantsOf(GardenTheme.MEADOW)[namedPlace(app)]
        assertEquals(named, plantFor(app, GardenTheme.MEADOW, icon = IconColour.Grey))
        assertEquals(named, plantFor(app, GardenTheme.MEADOW, icon = null))
        assertEquals(namedPlace(app), defaultPlace(app, GardenTheme.MEADOW, IconColour.Grey))
    }

    @Test
    fun `apps are spread over a theme's five plants`() {
        val plants = (0 until 50).map { plantFor("com.example.app$it", GardenTheme.MEADOW) }.distinct()
        assertEquals(5, plants.size)
    }

    // ---------- shapes ----------

    @Test
    fun `each plant fits its box in each look, by day and by night`() = assertNone(
        everyPlant.flatMap { (theme, plant) ->
            everyLook.flatMap { look ->
                listOf(false, true).mapNotNull { dark ->
                    val s = sketch(theme, plant, look, dark)
                    // A piece that is cut to an outline reaches no further than that outline.
                    val all = (s.ground + s.body).flatMap { (it.clip ?: it.points).toList().chunked(2) }
                    val out = all.filter { (x, y) -> x < 0f || x > Sketch.WIDTH || y < 0f || y > Sketch.HEIGHT }
                    if (out.isEmpty()) null else "$theme ${plant.name} $look dark=$dark: ${out.size} points outside, such as ${out.first()}"
                }
            }
        },
    )

    @Test
    fun `each look of each plant draws something that stands on the ground`() = everyPlant.forEach { (theme, plant) ->
        everyLook.forEach { look ->
            val s = sketch(theme, plant, look)
            assertTrue("$theme ${plant.name} $look", s.body.size >= 3 && s.ground.isNotEmpty())
            val lowest = s.body.maxOf { piece -> piece.points.filterIndexed { i, _ -> i % 2 == 1 }.max() }
            assertTrue("$theme ${plant.name} $look stands at $lowest", abs(lowest - Sketch.BASE.y) < 4f)
        }
    }

    /** The cells of the box, two units square, that [pieces] cover. */
    private fun cells(pieces: List<Piece>): Set<Int> {
        val covered = HashSet<Int>()
        for (piece in pieces) {
            val p = piece.points
            val n = p.size / 2
            val xs = (0 until n).map { p[2 * it] }
            val ys = (0 until n).map { p[2 * it + 1] }
            for (cx in (xs.min() / 2f).toInt()..(xs.max() / 2f).toInt()) for (cy in (ys.min() / 2f).toInt()..(ys.max() / 2f).toInt()) {
                val x = cx * 2f + 1f
                val y = cy * 2f + 1f
                var inside = false
                var j = n - 1
                for (i in 0 until n) {
                    if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
                    j = i
                }
                if (inside) covered += cx * 1000 + cy
            }
        }
        return covered
    }

    /** How much two plants' outlines share: 1 is the same outline, 0 is none of it. */
    private fun overlap(a: Set<Int>, b: Set<Int>) = a.intersect(b).size / a.union(b).size.toFloat()

    @Test
    fun `two plants of a theme are never alike in both outline and colour`() = assertNone(
        GardenTheme.entries.flatMap { theme ->
            val plants = plantsOf(theme)
            val outlines = plants.map { cells(sketch(theme, it, Look.Open(1f)).body) }
            plants.indices.flatMap { i ->
                (i + 1 until plants.size).mapNotNull { j ->
                    val shared = overlap(outlines[i], outlines[j])
                    val apart = abs(plants[i].petal.l - plants[j].petal.l)
                    val hues = hueGap(plants[i].petal.h, plants[j].petal.h)
                    // A round head on a stem is one outline, whatever its petals. Such plants differ in colour.
                    if (shared < SAME_OUTLINE || apart >= LIGHTNESS_APART || hues >= HUES_APART) null
                    else "$theme ${plants[i].name} and ${plants[j].name}: outlines share $shared, lightness $apart apart, hues $hues apart"
                }
            }
        },
    )

    @Test
    fun `each theme grows plants of two or more outlines`() = GardenTheme.entries.forEach { theme ->
        val outlines = plantsOf(theme).map { cells(sketch(theme, it, Look.Open(1f)).body) }
        // The plants that share no outline with one before them.
        val kinds = outlines.indices.count { i -> (0 until i).none { j -> overlap(outlines[i], outlines[j]) >= SAME_OUTLINE } }
        // Sunflower's field is of one family: four heads on a stem, and one spike.
        assertTrue("$theme has $kinds outlines", kinds >= 2)
    }

    @Test
    fun `a used-up plant doesn't look as it does with time left`() = everyPlant.forEach { (theme, plant) ->
        val low = cells(sketch(theme, plant, Look.Open(0f)).body)
        val spent = cells(sketch(theme, plant, Look.Spent).body)
        assertTrue("$theme ${plant.name}: ${overlap(low, spent)}", overlap(low, spent) < 0.8f)
    }

    // ---------- colours ----------

    private fun lch(c: Color): Triple<Float, Float, Float> {
        fun lin(v: Float) = if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        val (r, g, b) = listOf(c.red, c.green, c.blue).map(::lin)
        val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
        val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
        val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
        val a = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
        val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
        return Triple(0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s, hypot(a, bb), Math.toDegrees(atan2(bb, a).toDouble()).toFloat().mod(360f))
    }

    private fun hueGap(a: Float, b: Float) = abs((a - b + 540f).mod(360f) - 180f)

    @Test
    fun `a tone is the colour it names`() {
        fun hex(tone: Tone) = tone.color.run { "%02x%02x%02x".format((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()) }
        assertEquals("e54533", hex(Tone(0.62f, 0.20f, 30f)))
        assertEquals("709ae9", hex(Tone(0.69f, 0.125f, 262f)))
        assertEquals("636363", hex(Tone(0.5f, 0f, 0f)))
        // And back: the colour has the tone's lightness, chroma and hue.
        val (l, c, h) = lch(Tone(0.69f, 0.125f, 262f).color)
        assertEquals(0.69f, l, 0.005f)
        assertEquals(0.125f, c, 0.005f)
        assertEquals(262f, h, 1.5f)
    }

    @Test
    fun `each tone of each plant is drawn in its own lightness and hue`() = assertNone(
        everyPlant.flatMap { (theme, plant) ->
            val tones = listOfNotNull(plant.petal, plant.centre, plant.tip, plant.calyx, plant.warts, plant.stalk)
            tones.flatMap { tone -> listOf(tone, tone.shade(), tone.light(), tone.dried(), tone.night(), tone.night().shade(), tone.night().dried()) }.mapNotNull { tone ->
                // A tone that sRGB can't show loses chroma only: its lightness and its hue stay.
                val (l, c, h) = lch(tone.color)
                // A pale tone's hue can't be read back to the degree from a colour of 8 bits a channel.
                val off = abs(l - tone.l) > 0.006f || c > tone.c + 0.004f || c < tone.c * 0.6f || (tone.c > 0.05f && hueGap(h, tone.h) > 1.5f)
                if (off) "$theme ${plant.name}: $tone is drawn as l=$l c=$c h=$h" else null
            }
        }.distinct(),
    )

    @Test
    fun `a dried petal keeps its hue and more than half of its chroma`() = everyPlant.forEach { (_, plant) ->
        val dried = plant.petal.dried()
        // A red, orange or yellow petal turns a little towards straw. Each other one keeps its hue exactly.
        assertTrue("${plant.name}: hue ${plant.petal.h} dries to ${dried.h}", hueGap(plant.petal.h, dried.h) <= 8f)
        assertTrue("${plant.name}: chroma ${plant.petal.c} dries to ${dried.c}", dried.c >= plant.petal.c * 0.55f && dried.c < plant.petal.c)
        assertTrue(dried.l < plant.petal.l)
    }

    @Test
    fun `used up, a plant still shows its own colour, dried`() = assertNone(
        everyPlant.mapNotNull { (theme, plant) ->
            val s = sketch(theme, plant, Look.Spent)
            val own = (s.body + s.ground).count { piece ->
                val (_, c, h) = lch(piece.color)
                c >= plant.petal.c * 0.5f && c < plant.petal.c && hueGap(h, plant.petal.h) <= 12f
            }
            if (own > 0) null else "$theme ${plant.name} has no dried petal colour when used up"
        },
    )

    @Test
    fun `used up, two themes' own plants are different colours`() {
        val dried = GardenTheme.entries.map { plantsOf(it).first().petal.dried() }
        for (i in dried.indices) for (j in i + 1 until dried.size) {
            assertTrue("${GardenTheme.entries[i]} and ${GardenTheme.entries[j]}", hueGap(dried[i].h, dried[j].h) >= 25f || abs(dried[i].l - dried[j].l) >= 0.08f)
        }
    }

    @Test
    fun `a flower's centre differs from its petals in lightness`() = assertNone(
        everyPlant.filter { (_, plant) -> plant.form in listOf(Form.RAY, Form.ROSETTE, Form.CUP, Form.LOTUS) }.mapNotNull { (_, plant) ->
            val apart = abs(plant.centre.l - plant.petal.l)
            if (apart >= CENTRE_APART) null else "${plant.name}: centre is $apart from its petals"
        },
    )

    @Test
    fun `each petal stands out from its card, by day and by night`() = assertNone(
        everyPlant.flatMap { (theme, plant) ->
            listOf(false, true).mapNotNull { dark ->
                val petal = (if (dark) plant.petal.night() else plant.petal).color
                val ratio = contrast(petal, gardenColors(theme, dark).cardTint)
                if (ratio >= PETAL_ON_CARD) null else "$theme ${plant.name} dark=$dark: $ratio"
            }
        },
    )

    @Test
    fun `the small flowers, the falling petals and the petal shower are in the colours of the theme's plants`() {
        for (theme in GardenTheme.entries) for (dark in listOf(false, true)) {
            val garden = gardenColors(theme, dark)
            val plants = plantsOf(theme)
            assertEquals("$theme dark=$dark", plants.map { (if (dark) it.petal.night() else it.petal).color }, garden.petals)
            assertEquals(plants.size, garden.hearts.size)
        }
    }

    @Test
    fun `a small flower's middle differs from its petals, and a mushroom's is cream, as its warts`() {
        for (theme in GardenTheme.entries) {
            val garden = gardenColors(theme, dark = false)
            garden.petals.zip(garden.hearts).forEach { (petal, heart) ->
                assertTrue("$theme: $petal and $heart", abs(lch(petal).first - lch(heart).first) >= 0.08f || hueGap(lch(petal).third, lch(heart).third) >= 60f)
            }
        }
        val agaric = plantsOf(GardenTheme.MUSHROOM).first()
        assertEquals(agaric.warts!!.color, gardenColors(GardenTheme.MUSHROOM, dark = false).hearts.first())
    }

    @Test
    fun `the chart's highlight is the theme's own petal, made to stand out`() {
        for (theme in GardenTheme.entries) {
            val garden = gardenColors(theme, dark = false)
            val own = lch(plantsOf(theme).first().petal.color)
            val high = lch(garden.growthHigh)
            // It is that petal, darkened only as far as it must be to show on each surface.
            assertTrue("$theme: hue ${own.third} is drawn as ${high.third}", hueGap(own.third, high.third) <= 12f)
            assertTrue(high.first <= own.first + 0.01f)
        }
    }

    @Test
    fun `shade is darker and light is lighter`() = everyPlant.forEach { (_, plant: Plant) ->
        assertTrue(plant.petal.shade().l < plant.petal.l)
        assertTrue(plant.petal.light().l > plant.petal.l)
    }

    private companion object {
        /** Two outlines that share this much are one outline in different sizes. */
        const val SAME_OUTLINE = 0.7f

        /** Two petals this far apart in lightness (of 1), or this far apart in hue (in degrees), are different colours. */
        const val LIGHTNESS_APART = 0.08f
        const val HUES_APART = 30f

        /** A centre this far from its petals in lightness (of 1) shows at the size of a garden card. */
        const val CENTRE_APART = 0.12f

        /** The least contrast between a petal and the card that it is on. */
        const val PETAL_ON_CARD = 1.45f
    }
}
