package dev.pcoffline.meadow.ui.components.flora

import androidx.compose.runtime.Immutable
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.PLANTS_PER_THEME
import dev.pcoffline.meadow.usage.IconColour
import dev.pcoffline.meadow.ui.theme.GardenTheme
import kotlin.math.abs
import kotlin.math.sin

/** How a plant is built. Each form has its own outline, so a plant is told by its shape as well as its colour. */
enum class Form { RAY, ROSETTE, CUP, POMPOM, LOTUS, SPIKE, GLOBE, RACEME, MUSHROOM }

/** The outline of one petal. */
enum class PetalShape { ROUND, POINT, NARROW, NOTCH, TOOTH, WIDE, FAN }

enum class LeafShape { LANCE, OVAL, HEART, FEATHER, NONE }

/** What stays on the stem when the petals are gone. */
enum class Pod { POPPY, LOTUS }

enum class Cap { DOME, FUNNEL, BUN, TROOP, BELL, PARASOL }

/**
 * Where a flower's head is and how it is turned. [tilt] is where its face points on the page, in
 * degrees clockwise from straight up. [face] is how much it points at the viewer: 1 is face-on.
 * [cup] lifts the petal tips: 0 is flat open, 1 is closed. [plen] scales the petals' length and
 * [dry] is how dried the colours are. [x] and [y] place the head, and [lean] bends the stem.
 */
@Immutable
data class Pose(val tilt: Float, val face: Float, val cup: Float, val plen: Float, val dry: Float, val y: Float, val x: Float, val lean: Float)

/**
 * One plant of a theme: a flower, or a mushroom in the mushroom forest. [petal] is the colour of
 * its petals or its cap, and [centre] the colour of its middle. The other values shape it, in the
 * units of [Sketch]: [n] petals of [kind], each [len] long and [half] wide to each side, round a
 * middle [disc] in radius.
 */
@Immutable
data class Plant(
    val name: String,
    val form: Form,
    val petal: Tone,
    val centre: Tone = Gold,
    val n: Int = 0,
    val kind: PetalShape = PetalShape.ROUND,
    val len: Float = 0f,
    val half: Float = 0f,
    val disc: Float = 0f,
    val size: Float = 1f,
    /** How much darker each second petal is, so that wide petals read as separate. */
    val alt: Float = 0f,
    val layers: Int = 1,
    val seeds: Int = 0,
    val stamens: Int = 0,
    val dome: Float = 0.3f,
    /** A second colour at the petal tips. */
    val tip: Tone? = null,
    /** The cup under the petals, when it isn't leaf green. */
    val calyx: Tone? = null,
    val leaf: LeafShape = LeafShape.LANCE,
    /** How many petals lie on the ground when the time is used up. */
    val shed: Int = 0,
    /** The petals that stay on when the time is used up: it has lost the others. All stay when this is null. */
    val keep: Set<Int>? = null,
    val pod: Pod? = null,
    val low: Pose? = null,
    val spent: Pose? = null,
    /** A lotus petal's width as a share of its length. */
    val slim: Float = 0.27f,
    val stems: Int = 3,
    val whorls: Int = 7,
    val cover: Float = 0.46f,
    val floret: Float = 3.3f,
    val cap: Cap = Cap.DOME,
    val warts: Tone? = null,
    val stalk: Tone? = null,
    val umbo: Boolean = false,
    /** The stems, when they aren't the colour of the theme's own. */
    val green: Tone? = null,
) {
    /** This plant by night. */
    internal fun night() = copy(petal = petal.night(), centre = centre.night(), tip = tip?.night(), warts = warts?.night(), stalk = stalk?.night())
}

/** What a theme's plants grow from: the colours of its ground, stems and leaves. */
internal data class Soil(val ground: Tone, val stem: Tone, val leaf: Tone) {
    fun night() = Soil(Tone(ground.l * 0.52f, ground.c, ground.h), Tone(stem.l - 0.05f, stem.c * 0.9f, stem.h), Tone(leaf.l - 0.07f, leaf.c * 0.92f, leaf.h))
}

internal val Gold = Tone(0.84f, 0.16f, 88f)

// A centre differs from its petals in lightness, so that it shows at a small size: a light one on
// petals of middle lightness, and a dark one on light petals.
private val Pale = Tone(0.94f, 0.115f, 102f)
private val Rust = Tone(0.62f, 0.15f, 55f)
private val Amber = Tone(0.72f, 0.16f, 62f)
private val Plum = Tone(0.36f, 0.07f, 340f)
private val Seed = Tone(0.40f, 0.06f, 55f)
private val Cream = Tone(0.95f, 0.03f, 90f)

