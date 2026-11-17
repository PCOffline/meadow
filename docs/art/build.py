"""Draws the README's animated SVG art in the Meadow theme, with its own fonts embedded.

Run from the repository root: python3 docs/art/build.py  (needs: pip install fonttools brotli)
Writes docs/images/banner-light.svg, banner-dark.svg and the step-*.svg illustrations.
The plants, sun, moon, clouds, stars and fireflies are drawn as the app draws them. The plants
come from flora.py, a port of the app's flora.
Animations are CSS, so they stop for people who ask for reduced motion.
"""
import base64
import colorsys
import io
import math
import random
from pathlib import Path

import flora
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "app/src/main/res/font"
OUT = ROOT / "docs/images"

INK, NIGHT_INK = "#3E3A32", "#F3EEE2"
SOFT_INK, NIGHT_SOFT = "#6F675A", "#C3BBAC"

# The skies of Ambient.kt: the afternoon by day, and the night.
DAY_SKY, NIGHT_SKY = ("#D9ECF2", "#F8F4E8"), ("#26304A", "#3A4658")
MOON_LIGHT = "#FFF6DC"
# The Meadow theme's primary colour (Theme.kt). The sun and the grass lean a little towards it.
PRIMARY = "#4F7F4C"
TITLE, TAGLINE = "Meadow", "Gentle app limits, kept with friends"


def font_face(family: str, file: str, axes: dict, text: str) -> str:
    """A tiny WOFF2 with just the glyphs needed, at fixed axis values."""
    font = instancer.instantiateVariableFont(TTFont(FONTS / file), axes)
    options = subset.Options()
    options.flavor = "woff2"
    options.layout_features = ["kern", "liga"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=text)
    subsetter.subset(font)
    buffer = io.BytesIO()
    font.flavor = "woff2"
    font.save(buffer)
    data = base64.b64encode(buffer.getvalue()).decode()
    return f"@font-face{{font-family:'{family}';src:url(data:font/woff2;base64,{data}) format('woff2')}}"


FONT_CSS = (
    font_face("MeadowTitle", "fraunces.ttf", {"opsz": 144, "wght": 600, "SOFT": 100, "WONK": 1}, TITLE)
    + font_face("MeadowBody", "nunito.ttf", {"wght": 600}, TAGLINE)
)

MOTION_CSS = """
.sway{transform-box:fill-box;transform-origin:50% 100%;animation:sway 5s ease-in-out infinite alternate}
@keyframes sway{from{transform:rotate(-4deg)}to{transform:rotate(4deg)}}
.drift{animation:drift 70s linear infinite}
@keyframes drift{from{transform:translateX(-260px)}to{transform:translateX(1500px)}}
.glow{transform-box:fill-box;transform-origin:center;animation:glow 4s ease-in-out infinite alternate}
@keyframes glow{from{opacity:.35;transform:scale(.92)}to{opacity:.7;transform:scale(1.08)}}
.twinkle{animation:twinkle 3s ease-in-out infinite alternate}
@keyframes twinkle{from{opacity:.25}to{opacity:1}}
.fall{animation:fall 26s linear infinite}
@keyframes fall{from{transform:translate(0,-40px)}to{transform:translate(-140px,470px)}}
.waft{animation:waft 4.5s ease-in-out infinite alternate}
@keyframes waft{from{transform:translateX(-26px)}to{transform:translateX(26px)}}
.tumble{transform-box:fill-box;transform-origin:center;animation:tumble 6s ease-in-out infinite alternate}
@keyframes tumble{0%{transform:rotate(-35deg) scaleX(1)}50%{transform:rotate(25deg) scaleX(.45)}100%{transform:rotate(80deg) scaleX(.95)}}
.bob{animation:bob .9s ease-in-out infinite alternate}
@keyframes bob{from{transform:translateY(-4px)}to{transform:translateY(4px)}}
.wing{transform-box:fill-box;transform-origin:20% 100%;animation:wing .09s ease-in-out infinite alternate}
.wing.back{transform-origin:80% 100%;animation-delay:-.045s}
@keyframes wing{from{transform:rotate(-24deg)}to{transform:rotate(18deg)}}
.float{animation:float 7s ease-in-out infinite alternate}
@keyframes float{from{transform:translate(0,0)}to{transform:translate(26px,-22px)}}
.rise{animation:rise 3s ease-out infinite;opacity:0}
@keyframes rise{0%{opacity:0;transform:translate(0,0) scale(.7)}25%{opacity:1}100%{opacity:0;transform:translate(14px,-46px) scale(1.15)}}
.pop{transform-box:fill-box;transform-origin:center;animation:pop 2.4s ease-in-out infinite}
@keyframes pop{0%,100%{transform:scale(.85)}50%{transform:scale(1.12)}}
.grow{transform-box:fill-box;transform-origin:50% 100%;animation:grow 4s ease-in-out infinite}
@keyframes grow{0%{transform:scaleY(.15)}45%,85%{transform:scaleY(1)}100%{transform:scaleY(.15)}}
.bloom{transform-box:fill-box;transform-origin:center;animation:bloom 4s ease-in-out infinite}
@keyframes bloom{0%,20%{transform:scale(0)}50%,85%{transform:scale(1)}100%{transform:scale(0)}}
.spin{transform-box:fill-box;transform-origin:center;animation:spin 45s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
@media (prefers-reduced-motion:reduce){*{animation:none!important}.rise{opacity:1}}
"""


