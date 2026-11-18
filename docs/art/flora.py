"""Meadow's plants as flat SVG shapes, drawn as the app draws them.

This is a port of the app's flora (app/src/main/java/dev/pcoffline/meadow/ui/components/flora):
Tone.kt, Sketch.kt, and the flowers with a head of petals in Forms.kt. It has the Meadow theme's
five plants from Plants.kt. When those files change, change this file to match.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, replace

# ---------- Tone.kt ----------


def turn(h: float, toward: float, share: float) -> float:
    """The hue [h] turned [share] of the way towards [toward], the short way round."""
    d = (toward - h + 540) % 360 - 180
    return (h + d * share) % 360


def oklch(l: float, c: float, h: float) -> str:
    """The sRGB colour of an OKLCH colour, as #RRGGBB. Out of gamut, it keeps its lightness and hue."""
    hr = math.radians(h)
    ca, sb = math.cos(hr), math.sin(hr)

    def linear(chroma: float) -> list[float]:
        a, b = chroma * ca, chroma * sb
        l3 = (l + 0.3963377774 * a + 0.2158037573 * b) ** 3
        m3 = (l - 0.1055613458 * a - 0.0638541728 * b) ** 3
        s3 = (l - 0.0894841775 * a - 1.2914855480 * b) ** 3
        return [
            4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
            -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
            -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
        ]

    def shows(rgb: list[float]) -> bool:
        return all(-0.0005 <= v <= 1.0005 for v in rgb)

    rgb = linear(c)
    if not shows(rgb):
        fits, over = 0.0, c
        for _ in range(12):
            mid = (fits + over) / 2
            if shows(linear(mid)):
                fits = mid
            else:
                over = mid
        rgb = linear(fits)

    def encode(v: float) -> int:
        x = min(max(v, 0.0), 1.0)
        s = 12.92 * x if x <= 0.0031308 else 1.055 * x ** (1 / 2.4) - 0.055
        return round(s * 255)

    return "#" + "".join(f"{encode(v):02X}" for v in rgb)


@dataclass(frozen=True)
class Tone:
    l: float
    c: float
    h: float

    @property
    def color(self) -> str:
        return oklch(self.l, self.c, self.h)

    def shade(self, k: float = 1) -> Tone:
        return Tone(self.l - 0.10 * k, self.c + 0.012 * k, turn(self.h, 285, 0.09 * k))

    def light(self, k: float = 1) -> Tone:
        return Tone(min(0.97, self.l + 0.07 * k), self.c * (1 - 0.25 * k), turn(self.h, 95, 0.10 * k))

    def dried(self, k: float = 1) -> Tone:
        return Tone(self.l - 0.06 * k, self.c * (1 - 0.42 * k), turn(self.h, 75, 0.14 * k) if self.h < 130 else self.h)

    def night(self) -> Tone:
        return Tone(self.l - 0.04, self.c * 0.9, turn(self.h, 265, 0.06))

    def mix(self, other: Tone, k: float) -> Tone:
        return Tone(self.l + (other.l - self.l) * k, self.c + (other.c - self.c) * k, turn(self.h, other.h, k))


# ---------- Plants.kt ----------


@dataclass(frozen=True)
class Pose:
    tilt: float
    face: float
    cup: float
    plen: float
    dry: float
    y: float
    x: float
    lean: float


@dataclass(frozen=True)
class Plant:
    name: str
    form: str  # RAY, ROSETTE or CUP
    petal: Tone
    centre: Tone
    n: int = 0
    kind: str = "ROUND"
    len: float = 0
    half: float = 0
    disc: float = 0
    size: float = 1
    alt: float = 0
    layers: int = 1
    seeds: int = 0
    stamens: int = 0
    dome: float = 0.3
    leaf: str = "LANCE"
    shed: int = 0
    keep: frozenset | None = None
    pod: str | None = None
    spent: Pose | None = None

    def night(self) -> Plant:
        return replace(self, petal=self.petal.night(), centre=self.centre.night())