internal val FullPose = Pose(tilt = -14f, face = 0.86f, cup = 0.04f, plen = 1f, dry = 0f, y = 40f, x = 46f, lean = -6f)
internal val LowPose = Pose(tilt = 40f, face = 0.72f, cup = 0.24f, plen = 0.97f, dry = 0.1f, y = 45f, x = 53f, lean = -9f)
internal val SpentPose = Pose(tilt = 98f, face = 0.7f, cup = 0.3f, plen = 0.8f, dry = 1f, y = 51f, x = 60f, lean = -13f)
internal val AsleepPose = Pose(tilt = 16f, face = 0.5f, cup = 1f, plen = 1f, dry = 0f, y = 47f, x = 49f, lean = -7f)

/** A blossom on a twig stays up when its petals fall. */
private val BareTwig = Pose(tilt = 22f, face = 0.74f, cup = 0.1f, plen = 1f, dry = 0.25f, y = 42f, x = 50f, lean = -7f)

internal val Soils = mapOf(
    GardenTheme.MEADOW to Soil(Tone(0.80f, 0.045f, 95f), Tone(0.60f, 0.10f, 138f), Tone(0.70f, 0.12f, 138f)),
    // Blossom grows on brown twigs.
    GardenTheme.BLOSSOM to Soil(Tone(0.80f, 0.04f, 40f), Tone(0.47f, 0.055f, 45f), Tone(0.72f, 0.105f, 128f)),
    // Lavender's leaves are silver-green.
    GardenTheme.LAVENDER to Soil(Tone(0.83f, 0.04f, 85f), Tone(0.64f, 0.055f, 150f), Tone(0.74f, 0.05f, 155f)),
    GardenTheme.SUNFLOWER to Soil(Tone(0.70f, 0.06f, 65f), Tone(0.60f, 0.11f, 132f), Tone(0.70f, 0.13f, 132f)),
    // The pond's plants stand in water.
    GardenTheme.POND to Soil(Tone(0.84f, 0.05f, 205f), Tone(0.60f, 0.085f, 165f), Tone(0.68f, 0.10f, 162f)),
    GardenTheme.MUSHROOM to Soil(Tone(0.74f, 0.05f, 70f), Tone(0.58f, 0.09f, 135f), Tone(0.66f, 0.11f, 132f)),
)

/**
 * Each theme's five plants, its own first. Two plants of a theme differ in outline or clearly in
 * colour. Three themes' own plants are pink, in three lightnesses: Meadow's cosmos is deep,
 * Cherry blossom's cherry is pale, and Lily pond's lotus is between them, on water.
 */
