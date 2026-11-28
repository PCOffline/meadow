import io
import os
import subprocess
import tempfile
import unittest
from unittest import mock

from pr_pictures import END, START
from release_notes import (
    FOR_PEOPLE_END,
    FOR_PEOPLE_START,
    NOTE_MAX,
    OVERVIEW_MAX_WORDS,
    Change,
    Note,
    Picture,
    changes_app,
    changes_since,
    check,
    counted,
    overview_problems,
    parse_note,
    pictures_in,
    problems,
    render,
    with_overview,
)

URL = "https://raw.githubusercontent.com/o/r/refs/heads/pictures/pr-7/1"
PICTURES = f"""{START}
## Pictures
| Step | Before | After |
|---|---|---|
| 1. Open the garden | <img src="{URL}/before/stories/garden/01.png" width="300" alt="Open the garden"> | <img src="{URL}/after/stories/garden/01.png" width="300" alt="Open the garden"> |
| 2. Water a plant | | <img src="{URL}/after/stories/garden/02.png" width="300" alt="Water a plant"> |
<img src="{URL}/after/stories/garden/02.png" width="300" alt="Water a plant">
{END}"""

TEMPLATE = """## Summary

The garden wakes.

## Release note

<!-- Kind: new, improved, fixed or none. -->
Kind: new
<!-- Highlight: yes or no. -->
Highlight: yes
Note: You can now water your garden from the rest screen.
Picture: 2
Replaces: #5, #6

## Evidence

Kind: fixed
"""


class NoteTest(unittest.TestCase):
    def test_the_fields_come_from_the_release_note_section_only(self):
        note = parse_note(TEMPLATE)
        self.assertEqual(Note("new", "yes", "You can now water your garden from the rest screen.", "2", "#5, #6"), note)
        self.assertEqual([5, 6], note.replaced)
        self.assertEqual([], problems(note))

    def test_the_template_comments_are_not_fields(self):
        self.assertEqual(Note("none", "no", ""), parse_note("## Release note\n<!--\nKind: new\n-->\nKind: None\nHighlight: No\nNote:\n"))

    def test_a_description_without_the_section_has_no_note(self):
        self.assertIsNone(parse_note("## Summary\nKind: new"))
        self.assertEqual(1, len(problems(None)))

    def test_each_rule_says_what_to_change(self):
        def says(note, words):
            self.assertTrue(any(words in p for p in problems(note)), problems(note))

        says(Note("feature", "no", "A"), "Set Kind")
        says(Note("new", "maybe", "A"), "Set Highlight")
        says(Note("new", "no", ""), "Write the Note")
        says(Note("new", "no", "x" * (NOTE_MAX + 1)), f"{NOTE_MAX} characters or fewer")
        says(Note("new", "no", "A", "2"), "Leave Picture empty")
        says(Note("new", "yes", "A", "last"), "number of a story step, or to video")
        says(Note("new", "no", "A", replaces="the limit one"), "pull request numbers")
        says(Note("none", "no", "A"), "Leave Note, Picture and Replaces empty")
        says(Note("none", "yes", ""), "Leave Note, Picture and Replaces empty")
        says(Note("none", "no", "", replaces="#2"), "Leave Note, Picture and Replaces empty")
        self.assertEqual([], problems(Note("none", "no", "")))
        self.assertEqual([], problems(Note("fixed", "no", "x" * NOTE_MAX, replaces="219 #220")))
        self.assertEqual([], problems(Note("new", "yes", "A", "video")))


class CheckTest(unittest.TestCase):
    def run_check(self, files, body="", author="sam"):
        with (
            mock.patch.dict(os.environ, {"BODY": body, "AUTHOR": author}),
            mock.patch("sys.stdin", io.StringIO("\n".join(files))),
            mock.patch("sys.stdout", io.StringIO()) as out,
        ):
            try:
                check()
            except SystemExit as e:
                return e.code, out.getvalue()
            return 0, out.getvalue()

    def test_a_change_to_the_app_needs_a_good_note(self):
        self.assertEqual(0, self.run_check(["app/src/main/A.kt"], TEMPLATE)[0])
        code, out = self.run_check(["app/src/main/A.kt"], "## Summary")
        self.assertEqual(1, code)
        self.assertIn("::error::Add a", out)

    def test_other_changes_and_bots_need_no_note(self):
        self.assertEqual(0, self.run_check(["docs/a.md", ".github/workflows/build.yml"])[0])
        self.assertEqual(0, self.run_check(["gradle/libs.versions.toml"], author="dependabot[bot]")[0])

    def test_the_app_paths_are_the_ones_that_release(self):
        self.assertTrue(changes_app(["legal/terms.md"]))
        self.assertTrue(changes_app(["build.gradle.kts"]))
        self.assertFalse(changes_app(["backend/functions/src/a.ts", "application.md"]))