@dataclass(frozen=True)
class Soil:
    ground: Tone
    stem: Tone
    leaf: Tone

    def night(self) -> Soil:
        g, s, f = self.ground, self.stem, self.leaf
        return Soil(Tone(g.l * 0.52, g.c, g.h), Tone(s.l - 0.05, s.c * 0.9, s.h), Tone(f.l - 0.07, f.c * 0.92, f.h))


PALE = Tone(0.94, 0.115, 102)
AMBER = Tone(0.72, 0.16, 62)
PLUM = Tone(0.36, 0.07, 340)

FULL_POSE = Pose(tilt=-14, face=0.86, cup=0.04, plen=1, dry=0, y=40, x=46, lean=-6)
LOW_POSE = Pose(tilt=40, face=0.72, cup=0.24, plen=0.97, dry=0.1, y=45, x=53, lean=-9)
SPENT_POSE = Pose(tilt=98, face=0.7, cup=0.3, plen=0.8, dry=1, y=51, x=60, lean=-13)
ASLEEP_POSE = Pose(tilt=16, face=0.5, cup=1, plen=1, dry=0, y=47, x=49, lean=-7)

MEADOW_SOIL = Soil(Tone(0.80, 0.045, 95), Tone(0.60, 0.10, 138), Tone(0.70, 0.12, 138))

COSMOS = Plant("Cosmos", "RAY", Tone(0.66, 0.16, 350), PALE, n=8, kind="TOOTH", len=17, half=6, disc=4.6, alt=0.45,
               leaf="FEATHER", shed=3, keep=frozenset({0, 1, 2, 4, 5, 7}))
CORN_POPPY = Plant("Corn poppy", "CUP", Tone(0.70, 0.185, 30), PLUM, n=4, kind="WIDE", len=18.5, half=12.2, disc=4.4,
                   stamens=8, pod="POPPY", shed=4, leaf="FEATHER",
                   spent=Pose(tilt=14, face=0.6, cup=0, plen=1, dry=1, y=44, x=50, lean=-8))
CORNFLOWER = Plant("Cornflower", "RAY", Tone(0.69, 0.125, 262), Tone(0.45, 0.13, 295), n=9, kind="FAN", len=17.5,
                   half=5.6, disc=5.4, seeds=9, alt=0.5, shed=3, keep=frozenset({0, 2, 3, 5, 6, 8}))
BUTTERCUP = Plant("Buttercup", "ROSETTE", Tone(0.85, 0.17, 92), AMBER, n=5, kind="ROUND", len=15, half=7.6, disc=4.4,
                  size=0.95, alt=0.4, stamens=9, shed=3, keep=frozenset({0, 1, 3, 4}))
ASTER = Plant("Aster", "RAY", Tone(0.75, 0.105, 300), PALE, n=13, kind="NARROW", len=17, half=3.3, disc=6.4,
              layers=2, seeds=14, dome=0.5, shed=2, keep=frozenset({0, 1, 2, 4, 5, 7, 8, 9, 11, 12}))

# The Meadow theme's five plants, its own first.
MEADOW = [COSMOS, CORN_POPPY, CORNFLOWER, BUTTERCUP, ASTER]

# ---------- Sketch.kt ----------

WIDTH, HEIGHT = 90, 120
BASE = (45.0, 106.0)


def add(a, b):
    return (a[0] + b[0], a[1] + b[1])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1])


def mul(a, k):
    return (a[0] * k, a[1] * k)


def bezier(p0, p1, p2, p3, n: int = 24):
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((
            p0[0] * u * u * u + p1[0] * 3 * u * u * t + p2[0] * 3 * u * t * t + p3[0] * t * t * t,
            p0[1] * u * u * u + p1[1] * 3 * u * u * t + p2[1] * 3 * u * t * t + p3[1] * t * t * t,
        ))
    return out