internal val Plants = mapOf(
    GardenTheme.MEADOW to listOf(
        // Meadow's own plant, and the app's icon: a cosmos in a deep pink, darker than the cherry's and the lotus's.
        Plant(
            "Cosmos", Form.RAY, Tone(0.66f, 0.16f, 350f), Pale, n = 8, kind = PetalShape.TOOTH, len = 17f, half = 6f, disc = 4.6f, alt = 0.45f,
            leaf = LeafShape.FEATHER, shed = 3, keep = setOf(0, 1, 2, 4, 5, 7),
        ),
        Plant(
            "Corn poppy", Form.CUP, Tone(0.70f, 0.185f, 30f), Plum, n = 4, kind = PetalShape.WIDE, len = 18.5f, half = 12.2f, disc = 4.4f,
            stamens = 8, pod = Pod.POPPY, shed = 4, leaf = LeafShape.FEATHER,
            // A poppy's capsule stands up on its stem.
            spent = Pose(tilt = 14f, face = 0.6f, cup = 0f, plen = 1f, dry = 1f, y = 44f, x = 50f, lean = -8f),
        ),
        Plant(
            "Cornflower", Form.RAY, Tone(0.69f, 0.125f, 262f), Tone(0.45f, 0.13f, 295f), n = 9, kind = PetalShape.FAN, len = 17.5f,
            half = 5.6f, disc = 5.4f, seeds = 9, alt = 0.5f, shed = 3, keep = setOf(0, 2, 3, 5, 6, 8),
        ),
        Plant(
            "Buttercup", Form.ROSETTE, Tone(0.85f, 0.17f, 92f), Amber, n = 5, kind = PetalShape.ROUND, len = 15f, half = 7.6f, disc = 4.4f,
            size = 0.95f, alt = 0.4f, stamens = 9, shed = 3, keep = setOf(0, 1, 3, 4),
        ),
        Plant(
            "Aster", Form.RAY, Tone(0.75f, 0.105f, 300f), Pale, n = 13, kind = PetalShape.NARROW, len = 17f, half = 3.3f, disc = 6.4f,
            layers = 2, seeds = 14, dome = 0.5f, shed = 2, keep = setOf(0, 1, 2, 4, 5, 7, 8, 9, 11, 12),
        ),
    ),
    GardenTheme.BLOSSOM to listOf(
        Plant(
            "Cherry", Form.ROSETTE, Tone(0.855f, 0.08f, 358f), Tone(0.60f, 0.17f, 5f), n = 5, kind = PetalShape.NOTCH, len = 17.5f, half = 7.9f,
            disc = 3f, alt = 0.3f, calyx = Tone(0.56f, 0.12f, 12f), stamens = 7, shed = 5, keep = setOf(0, 3),
            low = LowPose.copy(tilt = 30f, face = 0.72f, y = 42f, x = 50f), spent = BareTwig,
        ),
        // Under the trees: spikes of blue bells on green stems.
        Plant(
            "Grape hyacinth", Form.SPIKE, Tone(0.60f, 0.14f, 275f), stems = 3, whorls = 8, cover = 0.5f, floret = 3.5f, size = 0.8f,
            green = Tone(0.62f, 0.10f, 135f),
        ),
        Plant(
            "Camellia", Form.CUP, Tone(0.65f, 0.175f, 22f), Pale, n = 5, kind = PetalShape.ROUND, len = 16.5f, half = 9.6f, disc = 4.2f,
            stamens = 7, shed = 4, leaf = LeafShape.OVAL,
        ),
        Plant("Wisteria", Form.RACEME, Tone(0.74f, 0.105f, 300f)),
        Plant(
            "Kerria", Form.ROSETTE, Tone(0.85f, 0.165f, 90f), Amber, n = 5, kind = PetalShape.POINT, len = 16.5f, half = 6.6f, disc = 3.4f,
            alt = 0.4f, stamens = 7, shed = 4, keep = setOf(0, 1, 3, 4),
        ),
    ),
    GardenTheme.LAVENDER to listOf(
        Plant("Lavender", Form.SPIKE, Tone(0.66f, 0.135f, 300f)),
        Plant("Allium", Form.GLOBE, Tone(0.77f, 0.115f, 328f)),
        Plant(
            "Periwinkle", Form.ROSETTE, Tone(0.73f, 0.11f, 276f), Tone(0.97f, 0.02f, 95f), n = 5, kind = PetalShape.TOOTH, len = 15.5f,
            half = 7.6f, disc = 2.6f, alt = 0.4f, shed = 3, keep = setOf(0, 1, 3, 4), leaf = LeafShape.OVAL,
        ),
        Plant(
            "Rock rose", Form.ROSETTE, Tone(0.81f, 0.105f, 345f), Rust, n = 5, kind = PetalShape.WIDE, len = 15.5f, half = 8.6f, disc = 3.8f,
            alt = 0.35f, stamens = 9, shed = 3, keep = setOf(0, 1, 3, 4), leaf = LeafShape.OVAL,
        ),
        Plant(
            "Golden marguerite", Form.RAY, Tone(0.85f, 0.16f, 92f), Amber, n = 9, kind = PetalShape.ROUND, len = 11.5f, half = 4.6f, disc = 6f,
            size = 1.05f, alt = 0.4f, seeds = 12, dome = 0.5f, leaf = LeafShape.FEATHER, shed = 2, keep = setOf(0, 1, 3, 4, 6, 7),
        ),
    ),
    GardenTheme.SUNFLOWER to listOf(
        Plant(
            "Sunflower", Form.RAY, Tone(0.86f, 0.17f, 90f), Seed, n = 13, kind = PetalShape.POINT, len = 15.5f, half = 4.3f, disc = 9.4f,
            layers = 2, size = 1.04f, seeds = 26, dome = 0.35f, leaf = LeafShape.HEART, shed = 3, keep = setOf(0, 1, 2, 4, 5, 6, 8, 9, 10, 12),
        ),
        Plant(
            "Coneflower", Form.RAY, Tone(0.72f, 0.13f, 345f), Tone(0.50f, 0.12f, 50f), n = 10, kind = PetalShape.NARROW, len = 18f,
            half = 4.4f, disc = 5.6f, alt = 0.4f, dome = 0.9f, shed = 3, keep = setOf(0, 1, 3, 4, 6, 7, 9),
        ),
        Plant(
            "Marigold", Form.POMPOM, Tone(0.74f, 0.175f, 55f), Rust, n = 12, kind = PetalShape.ROUND, len = 18f, half = 6.6f, disc = 3f,
            leaf = LeafShape.FEATHER, shed = 3,
        ),
        // The one cool colour in the field: spikes of blue sage.
        Plant("Sage", Form.SPIKE, Tone(0.62f, 0.15f, 268f), stems = 3, whorls = 9, cover = 0.56f, floret = 3.2f, size = 0.92f),
        Plant(
            "Blanket flower", Form.RAY, Tone(0.64f, 0.185f, 32f), Tone(0.42f, 0.10f, 25f), n = 11, kind = PetalShape.TOOTH, len = 16.5f,
            half = 4.9f, disc = 6.2f, tip = Tone(0.87f, 0.16f, 92f), dome = 0.6f, shed = 3, keep = setOf(0, 1, 3, 4, 5, 7, 8, 10),
        ),
    ),
    GardenTheme.POND to listOf(
        Plant("Lotus", Form.LOTUS, Tone(0.76f, 0.135f, 355f), Pale, len = 21f, pod = Pod.LOTUS, shed = 4, leaf = LeafShape.NONE),
        Plant("Blue water lily", Form.LOTUS, Tone(0.76f, 0.10f, 255f), Pale, len = 20f, slim = 0.2f, pod = Pod.LOTUS, shed = 4, leaf = LeafShape.NONE),
        Plant(
            "Kingcup", Form.ROSETTE, Tone(0.85f, 0.17f, 92f), Amber, n = 5, kind = PetalShape.ROUND, len = 15f, half = 8f, disc = 4.4f,
            alt = 0.4f, stamens = 9, shed = 3, keep = setOf(0, 1, 3, 4), leaf = LeafShape.HEART,
        ),
        Plant("Loosestrife", Form.SPIKE, Tone(0.66f, 0.16f, 335f), stems = 2, whorls = 10, cover = 0.62f, floret = 3.6f),
        Plant("Violet water lily", Form.LOTUS, Tone(0.62f, 0.135f, 300f), Pale, len = 19f, slim = 0.235f, pod = Pod.LOTUS, shed = 4, leaf = LeafShape.NONE),
    ),
    GardenTheme.MUSHROOM to listOf(
        // A mushroom has no middle. Where a small flower has one, a mushroom's colour is cream, as its warts or its stalk.
        Plant("Fly agaric", Form.MUSHROOM, Tone(0.62f, 0.20f, 30f), Cream, cap = Cap.DOME, warts = Cream),
        Plant("Chanterelle", Form.MUSHROOM, Tone(0.84f, 0.135f, 82f), Cream, cap = Cap.FUNNEL),
        Plant("Penny bun", Form.MUSHROOM, Tone(0.47f, 0.08f, 55f), Cream, cap = Cap.BUN),
        Plant("Amethyst deceiver", Form.MUSHROOM, Tone(0.66f, 0.115f, 305f), Cream, cap = Cap.TROOP, stalk = Tone(0.80f, 0.06f, 305f)),
        Plant("Parasol", Form.MUSHROOM, Tone(0.84f, 0.055f, 75f), Cream, cap = Cap.PARASOL, umbo = true),
    ),
)