def svg(width: int, height: int, body: str, label: str, fonts: bool = False) -> str:
    style = (FONT_CSS if fonts else "") + MOTION_CSS
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'role="img" aria-label="{label}"><title>{label}</title><style>{style}</style>{body}</svg>\n'
    )


def hex_rgb(color: str) -> tuple[float, float, float]:
    return tuple(int(color[i:i + 2], 16) / 255 for i in (1, 3, 5))


def rgb_hex(rgb) -> str:
    return "#" + "".join(f"{round(min(max(v, 0), 1) * 255):02X}" for v in rgb)


def lean_towards(color: str, toward: str, share: float, max_degrees: float) -> str:
    """[color] with its hue turned towards [toward]'s, as leanTowards in Theme.kt."""
    h, l, s = colorsys.rgb_to_hls(*hex_rgb(color))
    h2 = colorsys.rgb_to_hls(*hex_rgb(toward))[0]
    turn = (((h2 - h) * 360 + 540) % 360 - 180) * share
    return rgb_hex(colorsys.hls_to_rgb(((h * 360 + max(-max_degrees, min(max_degrees, turn))) % 360) / 360, l, s))


def over(color: str, alpha: float, below: str) -> str:
    """[color] at [alpha] over [below], as one solid colour."""
    return rgb_hex(a * alpha + b * (1 - alpha) for a, b in zip(hex_rgb(color), hex_rgb(below)))


SUN = lean_towards("#F4C04E", PRIMARY, 0.2, 8)


def grass(dark: bool) -> str:
    """The hills' grass: by night a little darker, scaled so that its hue holds."""
    g = lean_towards("#8DB57A", PRIMARY, 0.2, 12)
    return rgb_hex(v * 0.92 for v in hex_rgb(g)) if dark else g


def hills(dark: bool) -> tuple[str, str, str]:
    """The back, middle and front hills: the grass over the sky at the strengths the app uses."""
    sky = (NIGHT_SKY if dark else DAY_SKY)[1]
    back = over(grass(dark), 0.35, sky)
    middle = over(grass(dark), 0.55, back)
    return back, middle, over(grass(dark), 0.55, middle)


def petals(dark: bool) -> list[str]:
    """The petal colours of the theme's five plants, its own first."""
    return [(p.night() if dark else p).petal.color for p in flora.MEADOW]


def plant(sk: "flora.Sketch", foot: tuple[float, float], scale: float, parts=("fallen", "stem", "head")) -> str:
    """A plant's sketch with its foot at [foot], drawn [scale] times its size."""
    fx, fy = flora.BASE
    return (f'<g transform="translate({foot[0] - fx * scale:.1f} {foot[1] - fy * scale:.1f}) scale({scale:.3f})">'
            f'{sk.svg(parts)}</g>')