class Sketch:
    """A plant as flat shapes. Each piece has a part: soil, fallen (on the ground), stem or head."""

    def __init__(self):
        self.pieces: list[tuple[str, str, list]] = []
        self.part = "stem"

    def poly(self, points, tone: Tone):
        self.pieces.append((self.part, tone.color, points))

    def ellipse(self, c, rx: float, ry: float, tone: Tone, degrees: float = 0):
        n = 12 if rx < 2.5 else 28
        r = math.radians(degrees)
        cr, sr = math.cos(r), math.sin(r)
        pts = []
        for i in range(n):
            a = 2 * math.pi * i / n
            x, y = rx * math.cos(a), ry * math.sin(a)
            pts.append((c[0] + x * cr - y * sr, c[1] + x * sr + y * cr))
        self.poly(pts, tone)

    def circle(self, c, r: float, tone: Tone):
        self.ellipse(c, r, r, tone)

    def ribbon(self, line, w0: float, w1: float, tone: Tone):
        n = len(line) - 1
        left, right = [], []
        for i, p in enumerate(line):
            a, b = line[max(i - 1, 0)], line[min(i + 1, n)]
            d = math.hypot(b[0] - a[0], b[1] - a[1]) or 1.0
            normal = (-(b[1] - a[1]) / d, (b[0] - a[0]) / d)
            w = (w0 + (w1 - w0) * i / n) / 2
            left.append(add(p, mul(normal, w)))
            right.append(sub(p, mul(normal, w)))
        self.poly(left + right[::-1], tone)
        self.circle(line[-1], w1 / 2, tone)

    def svg(self, parts=None) -> str:
        """The pieces as SVG paths, in painting order."""
        out = []
        for part, color, pts in self.pieces:
            if parts is not None and part not in parts:
                continue
            d = "M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + "Z"
            out.append(f'<path d="{d}" fill="{color}"/>')
        return "".join(out)


# ---------- Forms.kt: flowers with a head of petals ----------

PLENTY_LEFT, LITTLE_LEFT = 0.5, 0.125
SHEDS_BELOW = 0.35
FALLEN = [((60, 107), 12), ((29, 108), -14), ((67, 105.5), 28), ((38, 109.5), 6), ((53, 109.5), -22)]


def vigour(bloom: float) -> float:
    """How lively a flower with [bloom] of its time left looks, from 0 to 1."""
    t = min(max((bloom - LITTLE_LEFT) / (PLENTY_LEFT - LITTLE_LEFT), 0), 1)
    return t * t * (3 - 2 * t)


class Look:
    """What a plant shows: open (with its vigour), spent or asleep."""

    def __init__(self, kind: str, vigour: float = 1):
        self.kind, self.vigour = kind, vigour

    def of(self, full, low, spent, asleep):
        if self.kind == "open":
            return low + (full - low) * self.vigour
        return spent if self.kind == "spent" else asleep


def open_(vigour_: float = 1) -> Look:
    return Look("open", vigour_)


SPENT, ASLEEP = Look("spent"), Look("asleep")


def mix_pose(a: Pose, b: Pose, t: float) -> Pose:
    return Pose(*(x + (y - x) * t for x, y in zip(
        (a.tilt, a.face, a.cup, a.plen, a.dry, a.y, a.x, a.lean),
        (b.tilt, b.face, b.cup, b.plen, b.dry, b.y, b.x, b.lean))))


def up(degrees: float):
    r = math.radians(degrees)
    return (math.sin(r), -math.cos(r))


def stem_line(head, tilt, lean, reach=18):
    base = BASE
    return bezier(base, (base[0] + lean, base[1] - 34), sub(head, mul(up(tilt), reach)), head)