/** The five plants that [theme] grows, its own first. */
fun plantsOf(theme: GardenTheme): List<Plant> = Plants.getValue(theme)

/** The place in each theme's five plants that an app's [packageName] gives it, when its icon has no colour. */
fun namedPlace(packageName: String): Int = (packageName.hashCode() and 0x7fffffff) % PLANTS_PER_THEME

/**
 * The place of the plant in [theme] whose petals are nearest to [icon] in colour. Hue counts most:
 * it is compared at one chroma for all, and lightness and chroma count half. With no colour (a
 * grey icon, or none), it is the place that the app's [packageName] gives.
 */
fun defaultPlace(packageName: String, theme: GardenTheme, icon: IconColour?): Int {
    if (icon == null || icon.grey) return namedPlace(packageName)
    return plantsOf(theme).indices.minBy { place ->
        val petal = plantsOf(theme)[place].petal
        val turn = Math.toRadians(((petal.h - icon.h + 540f) % 360f - 180f).toDouble())
        val hue = 2 * HUE_CHROMA * sin(abs(turn) / 2)
        val dl = HALF * (petal.l - icon.l)
        val dc = HALF * (petal.c - icon.c)
        hue * hue + dl * dl + dc * dc
    }
}

/** The chroma at which hues are compared: about that of the plants' petals. */
private const val HUE_CHROMA = 0.15

private const val HALF = 0.5

/**
 * The plant that an app keeps in [theme]. It is in the [chosen] place of the theme's five. With
 * no choice, it is the plant nearest in colour to the app's [icon].
 */
fun plantFor(packageName: String, theme: GardenTheme, chosen: Int? = null, icon: IconColour? = null): Plant =
    plantsOf(theme)[chosen?.takeIf { it in 0 until PLANTS_PER_THEME } ?: defaultPlace(packageName, theme, icon)]

/** The plant of [limit] in [theme], for an app whose icon is [icon]. */
fun plantFor(limit: AppLimit, theme: GardenTheme, icon: IconColour? = null): Plant = plantFor(limit.packageName, theme, limit.plant, icon)
