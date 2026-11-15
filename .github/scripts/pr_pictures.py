"""Turns the Pictures workflow's two emulator runs into the pull request's pictures comment.

Usage: pr_pictures.py <before dir> <after dir> <out dir> <url of out dir> [<url to browse it>]
       pr_pictures.py body <description file> <pictures.md>   (prints the new description)

<before dir> and <after dir> hold what the phone tests wrote (GalleryPictures and the stories),
for the pull request's base and for the pull request. This writes, under <out dir>:
- the pictures the comment shows, made smaller, and each story's video as an animated WebP,
- pictures.md: each story that is new or changed, step by step, then each screen that changed,
  before and after, grouped by theme (day and night) and by the large-text and narrow looks.
The workflow publishes <out dir> at <url of out dir>, and puts pictures.md in the pull request's
description (with_pictures). Needs Pillow, and ffmpeg for the videos.
"""
import json
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

START = "<!-- pictures: start. The Pictures workflow writes this part. -->"
END = "<!-- pictures: end -->"
WIDTH = 300
# A pixel counts as changed when a colour channel moves by more than this, and a picture counts as
# changed when more than this share of its pixels did. A clock that ticks over moves fewer.
PIXEL_TOLERANCE = 24
CHANGED_SHARE = 0.001
# GitHub refuses descriptions over 65,536 characters, and the author's text needs room too.
MAX_ROWS = 120
MAX_CHARS = 40_000


@dataclass
class Gallery:
    """The gallery pictures, by path under gallery/, sorted out by what happened to them."""

    changed: list[str] = field(default_factory=list)
    added: list[str] = field(default_factory=list)
    removed: list[str] = field(default_factory=list)
    unchanged: int = 0
    # From each run's gallery/index.tsv: a path's screen, state and look, in the order drawn.
    names: dict[str, tuple[str, str]] = field(default_factory=dict)
    # The group a path's look belongs to, such as a theme: "Meadow" for its day and its night.
    groups: dict[str, str] = field(default_factory=dict)
    # Whether the base drew a gallery at all. Without one, there's nothing to compare.
    base: bool = True

    def label(self, path: str) -> tuple[str, str]:
        return self.names.get(path) or label(path)

    def group(self, path: str) -> str:
        return self.groups.get(path) or self.label(path)[1]


@dataclass
class Step:
    caption: str
    before: str | None
    after: str | None


@dataclass
class StoryPictures:
    name: str
    title: str
    steps: list[Step]
    video: str | None
    new: bool
    # Whether any step's picture or caption differs from the base's. A new story counts as changed.
    changed: bool = True


def compare(before: set[str], after: set[str], differs, order: list[str] | None = None) -> Gallery:
    """
    Sorts the pictures in [before] and [after]; [differs] says whether a picture in both changed.
    Each list follows [order] (the order drawn), then the paths' own order.
    """
    rank = {path: i for i, path in enumerate(order or [])}

    def ordered(paths: set[str]) -> list[str]:
        return sorted(paths, key=lambda p: (rank.get(p, len(rank)), p))

    gallery = Gallery(added=ordered(after - before), removed=ordered(before - after), base=bool(before))
    for path in ordered(before & after):
        if differs(path):
            gallery.changed.append(path)
        else:
            gallery.unchanged += 1
    return gallery


def label(path: str) -> tuple[str, str]:
    """("Garden: nothing planted yet", "dark") for "dark/garden--nothing-planted-yet.png"."""
    look, _, file = path.partition("/")
    screen, _, state = Path(file).stem.partition("--")
    return f"{screen.replace('-', ' ').capitalize()}: {state.replace('-', ' ')}", look


def image(url: str, alt: str) -> str:
    return f'<img src="{url}" width="{WIDTH}" alt="{alt}">'


def story_section(story: StoryPictures, url: str) -> list[str]:
    lines = [f"#### {story.title}", ""]
    if story.video:
        lines += [image(f"{url}/{story.video}", f"{story.title}, as a video"), ""]
    if story.new:
        lines += ["| Step | Picture |", "|---|---|"]
        for i, step in enumerate(story.steps, 1):
            lines.append(f"| {i}. {step.caption} | {image(f'{url}/{step.after}', step.caption)} |")
    else:
        lines += ["| Step | Before | After |", "|---|---|---|"]
        for i, step in enumerate(story.steps, 1):
            before = image(f"{url}/{step.before}", f"Before: {step.caption}") if step.before else "None"
            after = image(f"{url}/{step.after}", step.caption) if step.after else "None"
            lines.append(f"| {i}. {step.caption} | {before} | {after} |")
    return lines + [""]


