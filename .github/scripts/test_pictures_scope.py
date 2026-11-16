import unittest

from pictures_scope import KOTLIN, STORIES, looks, stories


class LooksTest(unittest.TestCase):
    def test_a_change_to_a_screen_draws_the_basic_looks(self):
        self.assertEqual("basic", looks([KOTLIN + "ui/profile/ProfileScreen.kt", "app/src/main/res/values/strings.xml"]))

    def test_a_change_to_the_themes_or_the_flowers_draws_each_theme_by_day_only(self):
        self.assertEqual("themes", looks([KOTLIN + "ui/profile/ProfileScreen.kt", KOTLIN + "ui/theme/Theme.kt"]))
        self.assertEqual("themes", looks([KOTLIN + "ui/components/Flowers.kt", KOTLIN + "ui/settings/Appearance.kt"]))
        self.assertEqual("themes", looks([KOTLIN + "ui/components/flora/Plants.kt"]))

    def test_a_change_to_the_night_colours_draws_each_theme_by_night(self):
        self.assertEqual("night", looks(["app/src/main/res/values-night/colors.xml"]))

    def test_a_change_to_the_weather_draws_each_weather(self):
        self.assertEqual("weather", looks([KOTLIN + "data/Weather.kt"]))

    def test_a_change_to_the_sky_draws_the_night_and_each_weather(self):
        self.assertEqual("night,weather", looks([KOTLIN + "ui/components/Ambient.kt"]))

    def test_no_change_draws_the_basic_looks(self):
        self.assertEqual("basic", looks([]))


class StoriesTest(unittest.TestCase):
    def test_names_the_stories_that_were_added_or_changed(self):
        changed = [STORIES + "ThemePreviewStory.kt", STORIES + "EmojiWithPhotoStory.kt", KOTLIN + "ui/theme/Theme.kt"]
        self.assertEqual("EmojiWithPhotoStory,ThemePreviewStory", stories(changed))

    def test_a_change_that_touches_no_story_shows_none(self):
        self.assertEqual("", stories([KOTLIN + "ui/components/Flowers.kt"]))

    def test_a_change_to_how_every_story_is_drawn_shows_each_story_that_differs(self):
        self.assertEqual("*", stories([STORIES + "Story.kt"]))


if __name__ == "__main__":
    unittest.main()