class PicturesTest(unittest.TestCase):
    def test_only_the_after_pictures_count_once_each_in_order(self):
        self.assertEqual(
            [
                Picture(f"{URL}/after/stories/garden/01.png", "Open the garden"),
                Picture(f"{URL}/after/stories/garden/02.png", "Water a plant"),
            ],
            pictures_in(PICTURES),
        )

    def test_a_description_without_pictures_has_none(self):
        self.assertEqual([], pictures_in('<img src="https://x/after/a.png" alt="a">'))


class RenderTest(unittest.TestCase):
    def changes(self):
        return [
            Change("feat(app): water the garden (#7)", 7, "feat(app): water the garden", parse_note(TEMPLATE), pictures_in(PICTURES)),
            Change("fix: a plant that didn't grow (#8)", 8),
            Change("perf: open faster (#9)", 9, note=Note("improved", "no", "Meadow opens faster.")),
            Change("feat: cover the garden with tests (#10)", 10, note=Note("none", "no", "")),
            Change("chore: tidy", None),
            Change("build(deps): bump kotlin (#11)", 11),
        ]

    def test_the_note_wins_over_the_type_and_the_type_counts_without_a_note(self):
        kinds = [(c.kind, c.text) for c in self.changes()]
        self.assertEqual(
            [
                ("new", "You can now water your garden from the rest screen."),
                ("fixed", "A plant that didn't grow"),
                ("improved", "Meadow opens faster."),
                ("none", ""),
                ("none", "Tidy"),
                ("none", "Bump kotlin"),
            ],
            kinds,
        )

    def test_the_overview_counts_what_people_get(self):
        self.assertEqual("This update has 1 new feature, 1 improvement and 1 fix.", counted(self.changes()))
        self.assertEqual("This update has 2 fixes.", counted([Change("fix: a (#1)", 1), Change("fix: b (#2)", 2)]))
        self.assertEqual("", counted([Change("test: a (#1)", 1)]))

    def test_the_part_for_people_comes_first_then_the_highlights_then_every_change(self):
        text = render(self.changes(), "o/r", "v0.4.0", "v0.5.0")
        people = text[text.index(FOR_PEOPLE_START) : text.index(FOR_PEOPLE_END)]
        self.assertEqual(
            f"{FOR_PEOPLE_START}\nThis update has 1 new feature, 1 improvement and 1 fix.\n\n"
            "### New\n- You can now water your garden from the rest screen. (#7)\n\n"
            "### Improved\n- Meadow opens faster. (#9)\n\n"
            "### Fixed\n- A plant that didn't grow (#8)\n\n",
            people,
        )
        self.assertIn(
            "## Highlights\n\n### You can now water your garden from the rest screen (#7)\n\n"
            f'<img src="{URL}/after/stories/garden/02.png" width="300" alt="Water a plant">\n\n## All changes',
            text,
        )
        self.assertIn("## All changes\n\n- feat(app): water the garden (#7)\n- fix: a plant that didn't grow (#8)\n", text)
        self.assertIn("- chore: tidy\n- build(deps): bump kotlin (#11)\n", text)
        self.assertTrue(text.endswith("https://github.com/o/r/compare/v0.4.0...v0.5.0\n"))

    def test_a_release_with_nothing_for_people_gets_no_attention(self):
        changes = [Change("test: cover it (#1)", 1), Change("ci: faster build (#2)", 2)]
        text = render(changes, "o/r", None, "v0.1.0")
        self.assertTrue(text.startswith(f"{FOR_PEOPLE_START}\n{FOR_PEOPLE_END}\n"))
        self.assertNotIn("Highlights", text)
        self.assertNotIn("Full comparison", text)

    def test_at_most_three_highlights_with_one_picture_each(self):
        pictures = [Picture(f"https://x/after/{i}.png", str(i)) for i in range(5)]
        changes = [Change(f"feat: {i} (#{i})", i, note=Note("new", "yes", f"A{i}"), pictures=pictures) for i in range(1, 6)]
        text = render(changes, "o/r", None, "v1.0.0")
        self.assertEqual(["### A1 (#1)", "### A2 (#2)", "### A3 (#3)"], [line for line in text.splitlines() if line.startswith("### A")])
        self.assertEqual(3, text.count("<img"))

    def test_the_picture_is_the_one_named_else_the_first_step_else_the_first(self):
        story = [
            Picture("https://x/after/stories/S/story.webp", "video"),
            Picture("https://x/after/stories/S/01.png", "one"),
            Picture("https://x/after/stories/S/02.png", "two"),
            Picture("https://x/after/gallery/day/a.png", "screen"),
        ]

        def chosen(picture, pictures=story):
            return Change("feat: a (#1)", 1, note=Note("new", "yes", "A", picture), pictures=pictures).picture.caption

        self.assertEqual("two", chosen("2"))
        self.assertEqual("video", chosen("video"))
        self.assertEqual("one", chosen(""))
        self.assertEqual("one", chosen("9"))
        self.assertEqual("screen", chosen("", story[3:]))
        self.assertIsNone(Change("feat: a (#1)", 1, note=Note("new", "yes", "A")).picture)

    def test_a_note_that_replaces_another_in_the_release_hides_it(self):
        first = Change("feat: rows (#219)", 219, note=Note("new", "yes", "See what a change changes."))
        later = Change("feat: week (#221)", 221, note=Note("new", "yes", "Set your limits on one week.", replaces="#219"))
        text = render([first, later], "o/r", "v0.6.1", "v0.7.0")
        people = text[: text.index(FOR_PEOPLE_END)]
        self.assertIn("This update has 1 new feature.\n\n### New\n- Set your limits on one week. (#221)\n", people)
        self.assertNotIn("#219", people)
        self.assertEqual(["### Set your limits on one week (#221)"], [line for line in text.splitlines() if line.startswith("### S")])
        self.assertIn("- feat: rows (#219)\n- feat: week (#221)\n", text)
        # When the replaced one shipped before, the note just adds its line.
        self.assertIn("- Set your limits on one week. (#221)", render([later], "o/r", "v0.7.0", "v0.8.0"))


