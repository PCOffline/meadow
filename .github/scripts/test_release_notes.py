import os
import subprocess
import tempfile
import unittest
from unittest import mock

from pr_pictures import END, START
from release_notes import (
    FOR_PEOPLE_END,
    FOR_PEOPLE_START,
    Change,
    Picture,
    ask_claude,
    by_type,
    changes_since,
    checked,
    pictures_in,
    render,
    without_pictures,
)

URL = "https://raw.githubusercontent.com/o/r/refs/heads/pictures/pr-7/1"
DESCRIPTION = f"""## Summary
The garden wakes.

{START}
## Pictures
| Step | Before | After |
|---|---|---|
| 1. Open the garden | <img src="{URL}/before/stories/garden/01.png" width="300" alt="Open the garden"> | <img src="{URL}/after/stories/garden/01.png" width="300" alt="Open the garden"> |
| 2. Water a plant | | <img src="{URL}/after/stories/garden/02.png" width="300" alt="Water a plant"> |
<img src="{URL}/after/stories/garden/02.png" width="300" alt="Water a plant">
{END}

Closes #3
"""


class PicturesTest(unittest.TestCase):
    def test_only_the_after_pictures_count_once_each_in_order(self):
        self.assertEqual(
            [
                Picture(f"{URL}/after/stories/garden/01.png", "Open the garden"),
                Picture(f"{URL}/after/stories/garden/02.png", "Water a plant"),
            ],
            pictures_in(DESCRIPTION),
        )

    def test_a_description_without_pictures_has_none(self):
        self.assertEqual([], pictures_in('<img src="https://x/after/a.png" alt="a">'))

    def test_the_pictures_part_is_left_out_of_the_description(self):
        self.assertEqual("## Summary\nThe garden wakes.\n\n\n\nCloses #3", without_pictures(DESCRIPTION))
        self.assertEqual("Plain", without_pictures("Plain\n"))


class ChangeTest(unittest.TestCase):
    def test_the_summary_drops_the_type_and_the_pull_request(self):
        change = Change("feat(chat)!: open at what's new (#12)", "")
        self.assertEqual("feat", change.kind)
        self.assertEqual("Open at what's new", change.summary)
        self.assertEqual("Rest screen: read once", Change("Rest screen: read once (#4)", "").summary)

    def test_the_pull_request_title_wins_over_the_commit_subject(self):
        change = Change("fix: old words (#5)", "", 5, title="feat(app): new words")
        self.assertEqual(("feat", "New words"), (change.kind, change.summary))


class NotesTest(unittest.TestCase):
    def changes(self):
        return [
            Change("feat(app): water the garden (#7)", "", 7, pictures=pictures_in(DESCRIPTION)),
            Change("fix: a plant that didn't grow (#8)", "", 8),
            Change("perf: open faster (#9)", "", 9),
            Change("test: cover the garden (#10)", "", 10),
            Change("chore: tidy", "", None),
        ]

    def test_without_claude_the_notes_go_by_type_and_a_feature_with_pictures_is_a_highlight(self):
        notes = by_type(self.changes())
        self.assertEqual([{"text": "Water the garden", "changes": [7]}], notes["new"])
        self.assertEqual([{"text": "Open faster", "changes": [9]}], notes["improved"])
        self.assertEqual([{"text": "A plant that didn't grow", "changes": [8]}], notes["fixed"])
        self.assertEqual([7], [h["change"] for h in notes["highlights"]])

    def test_only_real_pull_requests_and_their_own_pictures_are_kept(self):
        notes = checked(
            {
                "overview": "",
                "highlights": [
                    {"title": "A", "text": "", "change": 7, "pictures": [f"{URL}/after/stories/garden/02.png", "https://elsewhere/x.png"]},
                    {"title": "B", "text": "", "change": 99, "pictures": []},
                ],
                "new": [{"text": "A", "changes": [7, 99]}],
                "improved": [],
                "fixed": [],
            },
            self.changes(),
        )
        self.assertEqual(["A"], [h["title"] for h in notes["highlights"]])
        self.assertEqual([Picture(f"{URL}/after/stories/garden/02.png", "Water a plant")], notes["highlights"][0]["pictures"])
        self.assertEqual([7], notes["new"][0]["changes"])

    def test_the_part_for_people_comes_first_then_the_highlights_then_every_change(self):
        changes = self.changes()
        notes = checked(by_type(changes) | {"overview": "The garden wakes."}, changes)
        text = render(notes, changes, "o/r", "v0.4.0", "v0.5.0")
        people = text[text.index(FOR_PEOPLE_START) : text.index(FOR_PEOPLE_END)]
        self.assertIn("The garden wakes.\n\n### New\n- Water the garden (#7)\n\n### Improved\n- Open faster (#9)", people)
        self.assertIn("### Fixed\n- A plant that didn't grow (#8)", people)
        self.assertNotIn("cover the garden", people)
        self.assertIn(f'## Highlights\n\n### Water the garden\n\n<img src="{URL}/after/stories/garden/01.png" width="300" alt="Open the garden">', text)
        self.assertIn("## All changes\n\n- feat(app): water the garden (#7)\n", text)
        self.assertIn("- test: cover the garden (#10)\n- chore: tidy\n", text)
        self.assertTrue(text.endswith("https://github.com/o/r/compare/v0.4.0...v0.5.0\n"))

    def test_a_release_with_nothing_for_people_still_says_something(self):
        changes = [Change("test: cover it (#1)", "", 1)]
        text = render(checked(by_type(changes), changes), changes, "o/r", None, "v0.1.0")
        self.assertIn(f"{FOR_PEOPLE_START}\nSmall changes that keep Meadow running well.\n\n{FOR_PEOPLE_END}", text)
        self.assertNotIn("Highlights", text)
        self.assertNotIn("Full comparison", text)

    def test_without_a_key_claude_isnt_asked(self):
        with mock.patch.dict(os.environ, {"ANTHROPIC_API_KEY": ""}):
            self.assertIsNone(ask_claude(self.changes(), "0.5.0"))


class ChangesSinceTest(unittest.TestCase):
    def setUp(self):
        self.old = os.getcwd()
        self.dir = tempfile.TemporaryDirectory()
        os.chdir(self.dir.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")

    def tearDown(self):
        os.chdir(self.old)
        self.dir.cleanup()

    def git(self, *args):
        return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, path, message):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with open(path, "a") as f:
            f.write("x\n")
        self.git("add", "-A")
        self.git("commit", "-q", "-m", message)
        return self.git("rev-parse", "HEAD")

    def test_only_the_app_commits_after_the_last_tag_count_oldest_first(self):
        self.commit("app/a.kt", "feat: first (#1)")
        self.git("tag", "v0.1.0")
        self.commit("app/a.kt", "fix: second (#2)\n\nThe body.")
        self.commit("backend/f.ts", "feat(backend): not in the app (#3)")
        sha = self.commit("legal/terms.md", "docs(legal): new terms")
        changes = changes_since("v0.1.0", sha)
        self.assertEqual(["fix: second (#2)", "docs(legal): new terms"], [c.subject for c in changes])
        self.assertEqual([2, None], [c.number for c in changes])
        self.assertEqual("fix: second (#2)\n\nThe body.", changes[0].message)
        self.assertEqual(3, len(changes_since(None, sha)))


if __name__ == "__main__":
    unittest.main()
