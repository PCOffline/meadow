import json
import tempfile
import unittest
from pathlib import Path

from pr_pictures import (
    END,
    MAX_CHARS,
    START,
    Gallery,
    Step,
    StoryPictures,
    compare,
    label,
    section,
    stories,
    with_pictures,
)

URL = "https://raw.githubusercontent.com/o/r/abc"


class CompareTest(unittest.TestCase):
    def test_sorts_pictures_into_changed_new_gone_and_the_same(self):
        before = {"light/a--x.png", "light/b--y.png", "dark/gone--z.png"}
        after = {"light/a--x.png", "light/b--y.png", "light/new--w.png"}
        gallery = compare(before, after, differs=lambda p: p == "light/b--y.png")
        self.assertEqual(["light/b--y.png"], gallery.changed)
        self.assertEqual(["light/new--w.png"], gallery.added)
        self.assertEqual(["dark/gone--z.png"], gallery.removed)
        self.assertEqual(1, gallery.unchanged)

    def test_with_no_base_pictures_everything_is_new(self):
        gallery = compare(set(), {"light/a--x.png"}, differs=lambda p: self.fail("nothing to compare"))
        self.assertEqual(["light/a--x.png"], gallery.added)


class LabelTest(unittest.TestCase):
    def test_names_the_screen_its_state_and_the_look(self):
        self.assertEqual(("Garden: nothing planted yet", "dark"), label("dark/garden--nothing-planted-yet.png"))


class WithPicturesTest(unittest.TestCase):
    def test_adds_the_pictures_after_the_authors_text(self):
        body = with_pictures("## Summary\n\nText.", "## Pictures")
        self.assertTrue(body.startswith("## Summary\n\nText.\n\n" + START))
        self.assertIn("## Pictures\n" + END, body)

    def test_replaces_the_last_pictures_and_keeps_the_rest(self):
        first = with_pictures("Before.", "Old")
        second = with_pictures(first + "\nAfter.", "New")
        self.assertNotIn("Old", second)
        self.assertIn("New", second)
        self.assertTrue(second.startswith("Before."))
        self.assertTrue(second.rstrip().endswith("After."))
        self.assertEqual(1, second.count(START))

    def test_an_empty_description_gets_only_the_pictures(self):
        self.assertEqual(f"{START}\nP\n{END}\n", with_pictures(None, "P"))


class SectionTest(unittest.TestCase):
    def test_starts_with_its_heading(self):
        self.assertTrue(section([], Gallery(), URL).startswith("## Pictures"))

    def test_without_a_story_points_to_the_rule(self):
        self.assertIn("No story", section([], Gallery(), URL))

    def test_a_changed_screen_shows_before_and_after(self):
        text = section([], Gallery(changed=["dark/garden--lived-in.png"], unchanged=3), URL)
        self.assertIn("1 changed, 0 new, 0 gone, 3 the same.", text)
        self.assertIn(f"{URL}/before/gallery/dark/garden--lived-in.png", text)
        self.assertIn(f"{URL}/after/gallery/dark/garden--lived-in.png", text)

    def test_nothing_changed_says_so(self):
        self.assertIn("No screen in the gallery changed (5 the same).", section([], Gallery(unchanged=5), URL))

    def test_gone_screens_are_named(self):
        self.assertIn("Gone from the gallery: Garden: loading (light).", section([], Gallery(removed=["light/garden--loading.png"]), URL))

    def test_a_new_story_shows_each_step_and_its_video(self):
        story = StoryPictures("S", "Set hours", [Step("Pick Saturday.", None, "after/stories/S/01.png")], "after/stories/S/story.webp", new=True)
        text = section([story], Gallery(), URL)
        self.assertIn("#### Set hours", text)
        self.assertIn(f"{URL}/after/stories/S/story.webp", text)
        self.assertIn("| 1. Pick Saturday. |", text)
        self.assertNotIn("| Before |", text)

    def test_a_story_the_base_had_too_shows_before_and_after(self):
        steps = [Step("One.", "before/stories/S/01.png", "after/stories/S/01.png"), Step("Two.", None, "after/stories/S/02.png")]
        text = section([StoryPictures("S", "T", steps, None, new=False)], Gallery(), URL)
        self.assertIn("| Step | Before | After |", text)
        self.assertIn(f"{URL}/before/stories/S/01.png", text)
        self.assertIn("| 2. Two. | None |", text)

    def test_too_many_screens_fit_in_a_comment_and_say_how_many_are_left(self):
        many = [f"light/screen-{i}--state-with-a-long-name-{i}.png" for i in range(1000)]
        text = section([], Gallery(changed=many), URL)
        self.assertLessEqual(len(text), MAX_CHARS + 200)
        self.assertRegex(text, r"\d+ more aren't shown here")


class StoriesTest(unittest.TestCase):
    def test_reads_each_story_and_pairs_its_steps_with_the_base(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("needs Pillow")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for side, count in (("before", 1), ("after", 2)):
                story = root / side / "stories" / "HoursStory"
                story.mkdir(parents=True)
                for i in range(1, count + 1):
                    Image.new("RGB", (1080, 2400), "white").save(story / f"{i:02d}.png")
                (story / "story.json").write_text(json.dumps({"title": "Hours", "steps": [f"Step {i}." for i in range(1, count + 1)]}))
            found = stories(root / "before", root / "after", root / "out")
            self.assertEqual(1, len(found))
            self.assertFalse(found[0].new)
            self.assertEqual("before/stories/HoursStory/01.png", found[0].steps[0].before)
            self.assertIsNone(found[0].steps[1].before)
            self.assertTrue((root / "out" / "after" / "stories" / "HoursStory" / "02.png").exists())


if __name__ == "__main__":
    unittest.main()
