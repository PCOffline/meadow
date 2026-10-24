import unittest

from failed_tests import MAX_LINES, failures

RESULTS = """<?xml version="1.0"?>
<testsuite name="dev.pcoffline.meadow.ui.screens.RenderedContrastTest" tests="3" failures="1" errors="1">
  <testcase name="passes" classname="dev.pcoffline.meadow.ui.screens.RenderedContrastTest"/>
  <testcase name="the garden" classname="dev.pcoffline.meadow.ui.screens.RenderedContrastTest">
    <failure message="java.lang.AssertionError: 2 problems">java.lang.AssertionError: 2 problems
  at org.junit.Assert.fail</failure>
  </testcase>
  <testcase name="skipped" classname="dev.pcoffline.meadow.ShowcaseTest"><skipped/></testcase>
  <testcase name="assumed" classname="dev.pcoffline.meadow.ShowcaseTest">
    <failure>org.junit.AssumptionViolatedException: Build with -Pmeadow.showcase</failure>
  </testcase>
  <testcase name="crashes" classname="dev.pcoffline.meadow.ui.screens.RenderedContrastTest">
    <error>{}</error>
  </testcase>
</testsuite>""".format("\n".join(f"line {i}" for i in range(40)))


HANG = """<?xml version="1.0"?>
<testsuite name="EditorsTest" tests="1" failures="1">
  <testcase name="hangs" classname="dev.pcoffline.meadow.ui.screens.EditorsTest">
    <failure message="androidx.test.espresso.AppNotIdleException: Compose did not get idle">androidx.test.espresso.AppNotIdleException: Compose did not get idle
	at androidx.compose.ui.test.junit4.ComposeIdlingResource.waitForIdle(ComposeIdlingResource.kt:10)
	at dev.pcoffline.meadow.ui.screens.ScreenTest.node(ScreenTest.kt:40)
	at dev.pcoffline.meadow.ui.screens.EditorsTest.hangs(EditorsTest.kt:66)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
	at org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)</failure>
  </testcase>
</testsuite>"""


class FailedTestsTest(unittest.TestCase):
    def test_shows_where_in_the_project_it_happened(self):
        text = failures(HANG)[0][1]
        self.assertIn("at dev.pcoffline.meadow.ui.screens.EditorsTest.hangs(EditorsTest.kt:66)", text)
        self.assertIn("at dev.pcoffline.meadow.ui.screens.ScreenTest.node(ScreenTest.kt:40)", text)
        self.assertNotIn("ComposeIdlingResource", text.split("\n", 1)[1])
        self.assertNotIn("FrameworkMethod", text)

    def test_lists_failures_and_errors_only_not_skips(self):
        found = failures(RESULTS)
        self.assertEqual([name for name, _ in found], ["RenderedContrastTest > the garden", "RenderedContrastTest > crashes"])
        self.assertEqual(found[0][1], "java.lang.AssertionError: 2 problems")

    def test_long_failures_are_cut_short(self):
        text = failures(RESULTS)[1][1]
        self.assertEqual(len(text.splitlines()), MAX_LINES + 1)
        self.assertIn("20 more lines", text)


if __name__ == "__main__":
    unittest.main()