class OverviewTest(unittest.TestCase):
    NOTES = f"{FOR_PEOPLE_START}\r\nThis update has 1 fix.\r\n\r\n### Fixed\r\n- A (#1)\r\n\r\n{FOR_PEOPLE_END}\r\n\r\n## All changes\r\n\r\n- fix: a (#1)\r\n"

    def test_the_new_overview_takes_the_old_ones_place_and_the_rest_stays(self):
        self.assertEqual(
            f"{FOR_PEOPLE_START}\nYour plants grow again after a long night.\n\n### Fixed\n- A (#1)\n\n{FOR_PEOPLE_END}\n\n## All changes\n\n- fix: a (#1)\n",
            with_overview(self.NOTES, "Your plants grow again after a long night."),
        )

    def test_a_release_with_nothing_for_people_gets_no_overview(self):
        with self.assertRaises(ValueError):
            with_overview(f"{FOR_PEOPLE_START}\n{FOR_PEOPLE_END}\n", "Hello.")
        with self.assertRaises(ValueError):
            with_overview("## What's Changed", "Hello.")

    def test_an_overview_is_one_short_paragraph(self):
        self.assertEqual([], overview_problems("Your garden waters itself now."))
        self.assertEqual(1, len(overview_problems(" ".join(["word"] * (OVERVIEW_MAX_WORDS + 1)))))
        self.assertEqual(1, len(overview_problems("One.\n\nTwo.")))
        self.assertEqual(1, len(overview_problems(" ")))


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
        self.assertEqual(3, len(changes_since(None, sha)))


if __name__ == "__main__":
    unittest.main()
