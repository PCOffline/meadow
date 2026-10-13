import unittest

from coverage_floor import check, counts

REPORT = """<?xml version="1.0"?>
<report name="debug">
  <package name="dev/pcoffline/meadow/data">
    <class name="dev/pcoffline/meadow/data/Limits">
      <counter type="LINE" missed="2" covered="8"/>
      <counter type="BRANCH" missed="1" covered="3"/>
    </class>
    <class name="dev/pcoffline/meadow/data/Limit$$serializer">
      <counter type="LINE" missed="50" covered="0"/>
    </class>
  </package>
  <package name="dev/pcoffline/meadow">
    <class name="dev/pcoffline/meadow/R$string"><counter type="LINE" missed="99" covered="0"/></class>
    <class name="dev/pcoffline/meadow/BuildConfig"><counter type="LINE" missed="9" covered="0"/></class>
    <class name="dev/pcoffline/meadow/ui/ComposableSingletons$GardenKt"><counter type="LINE" missed="9" covered="0"/></class>
  </package>
  <package name="dev/pcoffline/meadow/ui">
    <class name="dev/pcoffline/meadow/ui/Garden">
      <counter type="LINE" missed="8" covered="2"/>
      <counter type="BRANCH" missed="3" covered="1"/>
    </class>
  </package>
</report>"""


class CoverageFloorTest(unittest.TestCase):
    def test_generated_classes_are_left_out(self):
        packages = counts(REPORT)
        self.assertEqual(set(packages), {"dev.pcoffline.meadow.data", "dev.pcoffline.meadow.ui"})
        self.assertEqual(packages["dev.pcoffline.meadow.data"]["LINE"], [2, 8])

    def test_passes_at_the_floor(self):
        report, failures = check(counts(REPORT), lines=50, branches=50)
        self.assertEqual(failures, [])
        self.assertIn("50.0%", report[-1])

    def test_fails_below_the_floor(self):
        _, failures = check(counts(REPORT), lines=50.1, branches=60)
        self.assertEqual(len(failures), 2)
        self.assertIn("Line coverage 50.0%", failures[0])

    def test_an_empty_report_is_fully_covered(self):
        self.assertEqual(check({}, 100, 100)[1], [])


if __name__ == "__main__":
    unittest.main()