def leaf(sk: Sketch, at, angle, length, width, tone: Tone, kind="LANCE"):
    d = up(angle)
    n = (d[1], -d[0])
    broad = kind in ("HEART", "OVAL")

    def edge(t, side):
        w = width * max(0.0, math.sin(math.pi * t ** (0.75 if kind == "HEART" else 1.1))) ** (0.7 if broad else 1)
        bend = math.sin(t * math.pi) * length * 0.10
        return add(add(at, mul(d, t * length)), mul(n, w * side + bend))

    def shape(low, tone_):
        sk.poly([edge(i / 12, 1) for i in range(13)] + [edge((12 - i) / 12, low) for i in range(13)], tone_)

    shape(-1, tone)
    shape(0, tone.light(0.9))


def leaves(sk: Sketch, p: Plant, soil: Soil, line, droop, wilt):
    tone = soil.leaf.mix(soil.leaf.dried(), wilt * 0.7)

    def on(t):
        return line[int(t * (len(line) - 1))]

    if p.leaf == "NONE":
        return
    if p.leaf == "FEATHER":
        for t, side in ((0.25, -1), (0.42, 1), (0.56, -1)):
            at = on(t)
            for da in (-16, 8, 30):
                tip = add(at, mul(up(side * (52 + da + droop)), 12))
                sk.ribbon([at, ((at[0] + tip[0]) / 2, (at[1] + tip[1]) / 2 - 1), tip], 1.5, 0.6, tone)
    elif p.leaf == "HEART":
        leaf(sk, on(0.28), -62 - droop, 21, 8.5, tone, "HEART")
        leaf(sk, on(0.5), 66 + droop, 18, 7.5, tone, "HEART")
    else:
        w = 4.6 if p.leaf == "LANCE" else 6.2
        leaf(sk, on(0.24), -58 - droop, 19, w, tone, p.leaf)
        leaf(sk, on(0.46), 62 + droop, 16, w * 0.9, tone, p.leaf)


PETAL_PQ = {"ROUND": (1.7, 0.62), "NOTCH": (1.7, 0.62), "POINT": (1.25, 1.15), "NARROW": (1.3, 0.9),
            "TOOTH": (1.9, 0.55), "WIDE": (1.9, 0.5), "FAN": (3.2, 0.8)}
PETAL_END = {"NOTCH": 0.975, "TOOTH": 0.9, "FAN": 0.84}


def petal_outline(kind, length, half):
    p, q = PETAL_PQ[kind]
    end = PETAL_END.get(kind, 1.0)
    steps = 10
    top = []
    for i in range(steps + 1):
        t = i / steps * end
        top.append((t * length, half * max(0.0, math.sin(math.pi * t ** p)) ** q))
    w = top[-1][1]
    if kind == "NOTCH":
        tip = [(length * 0.995, half * 0.2), (length * 0.86, 0), (length * 0.995, -half * 0.2)]
    elif kind == "TOOTH":
        tip = [(length * 0.97, w * 0.8), (length * 0.955, w * 0.42), (length, 0), (length * 0.955, -w * 0.42), (length * 0.97, -w * 0.8)]
    elif kind == "FAN":
        tip = [(length, w * 0.95), (length * 0.86, w * 0.5), (length * 1.04, 0), (length * 0.86, -w * 0.5), (length, -w * 0.95)]
    else:
        tip = []
    return top + tip + [(x, -y) for x, y in reversed(top)]


class Head:
    """A flower's head: petals round an axis, seen by the same camera as its stem."""

    def __init__(self, at, tilt, face, cup=0.0):
        self.at, self.face, self.cup = at, face, cup
        self.v = up(tilt)
        r = math.radians(tilt)
        self.u = (math.cos(r), math.sin(r))
        self.across = math.degrees(math.atan2(self.u[1], self.u[0]))
        self.side = math.sqrt(max(0.0, 1 - face * face))

    def project(self, x, y, z=0.0):
        return add(add(self.at, mul(self.u, x)), mul(self.v, self.face * y + self.side * z))

    def petal(self, angle, kind, length, half, start=0.0):
        a = math.radians(angle)
        out = []
        for px, py in petal_outline(kind, length, half):
            lift = self.cup * (start + px) * (0.55 + 0.45 * px / max(length, 1e-6))
            r = start + px * (1 - 0.45 * self.cup)
            out.append(self.project(r * math.cos(a) - py * math.sin(a), r * math.sin(a) + py * math.cos(a), lift))
        return out


