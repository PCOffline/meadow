import unittest

from pictures_scope import KOTLIN, scope


class ScopeTest(unittest.TestCase):
    def test_a_change_to_a_screen_draws_the_basic_looks(self):
        self.assertEqual("basic", scope([KOTLIN + "ui/profile/ProfileScreen.kt", "app/src/main/res/values/strings.xml"]))

    def test_a_change_to_the_themes_draws_every_look(self):
        self.assertEqual("all", scope([KOTLIN + "ui/profile/ProfileScreen.kt", KOTLIN + "ui/theme/Theme.kt"]))

    def test_a_change_to_the_sky_or_the_weather_draws_every_look(self):
        self.assertEqual("all", scope([KOTLIN + "ui/components/Ambient.kt"]))
        self.assertEqual("all", scope([KOTLIN + "data/Weather.kt"]))

    def test_a_change_to_the_night_colours_draws_every_look(self):
        self.assertEqual("all", scope(["app/src/main/res/values-night/colors.xml"]))

    def test_no_change_draws_the_basic_looks(self):
        self.assertEqual("basic", scope([]))


if __name__ == "__main__":
    unittest.main()
