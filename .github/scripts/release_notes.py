"""Writes and checks the notes of app releases. The authors of the pull requests write them.

    release_notes.py <tag>                 prints the notes of a new release, for Deliver
    release_notes.py check                 checks a pull request's release note, for CI
    release_notes.py overview <tag> <text> puts a new overview in a published release

Each pull request that changes the app has a "Release note" section in its description
(.github/pull_request_template.md, and "Release notes" in AGENTS.md):

    ## Release note
    Kind: new | improved | fixed | none
    Highlight: yes | no
    Note: one line that people read, up to NOTE_MAX characters. A highlight's title.
    Picture: for a highlight only, and optional: a story step's number, or video.
    Replaces: optional: the pull requests whose note this note replaces, such as #219.

`<tag>` reads each commit since the latest app tag that changed the app (the paths in
plan_version.py). Each is a squashed pull request, so it reads the pull request with `gh`: its
release note and the pictures that the Pictures workflow put in its description. A pull request
with no release note, such as one from a bot or from before this rule, counts by its
Conventional Commits type: feat is new, perf is improved, fix is fixed, and the rest is none.
Environment: GITHUB_REPOSITORY, SHA (the released commit; HEAD when empty), GH_TOKEN.

The notes have three parts:
- the part for people, between FOR_PEOPLE_START and FOR_PEOPLE_END: an overview, then what is
  new, improved and fixed. The app shows it (Releases.notesForPeople). A release with nothing
  for people leaves this part empty, so the app shows no notes;
- the highlights, at most MAX_HIGHLIGHTS, each with its note as the title and one picture;
- every change, with its pull request, and a link to the full comparison.

A note that replaces another pull request's note hides that note when both are in the release.
It changes nothing when the other pull request is in an earlier release.

Deliver writes an overview that counts the changes. The release-overview skill
(.claude/skills/release-overview/SKILL.md) can write a better one by hand, with `overview`.

`check` reads BODY (the description), AUTHOR (the pull request's author) and the changed files
on stdin. A pull request that doesn't change the app, or that a bot opened, needs no note.
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field

from plan_version import COMPONENTS, git, latest
from pr_pictures import END as PICTURES_END
from pr_pictures import START as PICTURES_START

FOR_PEOPLE_START = "<!-- for people: start -->"
FOR_PEOPLE_END = "<!-- for people: end -->"
NOTE_MAX = 100
OVERVIEW_MAX_WORDS = 40
MAX_HIGHLIGHTS = 3
PICTURE_WIDTH = 300

KINDS = ("new", "improved", "fixed", "none")
SECTIONS = (("new", "New"), ("improved", "Improved"), ("fixed", "Fixed"))
# How the overview counts each kind: one, many.
COUNTED = {"new": ("new feature", "new features"), "improved": ("improvement", "improvements"), "fixed": ("fix", "fixes")}
BY_TYPE = {"feat": "new", "perf": "improved", "fix": "fixed"}

# "feat(chat)!: open at what's new (#12)": the type, and the summary without the pull request.
SUBJECT = re.compile(r"^(?P<type>[a-z]+)(\([^)\n]*\))?!?:\s+(?P<summary>.+?)(\s+\(#\d+\))*$", re.IGNORECASE)
PULL_REQUEST = re.compile(r"\(#(\d+)\)\s*$")
# The Pictures workflow writes each picture as <img src="..." width="300" alt="...">.
IMAGE = re.compile(r'<img\s+src="(?P<url>[^"]+)"[^>]*?alt="(?P<alt>[^"]*)"')
COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
HEADING = re.compile(r"^##\s+release note\s*$", re.IGNORECASE | re.MULTILINE)
FIELD = re.compile(r"^(?P<name>kind|highlight|note|picture|replaces):[ \t]*(?P<value>.*)$", re.IGNORECASE)
NUMBERS = re.compile(r"^(#?\d+)([\s,]+#?\d+)*$")
STEP = re.compile(r"/stories/[^/]+/(\d+)\.png$")


@dataclass
class Note:
    kind: str
    highlight: str
    text: str
    picture: str = ""
    replaces: str = ""

    @property
    def replaced(self) -> list[int]:
        return [int(n) for n in re.findall(r"\d+", self.replaces)]


@dataclass
class Picture:
    url: str
    caption: str


@dataclass
class Change:
    subject: str
    number: int | None = None
    title: str = ""
    note: Note | None = None
    pictures: list[Picture] = field(default_factory=list)

    @property
    def kind(self) -> str:
        if self.note:
            return self.note.kind
        match = SUBJECT.match(self.title or self.subject)
        return BY_TYPE.get(match["type"].lower(), "none") if match else "none"

    @property
    def text(self) -> str:
        """What people read: the note, or else the summary of the title with a capital letter."""
        if self.note:
            return self.note.text
        line = self.title or self.subject
        match = SUBJECT.match(line)
        summary = match["summary"] if match else PULL_REQUEST.sub("", line).strip()
        return summary[:1].upper() + summary[1:]

    @property
    def highlight(self) -> bool:
        return bool(self.note and self.note.kind != "none" and self.note.highlight == "yes")

    @property
    def picture(self) -> Picture | None:
        """The highlight's picture: the one its note names, else the story's first step, else the
        first picture."""
        chosen = self.note.picture if self.note else ""
        steps = {int(m[1]): p for p in self.pictures if (m := STEP.search(p.url))}
        if chosen == "video" and (video := next((p for p in self.pictures if p.url.endswith("/story.webp")), None)):
            return video
        if chosen.isdigit() and int(chosen) in steps:
            return steps[int(chosen)]
        if steps:
            return steps[min(steps)]
        return self.pictures[0] if self.pictures else None


def parse_note(description: str) -> Note | None:
    """The release note in [description], or None when it has no "Release note" section."""
    text = COMMENT.sub("", description)
    heading = HEADING.search(text)
    if heading is None:
        return None
    section = text[heading.end() :]
    if (following := re.search(r"^##\s", section, re.MULTILINE)) is not None:
        section = section[: following.start()]
    fields = {}
    for line in section.splitlines():
        if (match := FIELD.match(line.strip())) is not None:
            fields[match["name"].lower()] = match["value"].strip()
    return Note(
        fields.get("kind", "").lower(),
        fields.get("highlight", "").lower(),
        fields.get("note", ""),
        fields.get("picture", "").lower(),
        fields.get("replaces", ""),
    )


def problems(note: Note | None) -> list[str]:
    """What is wrong with [note], in words that tell the author what to change."""
    if note is None:
        return ["Add a \"## Release note\" section to the description, as in .github/pull_request_template.md."]
    found = []
    if note.kind not in KINDS:
        found.append(f"Set Kind to one of: {', '.join(KINDS)}.")
    if note.highlight not in ("yes", "no"):
        found.append("Set Highlight to yes or no.")
    if note.kind == "none":
        if note.highlight == "yes" or note.text or note.picture or note.replaces:
            found.append("Leave Note, Picture and Replaces empty and set Highlight to no when Kind is none.")
        return found
    if not note.text:
        found.append("Write the Note: one line that says what people see or can do now.")
    elif len(note.text) > NOTE_MAX:
        found.append(f"Shorten the Note to {NOTE_MAX} characters or fewer. It has {len(note.text)}.")
    if note.picture and note.highlight != "yes":
        found.append("Leave Picture empty, or set Highlight to yes.")
    elif note.picture and note.picture != "video" and not note.picture.isdigit():
        found.append("Set Picture to the number of a story step, or to video.")
    if note.replaces and not NUMBERS.match(note.replaces):
        found.append("Write Replaces as pull request numbers, such as #219, #220.")
    return found


def changes_app(files: list[str]) -> bool:
    paths = COMPONENTS["app"].paths
    return any(f == p or (p.endswith("/") and f.startswith(p)) for f in files for p in paths)


def changes_since(previous: str | None, sha: str) -> list[Change]:
    """The commits after [previous] up to [sha] that changed the app, oldest first."""
    since = f"{previous}..{sha}" if previous else sha
    subjects = git("log", "--reverse", "--format=%s", since, "--", *COMPONENTS["app"].paths)
    changes = []
    for subject in filter(None, subjects.splitlines()):
        number = PULL_REQUEST.search(subject)
        changes.append(Change(subject, int(number[1]) if number else None))
    return changes


def pictures_in(description: str) -> list[Picture]:
    """The "after" pictures of a description's Pictures part: each story step and each screen."""
    start = description.find(PICTURES_START)
    if start < 0:
        return []
    end = description.find(PICTURES_END, start)
    part = description[start : end if end >= 0 else None]
    seen, pictures = set(), []
    for match in IMAGE.finditer(part):
        url = match["url"]
        if "/after/" in url and url not in seen:
            seen.add(url)
            pictures.append(Picture(url, match["alt"]))
    return pictures


