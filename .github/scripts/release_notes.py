"""Writes the notes of an app release, for the Deliver workflow.

    python3 release_notes.py <tag>        (prints the notes as Markdown)

Environment: GITHUB_REPOSITORY, SHA (the released commit; HEAD when empty), GH_TOKEN (to read the
pull requests with `gh`), ANTHROPIC_API_KEY (optional), RELEASE_NOTES_MODEL (optional).

The notes cover each commit since the latest app tag that changed the app (the paths in
plan_version.py). Each commit is a squashed pull request, so this reads the pull request too: its
description and the pictures that the Pictures workflow put in it.

With ANTHROPIC_API_KEY, Claude writes the notes: an overview, the highlights with their pictures,
and what is new, improved and fixed. Without it, or when the call fails, the notes list each
change by its Conventional Commits type. The release never waits for, or fails on, the notes.

The notes have three parts:
- the part for people, between FOR_PEOPLE_START and FOR_PEOPLE_END. The app shows it
  (Releases.notesForPeople in app/src/main/java/dev/pcoffline/meadow/update/Releases.kt);
- the highlights, each with a picture from its pull request;
- every change, with its pull request, and a link to the full comparison.
"""
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field

from plan_version import COMPONENTS, git, latest
from pr_pictures import END as PICTURES_END
from pr_pictures import START as PICTURES_START

FOR_PEOPLE_START = "<!-- for people: start -->"
FOR_PEOPLE_END = "<!-- for people: end -->"
MODEL = "claude-opus-5-5"
PICTURE_WIDTH = 300
MAX_PICTURES = 3

# "feat(chat)!: open at what's new (#12)": the type, and the summary without the pull request.
SUBJECT = re.compile(r"^(?P<type>[a-z]+)(\([^)\n]*\))?!?:\s+(?P<summary>.+?)(\s+\(#\d+\))*$", re.IGNORECASE)
PULL_REQUEST = re.compile(r"\(#(\d+)\)\s*$")
# The Pictures workflow writes each picture as <img src="..." width="300" alt="...">.
IMAGE = re.compile(r'<img\s+src="(?P<url>[^"]+)"[^>]*?alt="(?P<alt>[^"]*)"')

KINDS = {"feat": "new", "perf": "improved", "fix": "fixed"}
SECTIONS = (("new", "New"), ("improved", "Improved"), ("fixed", "Fixed"))


@dataclass
class Picture:
    url: str
    caption: str


@dataclass
class Change:
    subject: str
    message: str
    number: int | None = None
    title: str = ""
    description: str = ""
    pictures: list[Picture] = field(default_factory=list)

    @property
    def kind(self) -> str:
        match = SUBJECT.match(self.title or self.subject)
        return match["type"].lower() if match else ""

    @property
    def summary(self) -> str:
        """The subject as people read it: no type, no pull request, a capital first letter."""
        line = self.title or self.subject
        match = SUBJECT.match(line)
        text = match["summary"] if match else PULL_REQUEST.sub("", line).strip()
        return text[:1].upper() + text[1:]


def changes_since(previous: str | None, sha: str) -> list[Change]:
    """The commits after [previous] up to [sha] that changed the app, oldest first."""
    since = f"{previous}..{sha}" if previous else sha
    log = git("log", "--reverse", "--format=%B%x00", since, "--", *COMPONENTS["app"].paths)
    changes = []
    for message in filter(None, (m.strip() for m in log.split("\0"))):
        subject = message.splitlines()[0]
        number = PULL_REQUEST.search(subject)
        changes.append(Change(subject, message, int(number[1]) if number else None))
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


def without_pictures(description: str) -> str:
    start = description.find(PICTURES_START)
    if start < 0:
        return description.strip()
    end = description.find(PICTURES_END, start)
    rest = description[end + len(PICTURES_END) :] if end >= 0 else ""
    return (description[:start] + rest).strip()


def read_pull_request(repo: str, change: Change) -> None:
    """Adds the title, description and pictures of [change]'s pull request, when it has one."""
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
    change.description = without_pictures(body)
    change.pictures = pictures_in(body)


# What Claude returns. Structured outputs keep it to this shape.
SCHEMA = {
    "type": "object",
    "properties": {
        "overview": {"type": "string"},
        "highlights": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "title": {"type": "string"},
                    "text": {"type": "string"},
                    "change": {"type": "integer"},
                    "pictures": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["title", "text", "change", "pictures"],
                "additionalProperties": False,
            },
        },
        **{
            key: {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {"text": {"type": "string"}, "changes": {"type": "array", "items": {"type": "integer"}}},
                    "required": ["text", "changes"],
                    "additionalProperties": False,
                },
            }
            for key, _ in SECTIONS
        },
    },
    "required": ["overview", "highlights", *(key for key, _ in SECTIONS)],
    "additionalProperties": False,
}

SYSTEM = """You write the release notes of Meadow, an Android app that helps people keep their \
phone use calm. People read the notes before they update: in the app, and on the GitHub release.

You get each change in the release: its pull request number, title, description, commit message \
and pictures. Write:
- overview: two to four sentences that say what this release brings, as a whole.
- highlights: the changes that people will notice most. Usually one to three; none when the \
release has only small fixes. Each has a short title, two to four sentences of text, the pull \
request number in "change", and up to three picture URLs from that change's own pictures, in the \
order that tells the change best. Choose pictures that show the change itself.
- new: each new feature, one line each.
- improved: each change that makes something that exists work better or faster, one line each.
- fixed: each bug fix, one line each, said as what works now.
Each line lists the pull request numbers that it comes from in "changes". Put each change in one \
list only. Leave out changes that people can't see or feel: tests, refactors, builds, docs, CI and \
dependency updates. A highlight can also have a line in its list.

Write as Meadow's app text: warm, calm and plain. Use ASD-STE100 Simplified Technical English: \
short sentences, the active voice, simple tenses and common words. Describe what people see and \
do. Leave out code, file names, services, error codes and how a feature works. Use sentence case, \
no emojis, no dashes, no sales words and no exclamation marks. Write "you" for the person who \
reads. Use only facts from the changes."""


