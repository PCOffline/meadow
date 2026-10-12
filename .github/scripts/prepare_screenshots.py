"""Turns the raw emulator captures from ShowcaseTest into README images.

Usage: prepare_screenshots.py <raw dir> <docs/images dir>
Screenshots become rounded, 540px-wide PNGs; the theme shots are joined into one strip; screen
recordings become looping, full-colour animated WebPs (needs ffmpeg).
"""
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageDraw

WIDTH = 540
RADIUS = 44


def rounded(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    height = round(image.height * WIDTH / image.width)
    image = image.resize((WIDTH, height), Image.LANCZOS)
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1), RADIUS, fill=255)
    image.putalpha(mask)
    return image


def strip(images: list[Image.Image], gap: int = 28) -> Image.Image:
    width = sum(i.width for i in images) + gap * (len(images) - 1)
    out = Image.new("RGBA", (width, max(i.height for i in images)), (0, 0, 0, 0))
    x = 0
    for image in images:
        out.paste(image, (x, 0), image)
        x += image.width + gap
    return out


def animation(video: Path, target: Path, fps: int = 24) -> None:
    """A screen recording as a full-colour, looping animated WebP with rounded corners."""
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-vf", f"fps={fps}", f"{tmp}/%04d.png"],
            check=True,
        )
        frames = [rounded(Image.open(p)) for p in sorted(Path(tmp).glob("*.png"))]
    frames[0].save(
        target, save_all=True, append_images=frames[1:], duration=round(1000 / fps), loop=0,
        quality=82, method=6,
    )


def main(raw: Path, out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    shots = {p.stem: rounded(Image.open(p)) for p in sorted(raw.glob("*.png"))}
    for name, image in shots.items():
        if not name.startswith("theme-"):
            image.save(out / f"screen-{name}.png", optimize=True)
    themes = [shots[k] for k in sorted(shots) if k.startswith("theme-")]
    if themes:
        strip(themes).save(out / "themes.png", optimize=True)
    for video in sorted(raw.glob("*.mp4")):
        animation(video, out / f"{video.stem}.webp")
    print("\n".join(sorted(p.name for p in out.iterdir())))


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