def sun(cx: float, cy: float, r: float) -> str:
    """The sun as the app draws it: two soft halos, ten turning rays and the disc."""
    rays = "".join(
        f'<path d="M{math.cos(a) * r * 1.3:.1f},{math.sin(a) * r * 1.3:.1f} L{math.cos(a) * r * 1.75:.1f},{math.sin(a) * r * 1.75:.1f}"/>'
        for a in (2 * math.pi * i / 10 for i in range(10))
    )
    return (
        f'<circle cx="{cx}" cy="{cy}" r="{r * 2.1:.1f}" fill="{SUN}" fill-opacity=".18"/>'
        f'<circle cx="{cx}" cy="{cy}" r="{r * 1.5:.1f}" fill="{SUN}" fill-opacity=".28"/>'
        f'<g transform="translate({cx} {cy})"><g class="spin" stroke="{SUN}" stroke-opacity=".7" stroke-width="{r * .16:.1f}" '
        f'stroke-linecap="round">{rays}</g></g>'
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{SUN}"/>'
    )


def moon(cx: float, cy: float, r: float) -> str:
    """A soft glow, and a crescent: a disc with a bite cut out, as the app draws it."""
    bx, by, br = cx + r * 0.45, cy - r * 0.25, r * 0.86
    d = math.hypot(bx - cx, by - cy)
    a = (r * r - br * br + d * d) / (2 * d)
    h = math.sqrt(r * r - a * a)
    ux, uy = (bx - cx) / d, (by - cy) / d
    mx, my = cx + ux * a, cy + uy * a
    p1 = (mx - uy * h, my + ux * h)
    p2 = (mx + uy * h, my - ux * h)
    return (
        f'<circle cx="{cx}" cy="{cy}" r="{r * 1.8:.1f}" fill="{MOON_LIGHT}" fill-opacity=".15"/>'
        f'<path d="M{p1[0]:.2f},{p1[1]:.2f} A{r},{r} 0 1 1 {p2[0]:.2f},{p2[1]:.2f} '
        f'A{br:.2f},{br:.2f} 0 0 0 {p1[0]:.2f},{p1[1]:.2f}Z" fill="{MOON_LIGHT}"/>'
    )


def cloud(x: float, y: float, r: float, opacity: float) -> str:
    """A cloud as the app draws it: three puffs on a flat base, one even shape."""
    return (
        f'<g fill="#FFFFFF" opacity="{opacity}">'
        f'<rect x="{x - r * 2.4:.1f}" y="{y + r * 0.2:.1f}" width="{r * 4.8:.1f}" height="{r * 1.25:.1f}" rx="{r * 0.62:.1f}"/>'
        f'<circle cx="{x}" cy="{y}" r="{r * 1.4:.1f}"/>'
        f'<circle cx="{x - r * 1.5:.1f}" cy="{y + r * 0.35:.1f}" r="{r * 1.1:.1f}"/>'
        f'<circle cx="{x + r * 1.5:.1f}" cy="{y + r * 0.3:.1f}" r="{r * 1.2:.1f}"/></g>'
    )


def star(x: float, y: float, big: bool, delay: float) -> str:
    return (f'<circle class="twinkle" style="animation-delay:-{delay:.1f}s" cx="{x:.0f}" cy="{y:.0f}" '
            f'r="{3 if big else 1.8}" fill="#FFFFFF" fill-opacity=".85"/>')


def firefly(x: float, y: float, rnd: random.Random) -> str:
    """A firefly glows in the sun's gold."""
    return (
        f'<g class="float" style="animation-delay:-{rnd.uniform(0, 7):.1f}s;animation-duration:{rnd.uniform(5, 9):.1f}s">'
        f'<g class="twinkle" style="animation-delay:-{rnd.uniform(0, 3):.1f}s">'
        f'<circle cx="{x:.0f}" cy="{y:.0f}" r="11" fill="{SUN}" fill-opacity=".25"/>'
        f'<circle cx="{x:.0f}" cy="{y:.0f}" r="3.2" fill="{SUN}"/></g></g>'
    )