def section(stories: list[StoryPictures], gallery: Gallery, url: str, browse: str | None = None) -> str:
    """The pictures, in Markdown, cut to fit in a pull request's description."""
    lines = ["## Pictures", ""]
    shown_stories = [story for story in stories if story.new or story.changed]
    same = [story.title for story in stories if not (story.new or story.changed)]
    if shown_stories:
        lines += ["### Stories", ""]
        for story in shown_stories:
            lines += story_section(story, url)
    else:
        lines += [
            "No new or changed story. A change that people see or do needs one: read **Pull requests** in AGENTS.md.",
            "",
        ]
    if same:
        lines += [f"The same as on the base: {', '.join(same)}.", ""]
    lines += ["### Screens", ""]
    if not gallery.base and gallery.added:
        where = f"[in the pictures' folder]({browse}/after/gallery)" if browse else "in the workflow run's artifact"
        lines.append(
            f"The base has no gallery pictures, so there's nothing to compare. All {len(gallery.added)} are {where}."
        )
        return "\n".join(lines)
    # Each group (a theme's day and night, large text, narrow) in a table of its own, in the order drawn.
    groups: dict[str, list[str]] = {}
    for path in gallery.changed:
        name, look = gallery.label(path)
        before, after = (image(f"{url}/{side}/gallery/{path}", f"{side}: {name}, {look}") for side in ("before", "after"))
        groups.setdefault(gallery.group(path), []).append(f"| {name} | {look} | {before} | {after} |")
    for path in gallery.added:
        name, look = gallery.label(path)
        groups.setdefault(gallery.group(path), []).append(
            f"| {name} | {look} | New | {image(f'{url}/after/gallery/{path}', f'{name}, {look}')} |"
        )
    total = sum(len(rows) for rows in groups.values())
    if total:
        lines += [
            (
                f"{len(gallery.changed)} changed, {len(gallery.added)} new, {len(gallery.removed)} gone, "
                f"{gallery.unchanged} the same."
            ),
        ]
        shown = 0
        size = len("\n".join(lines))
        for group, rows in groups.items():
            head = ["", f"#### {group}", "", "| Screen | Look | Before | After |", "|---|---|---|---|"]
            if shown >= MAX_ROWS or size + len("\n".join(head)) + len(rows[0]) + 2 > MAX_CHARS:
                break
            lines += head
            size += len("\n".join(head)) + 1
            for row in rows:
                if shown >= MAX_ROWS or size + len(row) + 1 > MAX_CHARS:
                    break
                lines.append(row)
                size += len(row) + 1
                shown += 1
        left = total - shown
        if left > 0:
            lines += ["", f"{left} more aren't shown here. The workflow run's artifact has all of them."]
    else:
        lines.append(f"No screen in the gallery changed ({gallery.unchanged} the same).")
    if gallery.removed:
        lines += ["", "Gone from the gallery: " + ", ".join("{} ({})".format(*gallery.label(p)) for p in gallery.removed) + "."]
    return "\n".join(lines)


def with_pictures(body: str | None, pictures: str) -> str:
    """[body] with [pictures] between the markers: in place of the last ones, or at the end."""
    body = body or ""
    block = f"{START}\n{pictures}\n{END}"
    start, end = body.find(START), body.find(END)
    if start != -1 and end > start:
        return body[:start] + block + body[end + len(END):]
    return f"{body.rstrip()}\n\n{block}\n" if body.strip() else f"{block}\n"


def names(root: Path) -> list[tuple[str, tuple[str, str], str]]:
    """
    A run's gallery/index.tsv: each path with its "Screen: state" and look, and the look's group,
    in the order drawn. An index without groups (an older base) groups each look on its own.
    """
    index = root / "gallery" / "index.tsv"
    if not index.exists():
        return []
    found = []
    for line in index.read_text().splitlines():
        path, screen, state, look, *group = line.split("\t")
        found.append((path, (f"{screen}: {state[:1].lower()}{state[1:]}", look), group[0] if group else look))
    return found


def pictures(root: Path, sub: str) -> set[str]:
    base = root / sub
    return {str(p.relative_to(base)) for p in base.rglob("*.png")} if base.is_dir() else set()


