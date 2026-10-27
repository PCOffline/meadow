package dev.pcoffline.meadow.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** The outline of a butterfly's wings. */
enum class WingShape {
    /** Broad, rounded wings, like a blue's. */
    ROUND,

    /** Long forewings with a sharp tip, like a monarch's. */
    POINTED,

    /** Ragged, wavy edges, like a comma's. */
    SCALLOPED,

    /** Swept-back forewings and a long tail on each hindwing. */
    SWALLOWTAIL,
}

/** Markings painted over the wing colour. */
enum class WingMark {
    /** A ringed eye on each wing. */
    EYESPOTS,

    /** A pale band across both wings. */
    BAND,

    /** Bold dark veins. */
    VEINS,

    /** Small dark spots. */
    DOTS,

    /** A dark forewing tip flecked with white. */
    TIPS,

    /** A sheen that brightens as the wings open to the light. */
    SHEEN,

    /** A row of pale dots along the dark border. */
    FRINGE,
}

/**
 * One kind of butterfly. Wings shade from [inner] at the body through [wing] to [outer] at the
 * edge, with a [edge] border; marks are drawn in [accent].
 */
@Immutable
data class ButterflyLook(
    val shape: WingShape,
    val marks: Set<WingMark>,
    val inner: Color,
    val wing: Color,
    val outer: Color,
    val edge: Color,
    val accent: Color,
    val body: Color = Color(0xFF3B322B),
)

/** The two butterflies of each theme's garden, chosen to sit with its flowers. */
fun butterfliesFor(theme: GardenTheme): List<ButterflyLook> = when (theme) {
    GardenTheme.MEADOW -> listOf(
        // A common blue, and a painted lady.
        ButterflyLook(WingShape.ROUND, setOf(WingMark.FRINGE, WingMark.DOTS), Color(0xFF4C74C0), Color(0xFF7FA6E6), Color(0xFFB8D2F5), Color(0xFF3A4566), Color(0xFFF0A35A)),
        ButterflyLook(WingShape.POINTED, setOf(WingMark.TIPS, WingMark.DOTS), Color(0xFFC0643A), Color(0xFFF0A066), Color(0xFFF7C89A), Color(0xFF3E2E28), Color(0xFF4A3329)),
    )
    GardenTheme.BLOSSOM -> listOf(
        // A rose with an eye on each wing, and a pale swallowtail.
        ButterflyLook(WingShape.ROUND, setOf(WingMark.EYESPOTS), Color(0xFFD9708F), Color(0xFFF2A2BC), Color(0xFFFAD3E0), Color(0xFF8E4460), Color(0xFF7A5BA8)),
        ButterflyLook(WingShape.SWALLOWTAIL, setOf(WingMark.BAND, WingMark.FRINGE), Color(0xFFE8C9A0), Color(0xFFFBEBD0), Color(0xFFFFF7EA), Color(0xFF3D3530), Color(0xFF3D3530)),
    )
    GardenTheme.LAVENDER -> listOf(
        // A purple emperor, and a peacock.
        ButterflyLook(WingShape.ROUND, setOf(WingMark.BAND, WingMark.SHEEN), Color(0xFF5B45A8), Color(0xFF8C74D6), Color(0xFFBBA8F2), Color(0xFF2F2650), Color(0xFFF4EEFF)),
        ButterflyLook(WingShape.SCALLOPED, setOf(WingMark.EYESPOTS), Color(0xFF7A3550), Color(0xFFA6567A), Color(0xFFC98AA6), Color(0xFF3B2232), Color(0xFF6E8FD8)),
    )
    GardenTheme.SUNFLOWER -> listOf(
        // A monarch, and a sulphur.
        ButterflyLook(WingShape.POINTED, setOf(WingMark.VEINS, WingMark.FRINGE), Color(0xFFD9701E), Color(0xFFF29A32), Color(0xFFF7BE5E), Color(0xFF2B211C), Color(0xFF2B211C)),
        ButterflyLook(WingShape.ROUND, setOf(WingMark.DOTS), Color(0xFFE6BE2E), Color(0xFFF6D860), Color(0xFFFBEA9E), Color(0xFFB08A2A), Color(0xFFE07A2C)),
    )
    GardenTheme.POND -> listOf(
        // A morpho, and a teal swallowtail.
        ButterflyLook(WingShape.ROUND, setOf(WingMark.SHEEN), Color(0xFF1E5FA8), Color(0xFF3A9AD8), Color(0xFF8AD4F2), Color(0xFF1B2638), Color(0xFFE9F7FF)),
        ButterflyLook(WingShape.SWALLOWTAIL, setOf(WingMark.BAND, WingMark.FRINGE), Color(0xFF2E6E70), Color(0xFF4B9E98), Color(0xFF8CCFC2), Color(0xFF1E2E30), Color(0xFFD8F2E8)),
    )
    GardenTheme.MUSHROOM -> listOf(
        // A comma, and a ringlet.
        ButterflyLook(WingShape.SCALLOPED, setOf(WingMark.DOTS, WingMark.FRINGE), Color(0xFFB0532A), Color(0xFFD9813F), Color(0xFFECAE6E), Color(0xFF4A2A1C), Color(0xFF4A2A1C)),
        ButterflyLook(WingShape.ROUND, setOf(WingMark.EYESPOTS), Color(0xFF6E4E3A), Color(0xFF9A7458), Color(0xFFC4A07E), Color(0xFF3A2A20), Color(0xFFE9B949)),
    )
}