def ask_claude(changes: list[Change], version: str) -> dict | None:
    """Claude's notes, or None when there's no key, no SDK, or the call fails."""
    if not os.environ.get("ANTHROPIC_API_KEY"):
        print("::notice::No ANTHROPIC_API_KEY, so the notes list the changes by their type", file=sys.stderr)
        return None
    try:
        import anthropic
    except ImportError:
        print("::warning::The anthropic package isn't installed, so the notes list the changes by their type", file=sys.stderr)
        return None
    request = [
        {
            "change": c.number,
            "title": c.title or c.subject,
            "description": c.description,
            "commit message": c.message,
            "pictures": [{"url": p.url, "caption": p.caption} for p in c.pictures],
        }
        for c in changes
    ]
    try:
        response = anthropic.Anthropic().messages.create(
            model=os.environ.get("RELEASE_NOTES_MODEL") or MODEL,
            max_tokens=16000,
            system=SYSTEM,
            messages=[{"role": "user", "content": f"Meadow {version}. The changes:\n\n{json.dumps(request, indent=1)}"}],
            output_config={"format": {"type": "json_schema", "schema": SCHEMA}},
        )
    except anthropic.APIError as e:
        print(f"::warning::Claude couldn't write the notes ({e}), so they list the changes by their type", file=sys.stderr)
        return None
    if response.stop_reason != "end_turn":
        print(f"::warning::Claude stopped ({response.stop_reason}), so the notes list the changes by their type", file=sys.stderr)
        return None
    text = next((b.text for b in response.content if b.type == "text"), "")
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        print("::warning::Claude's notes weren't JSON, so they list the changes by their type", file=sys.stderr)
        return None


def by_type(changes: list[Change]) -> dict:
    """Notes without Claude: features, improvements and fixes by their type. Each feature with
    pictures is a highlight."""
    notes: dict = {"overview": "", "highlights": [], **{key: [] for key, _ in SECTIONS}}
    for c in changes:
        if (section := KINDS.get(c.kind)) is None:
            continue
        notes[section].append({"text": c.summary, "changes": [c.number] if c.number else []})
        if section == "new" and c.pictures and c.number:
            notes["highlights"].append({"title": c.summary, "text": "", "change": c.number, "pictures": [c.pictures[0].url]})
    return notes


def checked(notes: dict, changes: list[Change]) -> dict:
    """[notes] with only real pull requests, and only pictures from the highlight's own one."""
    by_number = {c.number: c for c in changes if c.number}
    for key, _ in SECTIONS:
        for line in notes.get(key, []):
            line["changes"] = [n for n in line.get("changes", []) if n in by_number]
    highlights = []
    for h in notes.get("highlights", []):
        change = by_number.get(h.get("change"))
        if change is None:
            continue
        own = {p.url: p for p in change.pictures}
        h["pictures"] = [own[url] for url in dict.fromkeys(h.get("pictures", [])) if url in own][:MAX_PICTURES]
        highlights.append(h)
    notes["highlights"] = highlights
    return notes


def links(numbers: list[int]) -> str:
    return "".join(f" (#{n})" for n in numbers)


def render(notes: dict, changes: list[Change], repo: str, previous: str | None, tag: str) -> str:
    lines = [FOR_PEOPLE_START]
    if overview := notes.get("overview", "").strip():
        lines += [overview, ""]
    for key, heading in SECTIONS:
        if items := notes.get(key):
            lines += [f"### {heading}", *(f"- {i['text'].strip()}{links(i['changes'])}" for i in items), ""]
    if lines == [FOR_PEOPLE_START]:
        lines += ["Small changes that keep Meadow running well.", ""]
    lines += [FOR_PEOPLE_END, ""]

    if notes.get("highlights"):
        lines += ["## Highlights", ""]
        for h in notes["highlights"]:
            lines += [f"### {h['title'].strip()}", ""]
            if text := h.get("text", "").strip():
                lines += [f"{text} (#{h['change']})", ""]
            if h["pictures"]:
                lines += [
                    " ".join(f'<img src="{p.url}" width="{PICTURE_WIDTH}" alt="{p.caption}">' for p in h["pictures"]),
                    "",
                ]

    lines += ["## All changes", "", *(f"- {c.title or c.subject}" + ("" if c.number is None or c.title == "" else f" (#{c.number})") for c in changes), ""]
    if previous:
        lines.append(f"**Full comparison**: https://github.com/{repo}/compare/{previous}...{tag}")
    return "\n".join(lines).rstrip() + "\n"


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit(f"Usage: {sys.argv[0]} <tag>")
    tag = sys.argv[1]
    repo = os.environ["GITHUB_REPOSITORY"]
    sha = os.environ.get("SHA") or git("rev-parse", "HEAD")
    last = latest(COMPONENTS["app"], exclude=tag)
    previous = last[0] if last else None
    changes = changes_since(previous, sha)
    for change in changes:
        read_pull_request(repo, change)
    notes = ask_claude(changes, tag.removeprefix("v")) or by_type(changes)
    sys.stdout.write(render(checked(notes, changes), changes, repo, previous, tag))


if __name__ == "__main__":
    main()