def bee(scale: float = 1, uid: str = "bee") -> str:
    return (
        f'<g transform="scale({scale})">'
        f'<defs><clipPath id="{uid}-body"><ellipse cx="0" cy="0" rx="17" ry="12"/></clipPath></defs>'
        '<ellipse class="wing back" cx="-3" cy="-15" rx="7" ry="12" fill="#FFFFFF" fill-opacity=".7" stroke="#B9CBD6" stroke-width="1.2"/>'
        '<path d="M-17,1 l-6,1 l6,2z" fill="#3E3A32"/>'
        '<ellipse cx="0" cy="0" rx="17" ry="12" fill="#F2C14E"/>'
        f'<g clip-path="url(#{uid}-body)" fill="#3E3A32"><rect x="-9" y="-13" width="5" height="26"/><rect x="1" y="-13" width="5" height="26"/></g>'
        '<ellipse cx="0" cy="0" rx="17" ry="12" fill="none" stroke="#C99A2E" stroke-width="1.2"/>'
        '<circle cx="11" cy="-3" r="2.3" fill="#3E3A32"/><circle cx="11.8" cy="-3.8" r=".7" fill="#FFFFFF"/>'
        '<path d="M13,-9 q4,-9 9,-8 M10,-10 q1,-9 6,-10" stroke="#3E3A32" stroke-width="1.6" fill="none" stroke-linecap="round"/>'
        '<ellipse class="wing" cx="4" cy="-16" rx="8" ry="13" fill="#FFFFFF" fill-opacity=".85" stroke="#B9CBD6" stroke-width="1.2"/>'
        "</g>"
    )


def flight_css(name: str, points: list[tuple[float, float, float]], duration: float) -> str:
    """A smooth flight through (time, x, y) points, sampled from a Catmull-Rom curve, banking into turns."""
    def at(t: float) -> tuple[float, float]:
        i = max(k for k in range(len(points) - 1) if points[k][0] <= t)
        p0, p1 = points[max(i - 1, 0)], points[i]
        p2, p3 = points[i + 1], points[min(i + 2, len(points) - 1)]
        u = (t - p1[0]) / (p2[0] - p1[0])
        def cr(a, b, c, d):
            return 0.5 * ((2 * b) + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u ** 3)
        return cr(p0[1], p1[1], p2[1], p3[1]), cr(p0[2], p1[2], p2[2], p3[2])
    frames = []
    steps = 80
    for k in range(steps + 1):
        t = k / steps
        x, y = at(min(t, 0.9999))
        nx, ny = at(min(t + 0.01, 0.9999))
        tilt = max(-14, min(14, math.degrees(math.atan2(ny - y, max(nx - x, 0.5))) * 0.6))
        frames.append(f"{t * 100:.2f}%{{transform:translate({x:.1f}px,{y:.1f}px) rotate({tilt:.1f}deg)}}")
    return f".{name}{{animation:{name} {duration}s linear infinite}}@keyframes {name}{{{''.join(frames)}}}"


