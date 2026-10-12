"""Turns the raw emulator captures from ShowcaseTest into README images.

Usage: prepare_screenshots.py <raw dir> <docs/images dir>
Screenshots become rounded, 540px-wide PNGs; the theme shots are joined into one strip; screen
recordings become looping GIFs (needs ffmpeg).
"""
import subprocess
import sys
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


def gif(video: Path, target: Path) -> None:
    filters = (
        "fps=15,scale=360:-1:flags=lanczos,split[a][b];"
        "[a]palettegen=max_colors=160:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle"
    )
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-vf", filters, "-loop", "0", str(target)], check=True)


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
        gif(video, out / f"{video.stem}.gif")
    print("\n".join(sorted(p.name for p in out.iterdir())))


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