def ring(sk: Sketch, head: Head, count, kind, length, half, main: Tone, shade: Tone,
         start=0.0, offset=0.0, keep=None, far_shade=True, alt=0.0):
    k = min(1.0, (1 - head.face) * 1.5 + head.cup * 0.5) if far_shade else 0.0
    items = [(math.sin(math.radians(offset + 360 / count * i)), offset + 360 / count * i, i)
             for i in range(count) if keep is None or i in keep]
    items.sort(key=lambda it: ((-(it[2] % 2)) if alt > 0 else 0, -it[0]))
    for depth, angle, i in items:
        base = main.mix(shade, alt) if alt > 0 and i % 2 == 1 else main
        tone = base.mix(shade, k * depth ** 0.6) if depth > 0.05 else base
        sk.poly(head.petal(angle, kind, length, half, start), tone)


def disc(sk: Sketch, head: Head, radius, tone, dome):
    sk.ellipse(head.project(0, 0, radius * dome), radius, radius * max(head.face, 0.45), tone, head.across)


def dots(sk: Sketch, head: Head, count, radius, r, tone, dome):
    for i in range(count):
        a = math.radians(360 / count * i)
        sk.circle(head.project(radius * math.cos(a), radius * math.sin(a), radius * dome), r, tone)


GOLDEN_ANGLE = 2.39996


def spiral(count, radius):
    return [(radius * math.sqrt((i + 0.5) / count) * math.cos(i * GOLDEN_ANGLE),
             radius * math.sqrt((i + 0.5) / count) * math.sin(i * GOLDEN_ANGLE)) for i in range(count)]


def bud(sk: Sketch, at, tilt, long, petal: Tone, cup: Tone):
    head = Head(at, tilt, 1)
    sk.poly(head.petal(90, "POINT", long, long * 0.36, -1), petal)
    sk.poly(head.petal(82, "POINT", long * 0.93, long * 0.2, -1), petal.shade())
    sk.poly(head.petal(98, "POINT", long * 0.9, long * 0.17, -1), petal.light(0.5))
    sk.poly(head.petal(58, "POINT", long * 0.5, long * 0.15, -2), cup)
    sk.poly(head.petal(122, "POINT", long * 0.5, long * 0.15, -2), cup)
    sk.poly(head.petal(90, "POINT", long * 0.4, long * 0.19, -2.5), cup.light(0.6))


def poppy_pod(sk: Sketch, soil: Soil, head: Head, petal: Tone):
    v, at = head.v, head.at
    tone = soil.leaf.dried(0.75)
    sk.ellipse(add(at, mul(v, 3.5)), 6.2, 7, tone, head.across)
    sk.ellipse(add(at, mul(v, 9.5)), 6.4, 1.9, tone.shade(), head.across)
    sk.ellipse(sub(at, mul(v, 3.5)), 3, 1.5, tone.shade(0.6), head.across)
    # One petal still holds on.
    sk.poly(Head(sub(at, v), 0, 1).petal(-62, "WIDE", 15, 8), petal)


def fallen(sk: Sketch, points, tone, r=3.0):
    for p, angle in points:
        sk.ellipse(p, r, r * 0.42, tone, angle)