def banner(dark: bool) -> str:
    rnd = random.Random(7)
    w, h = 1280, 420
    sky = NIGHT_SKY if dark else DAY_SKY
    back, middle, front = hills(dark)
    # One drawing of each of the theme's five plants in full bloom, used for each plant on the hills.
    plants = "".join(
        f'<g id="plant-{i}">{flora.sketch(p, flora.open_(1), dark).svg(("stem", "head"))}</g>' for i, p in enumerate(flora.MEADOW)
    )
    body = [
        (f'<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sky[0]}"/>'
         f'<stop offset="1" stop-color="{sky[1]}"/></linearGradient>'
         f'<clipPath id="frame"><rect width="{w}" height="{h}" rx="28"/></clipPath>{plants}</defs>'),
        '<g clip-path="url(#frame)">',
        f'<rect width="{w}" height="{h}" fill="url(#sky)"/>',
    ]
    if dark:
        for i in range(46):
            x, y, delay = rnd.uniform(20, w - 20), rnd.uniform(14, 230), rnd.uniform(0, 3)
            # The stars keep clear of the title.
            if not (60 < x < 600 and 70 < y < 240):
                body.append(star(x, y, i % 5 == 0, delay))
        body.append(moon(1070, 104, 40))
    else:
        body.append(sun(1070, 104, 40))
    # The clouds are white: clear by day, and faint by night.
    opacity = .10 if dark else .85
    body.append(f'<g class="drift" style="animation-delay:-20s">{cloud(0, 120, 22, opacity)}</g>')
    body.append(f'<g class="drift" style="animation-duration:95s;animation-delay:-70s">{cloud(0, 70, 15, opacity)}</g>')
    body.append(f'<path d="M0,300 C220,240 420,250 640,290 C860,330 1060,250 1280,270 V420 H0Z" fill="{back}"/>')
    body.append(f'<path d="M0,345 C260,300 520,330 760,340 C1000,350 1140,300 1280,315 V420 H0Z" fill="{middle}"/>')
    # The theme's plants on the hills, kept clear of the title on the left.
    spots = [(560, 318), (640, 330), (720, 322), (820, 336), (900, 326), (980, 318), (1060, 330), (1150, 316), (1230, 326),
             (90, 366), (210, 372), (330, 368), (450, 374)]
    fx, fy = flora.BASE
    for i, (x, y) in enumerate(spots):
        s = rnd.uniform(0.86, 1.04)
        sx = -s if i % 3 == 1 else s
        body.append(
            f'<g transform="translate({x - fx * sx:.1f} {y - fy * s:.1f}) scale({sx:.3f} {s:.3f})">'
            f'<g class="sway" style="animation-delay:-{rnd.uniform(0, 5):.1f}s"><use href="#plant-{i % 5}"/></g></g>'
        )
    body.append(f'<path d="M0,385 C300,360 640,380 900,378 C1100,376 1200,360 1280,365 V420 H0Z" fill="{front}"/>')
    if dark:
        for i in range(9):
            body.append(firefly(rnd.uniform(620, 1250), rnd.uniform(220, 330), rnd))
    else:
        colours = petals(dark)
        for i in range(9):
            px = rnd.uniform(380, 1320)
            duration = rnd.uniform(24, 34)
            size = rnd.uniform(5, 9)
            body.append(
                f'<g class="fall" style="animation-delay:-{rnd.uniform(0, duration):.1f}s;animation-duration:{duration:.1f}s">'
                f'<g class="waft" style="animation-delay:-{rnd.uniform(0, 4.5):.1f}s;animation-duration:{rnd.uniform(3.5, 5.5):.1f}s">'
                f'<g transform="translate({px:.0f} 0)"><g class="tumble" style="animation-delay:-{rnd.uniform(0, 6):.1f}s">'
                f'<ellipse rx="{size:.1f}" ry="{size * 0.6:.1f}" fill="{colours[i % 5]}" fill-opacity=".75"/>'
                "</g></g></g></g>"
            )
        # The bee wanders across, stopping to hover over a flower on the way.
        body.append("<style>" + flight_css("flight", [
            # Low under the title on the left, then up into the open sky.
            (0.00, -90, 288), (0.14, 170, 272), (0.28, 410, 266), (0.38, 626, 238), (0.50, 640, 232),
            (0.60, 800, 180), (0.78, 1060, 205), (1.00, 1380, 160),
        ], 20) + "</style>")
        body.append(f'<g class="flight"><g class="bob">{bee(1.4, "banner-bee")}</g></g>')
    ink, soft = (NIGHT_INK, NIGHT_SOFT) if dark else (INK, SOFT_INK)
    body.append(f'<text x="80" y="176" font-family="MeadowTitle,Georgia,serif" font-size="104" fill="{ink}">{TITLE}</text>')
    body.append(f'<text x="84" y="226" font-family="MeadowBody,Helvetica,sans-serif" font-size="30" fill="{soft}">{TAGLINE}</text>')
    body.append("</g>")
    return svg(w, h, "".join(body), f"{TITLE}: {TAGLINE}", fonts=True)