def differs(before: Path, after: Path) -> bool:
    from PIL import Image, ImageChops

    with Image.open(before) as a, Image.open(after) as b:
        if a.size != b.size:
            return True
        diff = ImageChops.difference(a.convert("RGB"), b.convert("RGB")).convert("L")
        moved = sum(diff.point(lambda v: 255 if v > PIXEL_TOLERANCE else 0).histogram()[255:])
        return moved > CHANGED_SHARE * a.width * a.height


def smaller(source: Path, target: Path) -> None:
    from PIL import Image

    target.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(source) as picture:
        height = round(picture.height * WIDTH * 2 / picture.width)
        picture.convert("RGB").resize((WIDTH * 2, height), Image.LANCZOS).save(target, optimize=True)


def animation(video: Path, target: Path) -> bool:
    """The video as a looping animated WebP. False when ffmpeg can't make one."""
    target.parent.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(video), "-vf", f"fps=10,scale={WIDTH * 2}:-2",
         "-loop", "0", "-quality", "70", str(target)],
        check=False,
    )
    return result.returncode == 0 and target.exists()


def stories(before: Path, after: Path, out: Path) -> list[StoryPictures]:
    found = []
    for meta in sorted((after / "stories").glob("*/story.json")):
        name = meta.parent.name
        story = json.loads(meta.read_text())
        old = before / "stories" / name
        old_story = json.loads((old / "story.json").read_text()) if (old / "story.json").exists() else None
        old_count = len(old_story["steps"]) if old_story else 0
        changed = old_story is None or old_story["steps"] != story["steps"] or any(
            not (old / f"{i:02d}.png").exists() or differs(old / f"{i:02d}.png", meta.parent / f"{i:02d}.png")
            for i in range(1, len(story["steps"]) + 1)
        )
        if not changed:
            # The same as on the base: named, but with no pictures to publish.
            found.append(StoryPictures(name, story["title"], [], None, new=False, changed=False))
            continue
        steps = []
        for i, caption in enumerate(story["steps"], 1):
            shot = f"{i:02d}.png"
            smaller(meta.parent / shot, out / "after" / "stories" / name / shot)
            before_shot = None
            if i <= old_count and (old / shot).exists():
                smaller(old / shot, out / "before" / "stories" / name / shot)
                before_shot = f"before/stories/{name}/{shot}"
            steps.append(Step(caption, before_shot, f"after/stories/{name}/{shot}"))
        video = None
        if (meta.parent / "story.mp4").exists():
            webp = f"after/stories/{name}/story.webp"
            if animation(meta.parent / "story.mp4", out / webp):
                video = webp
        found.append(StoryPictures(name, story["title"], steps, video, new=old_story is None))
    return found


def main(before: Path, after: Path, out: Path, url: str, browse: str | None = None) -> None:
    out.mkdir(parents=True, exist_ok=True)
    named = names(after) + names(before)
    gallery = compare(
        pictures(before, "gallery"), pictures(after, "gallery"),
        lambda p: differs(before / "gallery" / p, after / "gallery" / p),
        order=[path for path, _, _ in named],
    )
    gallery.names = {path: name for path, name, _ in reversed(named)}
    gallery.groups = {path: group for path, _, group in reversed(named)}
    for path in gallery.changed:
        smaller(before / "gallery" / path, out / "before" / "gallery" / path)
    # Without a base, the folder keeps every picture at full size, and the description shows none.
    for path in gallery.changed + (gallery.added if gallery.base else []):
        smaller(after / "gallery" / path, out / "after" / "gallery" / path)
    if not gallery.base:
        for path in gallery.added:
            (out / "after" / "gallery" / path).parent.mkdir(parents=True, exist_ok=True)
            (out / "after" / "gallery" / path).write_bytes((after / "gallery" / path).read_bytes())
    text = section(stories(before, after, out), gallery, url.rstrip("/"), browse and browse.rstrip("/"))
    (out / "pictures.md").write_text(text)
    print(f"{len(gallery.changed)} changed, {len(gallery.added)} new, {len(gallery.removed)} gone, {gallery.unchanged} the same")


if __name__ == "__main__":
    if sys.argv[1] == "body":
        print(with_pictures(Path(sys.argv[2]).read_text(), Path(sys.argv[3]).read_text()), end="")
    else:
        main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4], (sys.argv[5:] or [None])[0])