def flower(sk: Sketch, p: Plant, soil: Soil, look: Look):
    if look.kind == "open":
        pose = mix_pose(LOW_POSE, FULL_POSE, look.vigour)
    elif look.kind == "spent":
        pose = p.spent or SPENT_POSE
    else:
        pose = ASLEEP_POSE
    wilt = pose.dry
    petal = p.petal.mix(p.petal.dried(), wilt)
    shade = petal.shade()
    centre = p.centre.mix(p.centre.dried(0.6).shade(0.5), wilt)
    stem_tone = soil.stem.mix(soil.stem.dried(), wilt * 0.6)
    cup_tone = soil.leaf.mix(soil.leaf.dried(), wilt * 0.6)
    size = p.size * 1.1
    at = (pose.x, pose.y)
    line = stem_line(at, pose.tilt, pose.lean)
    droop = look.of(0, 14, 34, 6)
    head = Head(at, pose.tilt, pose.face, pose.cup)
    length = p.len * size * pose.plen
    half = p.half * size
    d = p.disc * size

    sk.part = "stem"
    sk.ribbon(line, 4.4, 2.6, stem_tone)
    leaves(sk, p, soil, line, droop, wilt)
    sk.part = "head"
    if look.kind == "asleep":
        bud(sk, at, pose.tilt, (length + d) * 1.12, petal, cup_tone)
        return
    if look.kind == "spent" and p.pod == "POPPY":
        poppy_pod(sk, soil, head, petal)
        return
    keep = p.keep if look.kind == "spent" else None
    if pose.face < 0.66 or look.kind == "spent":
        r = d * 1.1 + 1.5
        sk.ellipse(sub(at, mul(head.v, r * 0.55)), r * 0.8, r * 0.62, cup_tone, head.across)
    if p.form == "RAY":
        if p.layers > 1:
            ring(sk, head, p.n, p.kind, length * 0.97, half, shade, shade.shade(0.6), start=d * 0.5, offset=180 / p.n, keep=keep)
        ring(sk, head, p.n, p.kind, length, half, petal, shade, start=d * 0.5, keep=keep, alt=p.alt)
        disc(sk, head, d, centre, p.dome)
        if p.seeds > 0:
            for x, y in spiral(p.seeds, d * 0.78):
                sk.circle(head.project(x, y, d * 0.78 * p.dome), d * 0.085, centre.light(1.6))
    elif p.form == "ROSETTE":
        offset = 90 - 180 / p.n
        # Behind the petals are the points of the cup. They show where petals fell.
        if look.kind == "spent":
            ring(sk, head, 5, "POINT", d * 2.3, d * 0.6, cup_tone, cup_tone, start=d * 0.5, offset=offset + 36, far_shade=False)
        ring(sk, head, p.n, p.kind, length, half, petal, shade, start=d * 0.2, offset=offset, keep=keep, alt=p.alt)
        disc(sk, head, d, centre, 0.2)
        if p.stamens > 0 and look.kind != "spent":
            dots(sk, head, p.stamens, d * 1.9, 0.95 * size, centre, 0.5)
    elif p.form == "CUP":
        # A few broad petals in two layers: the back ones darker.
        ring(sk, head, p.n, p.kind, length, half, shade, shade.shade(0.7), start=d * 0.1, offset=90, keep=keep, far_shade=False)
        ring(sk, head, p.n, p.kind, length * 0.93, half, petal, shade, start=d * 0.1, offset=90 - 180 / p.n, keep=keep, alt=p.alt)
        disc(sk, head, d, centre, 0.2)
        if p.stamens > 0 and look.kind != "spent":
            dots(sk, head, p.stamens, d * 1.5, 0.9 * size, centre, 0.3)


def sketch(p: Plant, look: Look, dark: bool = False, soil: Soil = MEADOW_SOIL) -> Sketch:
    """[p] as flat shapes, by day or with [dark] by night, in a box WIDTH by HEIGHT with its foot at BASE."""
    soil = soil.night() if dark else soil
    p = p.night() if dark else p
    sk = Sketch()
    sk.part = "soil"
    sk.ellipse((45, 107.5), 25, 4.6, soil.ground)
    sk.part = "fallen"
    if p.shed:
        if look.kind == "open" and look.vigour < SHEDS_BELOW:
            fallen(sk, FALLEN[:2], p.petal)
        elif look.kind == "spent":
            fallen(sk, FALLEN[:p.shed], p.petal.mix(p.petal.dried(), 0.25))
    flower(sk, p, soil, look)
    return sk