def read_pull_request(repo: str, change: Change) -> None:
    """Adds the title, release note and pictures of [change]'s pull request, when it has one."""
    if change.number is None:
        return
    found = subprocess.run(
        ["gh", "api", f"repos/{repo}/pulls/{change.number}", "--jq", "{title, body}"],
        capture_output=True, text=True, check=False,
    )
    if found.returncode != 0:
        print(f"::warning::Couldn't read pull request #{change.number}: {found.stderr.strip()}", file=sys.stderr)
        return
    pull = json.loads(found.stdout)
    body = pull.get("body") or ""
    change.title = pull.get("title") or ""
    note = parse_note(body)
    # A note with problems got past the check (or came before it): the type decides instead.
    change.note = note if note is not None and not problems(note) else None
    change.pictures = pictures_in(body)


def counted(changes: list[Change]) -> str:
    """"This update has 2 new features and 1 fix.", or "" when nothing is for people."""
    parts = []
    for kind, (one, many) in COUNTED.items():
        if n := sum(c.kind == kind for c in changes):
            parts.append(f"{n} {one if n == 1 else many}")
    if not parts:
        return ""
    listed = parts[0] if len(parts) == 1 else ", ".join(parts[:-1]) + " and " + parts[-1]
    return f"This update has {listed}."