def backdrop(content: str, label: str) -> str:
    """A round, cream tile that reads well on light and dark pages."""
    return svg(
        220, 220,
        '<defs><clipPath id="tile"><circle cx="110" cy="110" r="104"/></clipPath></defs>'
        '<circle cx="110" cy="110" r="104" fill="#FBF3E6"/>'
        f'<g clip-path="url(#tile)">{content}</g>'
        '<circle cx="110" cy="110" r="104" fill="none" stroke="#E6DCC8" stroke-width="3"/>',
        label,
    )


TILE_HILL = "M0,170 C60,150 160,150 220,170 V220 H0Z"


def step_plant() -> str:
    """The theme's own plant grows and opens."""
    cosmos = flora.sketch(flora.COSMOS, flora.open_(1))
    return backdrop(
        f'<path d="{TILE_HILL}" fill="{hills(False)[1]}"/>'
        f'<g class="grow" style="animation-delay:-2s">{plant(cosmos, (110, 178), 1.55, ("stem",))}</g>'
        f'<g class="bloom" style="animation-delay:-2s">{plant(cosmos, (110, 178), 1.55, ("head",))}</g>'
        '<g transform="translate(168 62)"><circle r="18" fill="#FFFFFF" stroke="#5B7F55" stroke-width="4"/>'
        '<path d="M0,-11 V0 L8,5" stroke="#3E3A32" stroke-width="4" fill="none" stroke-linecap="round"/></g>',
        "Plant a limit",
    )


def step_rest() -> str:
    """The plant is dried, and sleeps under the moon until tomorrow."""
    rnd = random.Random(3)
    stars = "".join(star(rnd.uniform(20, 200), rnd.uniform(16, 120), i % 4 == 0, rnd.uniform(0, 3)) for i in range(12))
    cosmos = flora.sketch(flora.COSMOS, flora.SPENT, dark=True)
    return backdrop(
        f'<defs><linearGradient id="night" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{NIGHT_SKY[0]}"/>'
        f'<stop offset="1" stop-color="{NIGHT_SKY[1]}"/></linearGradient></defs>'
        '<rect width="220" height="220" fill="url(#night)"/>'
        + stars + moon(66, 62, 20)
        + f'<path d="{TILE_HILL}" fill="{hills(True)[1]}"/>'
        + plant(cosmos, (104, 178), 1.55)
        + "".join(
            f'<text class="rise" style="animation-delay:-{d}s" x="{146 + i * 9}" y="{90 - i * 8}" font-family="Georgia,serif" '
            f'font-size="{18 + i * 4}" font-weight="700" fill="{NIGHT_SOFT}">z</text>'
            for i, d in enumerate((0, 1, 2))
        ),
        "When time is up, the app rests",
    )


def step_ask() -> str:
    """A Bumblebee brings more time to a buttercup."""
    buttercup = flora.sketch(flora.BUTTERCUP, flora.open_(1))
    return backdrop(
        f'<path d="{TILE_HILL}" fill="{hills(False)[1]}"/>'
        f'<g class="sway" style="animation-delay:-1s">{plant(buttercup, (76, 178), 1.4, ("stem", "head"))}</g>'
        + '<g transform="translate(150 104)"><g class="bob">' + bee(1.3, "step-bee") + "</g></g>"
        f'<path class="pop" d="M128,48 c0,-10 14,-12 16,-2 c2,-10 16,-8 16,2 c0,10 -16,18 -16,22 c0,-4 -16,-12 -16,-22z" fill="{petals(False)[0]}"/>',
        "Ask a Bumblebee for more time",
    )


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "banner-light.svg").write_text(banner(dark=False))
    (OUT / "banner-dark.svg").write_text(banner(dark=True))
    (OUT / "step-plant.svg").write_text(step_plant())
    (OUT / "step-rest.svg").write_text(step_rest())
    (OUT / "step-ask.svg").write_text(step_ask())
    for p in sorted(OUT.glob("*.svg")):
        print(p.name, p.stat().st_size)


if __name__ == "__main__":
    main()
