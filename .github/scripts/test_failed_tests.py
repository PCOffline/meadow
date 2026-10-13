import unittest

from failed_tests import MAX_LINES, failures

RESULTS = """<?xml version="1.0"?>
<testsuite name="dev.pcoffline.meadow.ui.screens.RenderedContrastTest" tests="3" failures="1" errors="1">
  <testcase name="passes" classname="dev.pcoffline.meadow.ui.screens.RenderedContrastTest"/>
  <testcase name="the garden" classname="dev.pcoffline.meadow.ui.screens.RenderedContrastTest">
    <failure message="java.lang.AssertionError: 2 problems">java.lang.AssertionError: 2 problems
  at org.junit.Assert.fail</failure>
  </testcase>
  <testcase name="crashes" classname="dev.pcoffline.meadow.ui.screens.RenderedContrastTest">
    <error>%s</error>
  </testcase>
</testsuite>""" % "\n".join(f"line {i}" for i in range(40))


class FailedTestsTest(unittest.TestCase):
    def test_lists_failures_and_errors_only(self):
        found = failures(RESULTS)
        self.assertEqual([name for name, _ in found], ["RenderedContrastTest > the garden", "RenderedContrastTest > crashes"])
        self.assertEqual(found[0][1], "java.lang.AssertionError: 2 problems")

    def test_long_failures_are_cut_short(self):
        text = failures(RESULTS)[1][1]
        self.assertEqual(len(text.splitlines()), MAX_LINES + 1)
        self.assertIn("20 more lines", text)


if __name__ == "__main__":
    unittest.main()
