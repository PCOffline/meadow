"""Draws the README's animated SVG art in Meadow's palette, with its own fonts embedded.

Run from the repository root: python3 docs/art/build.py  (needs: pip install fonttools brotli)
Writes docs/images/banner-light.svg, banner-dark.svg and the step-*.svg illustrations.
Animations are CSS, so they stop for people who ask for reduced motion.
"""
import base64
import io
import math
import random
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "app/src/main/res/font"
OUT = ROOT / "docs/images"

INK, NIGHT_INK = "#3E3A32", "#F3EEE2"
SOFT_INK, NIGHT_SOFT = "#6F675A", "#C3BBAC"
PETALS = ["#E8A0A6", "#B4A7D6", "#E9B949", "#F2B48C", "#9CC3D5"]
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
@media (prefers-reduced-motion:reduce){*{animation:none!important}.rise{opacity:1}}
"""


def svg(width: int, height: int, body: str, label: str, fonts: bool = False) -> str:
    style = (FONT_CSS if fonts else "") + MOTION_CSS
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'role="img" aria-label="{label}"><title>{label}</title><style>{style}</style>{body}</svg>\n'
    )


def flower(x: float, y: float, size: float, petal: str, stem: str, delay: float, petals: int = 6) -> str:
    """A swaying flower whose stem starts at (x, y)."""
    head_y = y - size * 2.2
    parts = [
        (f'<path d="M{x},{y} C{x - size * .3},{y - size} {x + size * .3},{y - size * 1.5} {x},{head_y}" '
        f'stroke="{stem}" stroke-width="{max(2, size * .14):.1f}" fill="none" stroke-linecap="round"/>'),
        (f'<ellipse cx="{x + size * .38}" cy="{y - size * .9}" rx="{size * .34}" ry="{size * .14}" fill="{stem}" '
        f'transform="rotate(-30 {x + size * .38} {y - size * .9})"/>'),
    ]
    for i in range(petals):
        angle = 360 / petals * i
        rad = math.radians(angle)
        px, py = x + math.cos(rad) * size * .42, head_y + math.sin(rad) * size * .42
        parts.append(
            f'<ellipse cx="{px:.1f}" cy="{py:.1f}" rx="{size * .36:.1f}" ry="{size * .22:.1f}" fill="{petal}" '
            f'transform="rotate({angle:.0f} {px:.1f} {py:.1f})"/>'
        )
    parts.append(f'<circle cx="{x}" cy="{head_y}" r="{size * .24:.1f}" fill="#F6E3A1"/>')
    return f'<g class="sway" style="animation-delay:-{delay:.1f}s">{"".join(parts)}</g>'


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


def cloud(x: float, y: float, s: float, color: str) -> str:
    return (
        f'<g fill="{color}"><ellipse cx="{x}" cy="{y}" rx="{60 * s}" ry="{22 * s}"/>'
        f'<circle cx="{x - 22 * s}" cy="{y - 12 * s}" r="{24 * s}"/><circle cx="{x + 16 * s}" cy="{y - 18 * s}" r="{30 * s}"/></g>'
    )


def banner(dark: bool) -> str:
    rnd = random.Random(7)
    w, h = 1280, 420
    sky = ("#26304A", "#3A4658") if dark else ("#D9ECF2", "#FBF4E8")
    hills = ("#2F472B", "#3B5636", "#263B23") if dark else ("#C4DBB4", "#9CC08E", "#86AE79")
    stem = "#6E8F63" if dark else "#5B7F55"
    body = [
        (f'<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sky[0]}"/>'
        f'<stop offset="1" stop-color="{sky[1]}"/></linearGradient>'
        f'<radialGradient id="halo"><stop offset="0" stop-color="{"#F3EEE2" if dark else "#FBE3A6"}"/>'
        f'<stop offset="1" stop-color="{"#F3EEE2" if dark else "#FBE3A6"}" stop-opacity="0"/></radialGradient>'
        '<clipPath id="frame"><rect width="1280" height="420" rx="28"/></clipPath></defs>'),
        '<g clip-path="url(#frame)">',
        f'<rect width="{w}" height="{h}" fill="url(#sky)"/>',
    ]
    if dark:
        for i in range(46):
            sx, sy, r = rnd.uniform(20, w - 20), rnd.uniform(14, 230), rnd.uniform(.8, 2.2)
            body.append(f'<circle class="twinkle" style="animation-delay:-{rnd.uniform(0, 3):.1f}s" cx="{sx:.0f}" cy="{sy:.0f}" r="{r:.1f}" fill="#F3EEE2"/>')
        body.append('<circle class="glow" cx="1070" cy="100" r="90" fill="url(#halo)"/>')
        body.append('<circle cx="1070" cy="100" r="40" fill="#F3EEE2"/><circle cx="1088" cy="88" r="36" fill="#2B3550"/>')
    else:
        body.append('<circle class="glow" cx="1070" cy="104" r="96" fill="url(#halo)"/>')
        body.append('<circle cx="1070" cy="104" r="44" fill="#F6D58A"/>')
    cloud_color = "#4A5670" if dark else "#FFFFFF"
    body.append(f'<g class="drift" style="animation-delay:-20s" opacity=".9">{cloud(0, 120, 1.1, cloud_color)}</g>')
    body.append(f'<g class="drift" style="animation-duration:95s;animation-delay:-70s" opacity=".7">{cloud(0, 70, .8, cloud_color)}</g>')
    body.append(f'<path d="M0,300 C220,240 420,250 640,290 C860,330 1060,250 1280,270 V420 H0Z" fill="{hills[0]}"/>')
    body.append(f'<path d="M0,345 C260,300 520,330 760,340 C1000,350 1140,300 1280,315 V420 H0Z" fill="{hills[1]}"/>')
    # Flowers on the hills, kept clear of the title on the left.
    spots = [(560, 318), (640, 330), (720, 322), (820, 336), (900, 326), (980, 318), (1060, 330), (1150, 316), (1230, 326),
             (90, 366), (210, 372), (330, 368), (450, 374)]
    for i, (fx, fy) in enumerate(spots):
        petal = PETALS[i % len(PETALS)]
        if dark:
            petal = {"#E8A0A6": "#C98A90", "#B4A7D6": "#9B8FC0", "#E9B949": "#CFA645", "#F2B48C": "#D69C7A", "#9CC3D5": "#86AABB"}[petal]
        body.append(flower(fx, fy, rnd.uniform(19, 27), petal, stem, rnd.uniform(0, 5)))
    body.append(f'<path d="M0,385 C300,360 640,380 900,378 C1100,376 1200,360 1280,365 V420 H0Z" fill="{hills[2]}"/>')
    if dark:
        for i in range(9):
            fx, fy = rnd.uniform(520, 1250), rnd.uniform(220, 330)
            body.append(
                f'<g class="float" style="animation-delay:-{rnd.uniform(0, 7):.1f}s;animation-duration:{rnd.uniform(5, 9):.1f}s">'
                f'<circle class="twinkle" style="animation-delay:-{rnd.uniform(0, 3):.1f}s" cx="{fx:.0f}" cy="{fy:.0f}" r="3.2" fill="#F6E3A1"/></g>'
            )
    else:
        for i in range(9):
            px = rnd.uniform(380, 1320)
            duration = rnd.uniform(24, 34)
            body.append(
                f'<g class="fall" style="animation-delay:-{rnd.uniform(0, duration):.1f}s;animation-duration:{duration:.1f}s">'
                f'<g class="waft" style="animation-delay:-{rnd.uniform(0, 4.5):.1f}s;animation-duration:{rnd.uniform(3.5, 5.5):.1f}s">'
                f'<g transform="translate({px:.0f} 0) scale(1.3)"><g class="tumble" style="animation-delay:-{rnd.uniform(0, 6):.1f}s">'
                f'<path d="M0,-9 C7,-6 7,5 0,9 C-7,5 -7,-6 0,-9Z" fill="{PETALS[i % 4]}" opacity=".9"/>'
                f'<path d="M0,-6 V6" stroke="#FFFFFF" stroke-opacity=".5" stroke-width="1.2" stroke-linecap="round"/>'
                "</g></g></g></g>"
            )
        # The bee wanders across, stopping to hover over a flower on the way.
        body.append("<style>" + flight_css("flight", [
            # Low under the title on the left, then up into the open sky.
            (0.00, -90, 288), (0.14, 170, 272), (0.28, 410, 266), (0.38, 626, 246), (0.50, 640, 240),
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


def step_plant() -> str:
    return backdrop(
        '<path d="M0,170 C60,150 160,150 220,170 V220 H0Z" fill="#9CC08E"/>'
        '<g class="grow" style="animation-delay:-2s"><path d="M110,172 C104,150 116,128 110,98" stroke="#5B7F55" stroke-width="6" fill="none" stroke-linecap="round"/>'
        '<ellipse cx="124" cy="140" rx="15" ry="6" fill="#5B7F55" transform="rotate(-30 124 140)"/>'
        '<ellipse cx="96" cy="128" rx="13" ry="5" fill="#5B7F55" transform="rotate(30 96 128)"/></g>'
        '<g class="bloom" style="animation-delay:-2s">' + "".join(
            f'<ellipse cx="{110 + math.cos(math.radians(a)) * 15:.1f}" cy="{92 + math.sin(math.radians(a)) * 15:.1f}" rx="13" ry="8" '
            f'fill="#E8A0A6" transform="rotate({a} {110 + math.cos(math.radians(a)) * 15:.1f} {92 + math.sin(math.radians(a)) * 15:.1f})"/>'
            for a in range(0, 360, 60)
        ) + '<circle cx="110" cy="92" r="9" fill="#F6E3A1"/></g>'
        '<g transform="translate(150 66)"><circle r="20" fill="#FFFFFF" stroke="#5B7F55" stroke-width="4"/>'
        '<path d="M0,-12 V0 L9,6" stroke="#3E3A32" stroke-width="4" fill="none" stroke-linecap="round"/></g>',
        "Plant a limit",
    )


def step_rest() -> str:
    petals = "".join(
        f'<ellipse cx="{110 + math.cos(math.radians(a)) * 12:.1f}" cy="{112 + math.sin(math.radians(a)) * 6:.1f}" rx="10" ry="6" '
        f'fill="#B4A7D6" transform="rotate({a / 3:.0f} 110 112)"/>'
        for a in range(-60, 241, 60)
    )
    return backdrop(
        '<rect width="220" height="220" fill="#3A4658" opacity=".12"/>'
        '<path d="M62,36 A22,22 0 1,0 62,80 A17,22 0 1,1 62,36 Z" fill="#E9C46A"/>'
        '<path d="M0,170 C60,150 160,150 220,170 V220 H0Z" fill="#9CC08E"/>'
        '<path d="M110,172 C108,150 112,136 114,122 C118,110 104,108 104,112" stroke="#5B7F55" stroke-width="6" fill="none" stroke-linecap="round"/>'
        f'<g transform="rotate(25 110 112)">{petals}<circle cx="110" cy="112" r="7" fill="#F6E3A1"/></g>'
        + "".join(
            f'<text class="rise" style="animation-delay:-{d}s" x="{132 + i * 8}" y="{96 - i * 6}" font-family="Georgia,serif" '
            f'font-size="{18 + i * 4}" font-weight="700" fill="#6F675A">z</text>'
            for i, d in enumerate((0, 1, 2))
        ),
        "When time is up, the app rests",
    )


def step_ask() -> str:
    return backdrop(
        '<path d="M0,170 C60,150 160,150 220,170 V220 H0Z" fill="#9CC08E"/>'
        + flower(80, 176, 26, "#E9B949", "#5B7F55", 1.0)
        + '<g transform="translate(138 92)"><g class="bob">' + bee(1.3, "step-bee") + "</g></g>"
        '<path class="pop" d="M120,48 c0,-10 14,-12 16,-2 c2,-10 16,-8 16,2 c0,10 -16,18 -16,22 c0,-4 -16,-12 -16,-22z" fill="#E8A0A6"/>',
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
