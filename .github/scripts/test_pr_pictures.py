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
    names,
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


class OrderTest(unittest.TestCase):
    def test_lists_pictures_in_the_order_drawn(self):
        gallery = compare(set(), {"light/b.png", "dark/a.png", "light/z.png"}, lambda p: False, order=["light/b.png", "dark/a.png"])
        self.assertEqual(["light/b.png", "dark/a.png", "light/z.png"], gallery.added)


class NamesTest(unittest.TestCase):
    def test_reads_the_real_names_from_the_index(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "gallery").mkdir()
            (root / "gallery" / "index.tsv").write_text("dark/chats--a-message.png\tChats\tA message that didn't send\tdark")
            self.assertEqual([("dark/chats--a-message.png", ("Chats: a message that didn't send", "dark"), "dark")], names(root))

    def test_reads_each_looks_group(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "gallery").mkdir()
            (root / "gallery" / "index.tsv").write_text("blossom-night/garden--rain.png\tGarden\tRain\tNight\tCherry blossom")
            self.assertEqual([("blossom-night/garden--rain.png", ("Garden: rain", "Night"), "Cherry blossom")], names(root))

    def test_a_named_picture_shows_its_real_name(self):
        gallery = Gallery(changed=["dark/x.png"], names={"dark/x.png": ("Chats: a friend's chat", "dark")})
        self.assertIn("| Chats: a friend's chat | dark |", section([], gallery, URL))


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
        self.assertIn("No new or changed story", section([], Gallery(), URL))

    def test_a_story_the_same_as_on_the_base_is_only_named(self):
        same = StoryPictures("W", "Set weekday hours", [], None, new=False, changed=False)
        text = section([same], Gallery(), URL)
        self.assertNotIn("#### Set weekday hours", text)
        self.assertIn("The same as on the base: Set weekday hours.", text)
        self.assertIn("No new or changed story", text)

    def test_screens_are_grouped_by_theme_with_day_and_night_together(self):
        paths = ["meadow-day/a.png", "meadow-night/a.png", "blossom-day/a.png", "blossom-night/a.png", "large-text/a.png"]
        gallery = Gallery(
            changed=paths,
            names={p: ("Garden: rain", "Night" if "night" in p else "Day") for p in paths},
            groups={p: g for p, g in zip(paths, ["Meadow", "Meadow", "Cherry blossom", "Cherry blossom", "Large text"])},
        )
        text = section([], gallery, URL)
        self.assertEqual(3, text.count("| Screen | Look | Before | After |"))
        meadow, blossom, large = (text.index(f"#### {g}") for g in ("Meadow", "Cherry blossom", "Large text"))
        self.assertLess(meadow, blossom)
        self.assertLess(blossom, large)
        under_meadow = text[meadow:blossom]
        self.assertIn("| Garden: rain | Day |", under_meadow)
        self.assertIn("| Garden: rain | Night |", under_meadow)

    def test_a_changed_screen_shows_before_and_after(self):
        text = section([], Gallery(changed=["dark/garden--lived-in.png"], unchanged=3), URL)
        self.assertIn("1 changed, 0 new, 0 gone, 3 the same.", text)
        self.assertIn(f"{URL}/before/gallery/dark/garden--lived-in.png", text)
        self.assertIn(f"{URL}/after/gallery/dark/garden--lived-in.png", text)

    def test_nothing_changed_says_so(self):
        self.assertIn("No screen in the gallery changed (5 the same).", section([], Gallery(unchanged=5), URL))

    def test_without_base_pictures_points_to_the_folder_and_lists_none(self):
        gallery = compare(set(), {f"light/s{i}.png" for i in range(414)}, lambda p: False)
        text = section([], gallery, URL, browse="https://github.com/o/r/tree/pictures/pr-1/run")
        self.assertIn("All 414 are [in the pictures' folder](https://github.com/o/r/tree/pictures/pr-1/run/after/gallery).", text)
        self.assertNotIn("<img", text)

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
            self.assertTrue(found[0].changed)
            self.assertEqual("before/stories/HoursStory/01.png", found[0].steps[0].before)
            self.assertIsNone(found[0].steps[1].before)
            self.assertTrue((root / "out" / "after" / "stories" / "HoursStory" / "02.png").exists())

    def test_a_story_drawn_the_same_on_the_base_has_no_pictures(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("needs Pillow")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for side in ("before", "after"):
                story = root / side / "stories" / "WeekdayHoursStory"
                story.mkdir(parents=True)
                Image.new("RGB", (108, 240), "white").save(story / "01.png")
                (story / "story.json").write_text(json.dumps({"title": "Weekday hours", "steps": ["One."]}))
            found = stories(root / "before", root / "after", root / "out")
            self.assertFalse(found[0].changed)
            self.assertEqual([], found[0].steps)
            self.assertFalse((root / "out" / "after" / "stories" / "WeekdayHoursStory").exists())

    def test_a_story_with_a_new_caption_has_changed(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("needs Pillow")
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for side, caption in (("before", "Old."), ("after", "New.")):
                story = root / side / "stories" / "S"
                story.mkdir(parents=True)
                Image.new("RGB", (108, 240), "white").save(story / "01.png")
                (story / "story.json").write_text(json.dumps({"title": "S", "steps": [caption]}))
            self.assertTrue(stories(root / "before", root / "after", root / "out")[0].changed)


if __name__ == "__main__":
    unittest.main()