def for_people(changes: list[Change]) -> list[Change]:
    """[changes] without each one that a change in the same release replaces."""
    replaced = {n for c in changes if c.note for n in c.note.replaced if n != c.number}
    return [c for c in changes if c.number not in replaced]


def render(changes: list[Change], repo: str, previous: str | None, tag: str) -> str:
    shown = for_people(changes)
    lines = [FOR_PEOPLE_START]
    if overview := counted(shown):
        lines += [overview, ""]
        for kind, heading in SECTIONS:
            if items := [c for c in shown if c.kind == kind]:
                lines += [f"### {heading}", *(f"- {c.text}" + (f" (#{c.number})" if c.number else "") for c in items), ""]
    lines += [FOR_PEOPLE_END, ""]

    if highlights := [c for c in shown if c.highlight][:MAX_HIGHLIGHTS]:
        lines += ["## Highlights", ""]
        for c in highlights:
            lines += [f"### {c.text.rstrip('.')} (#{c.number})", ""]
            if picture := c.picture:
                lines += [f'<img src="{picture.url}" width="{PICTURE_WIDTH}" alt="{picture.caption}">', ""]

    lines += ["## All changes", ""]
    lines += [f"- {c.title} (#{c.number})" if c.title else f"- {c.subject}" for c in changes]
    lines.append("")
    if previous:
        lines.append(f"**Full comparison**: https://github.com/{repo}/compare/{previous}...{tag}")
    return "\n".join(lines).rstrip() + "\n"


def overview_problems(text: str) -> list[str]:
    found = []
    if not text.strip():
        found.append("Write the overview.")
    if "\n" in text.strip():
        found.append("Write the overview as one paragraph.")
    if (words := len(text.split())) > OVERVIEW_MAX_WORDS:
        found.append(f"Shorten the overview to {OVERVIEW_MAX_WORDS} words or fewer. It has {words}.")
    return found


def with_overview(body: str, text: str) -> str:
    """[body] with [text] as the overview of its part for people, in place of the one it has."""
    body = body.replace("\r\n", "\n")
    start = body.find(FOR_PEOPLE_START)
    end = body.find(FOR_PEOPLE_END, start + 1)
    if start < 0 or end < 0:
        raise ValueError("These notes have no part for people.")
    lines = body[start + len(FOR_PEOPLE_START) : end].strip().splitlines()
    first = next((i for i, line in enumerate(lines) if line.startswith("### ")), None)
    if first is None:
        raise ValueError("This release has nothing for people, so it has no overview.")
    part = "\n".join([text.strip(), "", *lines[first:]])
    return f"{body[:start]}{FOR_PEOPLE_START}\n{part}\n\n{body[end:]}"


def check() -> None:
    files = sys.stdin.read().split()
    if not changes_app(files):
        print("The app doesn't change, so this pull request needs no release note.")
        return
    if os.environ.get("AUTHOR", "").endswith("[bot]"):
        print("A bot opened this pull request, so its type decides its release note.")
        return
    if found := problems(parse_note(os.environ.get("BODY", ""))):
        for problem in found:
            print(f"::error::{problem}")
        raise SystemExit(1)
    print("The release note is good.")


def overview(tag: str, text: str) -> None:
    if found := overview_problems(text):
        raise SystemExit("\n".join(found))
    body = subprocess.run(["gh", "release", "view", tag, "--json", "body", "--jq", ".body"], capture_output=True, text=True, check=True).stdout
    try:
        notes = with_overview(body, text)
    except ValueError as e:
        raise SystemExit(str(e)) from e
    with tempfile.NamedTemporaryFile("w", suffix=".md") as f:
        f.write(notes)
        f.flush()
        subprocess.run(["gh", "release", "edit", tag, "--notes-file", f.name], check=True)
    sys.stdout.write(notes[notes.index(FOR_PEOPLE_START) : notes.index(FOR_PEOPLE_END) + len(FOR_PEOPLE_END)] + "\n")


def release(tag: str) -> None:
    repo = os.environ["GITHUB_REPOSITORY"]
    sha = os.environ.get("SHA") or git("rev-parse", "HEAD")
    last = latest(COMPONENTS["app"], exclude=tag)
    previous = last[0] if last else None
    changes = changes_since(previous, sha)
    for change in changes:
        read_pull_request(repo, change)
    sys.stdout.write(render(changes, repo, previous, tag))


def main() -> None:
    args = sys.argv[1:]
    if args == ["check"]:
        check()
    elif len(args) == 3 and args[0] == "overview":
        overview(args[1], args[2])
    elif len(args) == 1 and args[0] not in ("check", "overview"):
        release(args[0])
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
