import os
import tempfile
import unittest

from coverage_floor import (
    check,
    composable_signatures,
    counts,
    jacoco_version,
    parse_lines,
    tool_classpath,
)

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


GARDEN_KT = """package dev.pcoffline.meadow.ui

@Composable
fun Garden(
    name: String,
    size: Int = 3,
) {
    if (size > 2) Text(name)
}

@Composable private fun Leaf(on: Boolean) = if (on) 1 else 0

@Preview
@Composable
internal fun Bloom(open: Boolean): Int {
    return if (open) 1 else 0
}

fun plain(content: @Composable () -> Unit) {
    if (true) content()
}
"""

SOURCED_REPORT = """<?xml version="1.0"?>
<report name="debug">
  <package name="dev/pcoffline/meadow/ui">
    <class name="dev/pcoffline/meadow/ui/GardenKt">
      <counter type="LINE" missed="1" covered="9"/>
      <counter type="BRANCH" missed="9" covered="1"/>
    </class>
    <sourcefile name="Garden.kt">
      <line nr="4" mi="0" ci="1" mb="4" cb="0"/>
      <line nr="7" mi="0" ci="1" mb="2" cb="0"/>
      <line nr="8" mi="0" ci="1" mb="1" cb="1"/>
      <line nr="11" mi="0" ci="1" mb="1" cb="1"/>
      <line nr="15" mi="0" ci="1" mb="2" cb="0"/>
      <line nr="16" mi="0" ci="1" mb="0" cb="2"/>
      <line nr="20" mi="0" ci="1" mb="1" cb="1"/>
    </sourcefile>
  </package>
</report>"""


class ComposableSignaturesTest(unittest.TestCase):
    def test_each_composable_signature_is_found_up_to_its_body(self):
        self.assertEqual(composable_signatures(GARDEN_KT), {4, 5, 6, 7, 11, 15})

    def test_with_sources_branches_on_composable_signatures_are_left_out(self):
        with tempfile.TemporaryDirectory() as sources:
            folder = os.path.join(sources, "dev/pcoffline/meadow/ui")
            os.makedirs(folder)
            with open(os.path.join(folder, "Garden.kt"), "w", encoding="utf-8") as f:
                f.write(GARDEN_KT)
            packages = counts(SOURCED_REPORT, sources)
        # Lines 8, 16 and 20: what the code itself decides. Lines stay counted by class.
        self.assertEqual(packages["dev.pcoffline.meadow.ui"]["BRANCH"], [2, 4])
        self.assertEqual(packages["dev.pcoffline.meadow.ui"]["LINE"], [1, 9])

    def test_without_sources_every_branch_counts(self):
        self.assertEqual(counts(SOURCED_REPORT)["dev.pcoffline.meadow.ui"]["BRANCH"], [9, 1])

    def test_a_source_file_that_is_missing_keeps_all_its_branches(self):
        with tempfile.TemporaryDirectory() as sources:
            self.assertEqual(counts(SOURCED_REPORT, sources)["dev.pcoffline.meadow.ui"]["BRANCH"], [11, 5])


class ComposeBranchesTest(unittest.TestCase):
    def test_the_tool_s_lines_are_read(self):
        output = "dev/pcoffline/meadow/ui/Garden.kt 8 1 1\nnoise\ndev/pcoffline/meadow/ui/Garden.kt 20 0 2\n"
        self.assertEqual(parse_lines(output), {("dev/pcoffline/meadow/ui/Garden.kt", 8): (1, 1), ("dev/pcoffline/meadow/ui/Garden.kt", 20): (0, 2)})

    def test_with_the_tool_s_lines_each_line_has_only_its_own_branches(self):
        # Line 16's branches are all the compiler's, so the tool lists no branches for it.
        lines = {("dev/pcoffline/meadow/ui/Garden.kt", 8): (1, 1), ("dev/pcoffline/meadow/ui/Garden.kt", 20): (0, 2),
                 ("dev/pcoffline/meadow/ui/Garden.kt", 4): (4, 0)}
        with tempfile.TemporaryDirectory() as sources:
            folder = os.path.join(sources, "dev/pcoffline/meadow/ui")
            os.makedirs(folder)
            with open(os.path.join(folder, "Garden.kt"), "w", encoding="utf-8") as f:
                f.write(GARDEN_KT)
            packages = counts(SOURCED_REPORT, sources, lines)
        # Line 4 is a signature: still left out. Lines 8 and 20 keep what the tool counted.
        self.assertEqual(packages["dev.pcoffline.meadow.ui"]["BRANCH"], [1, 3])

    def test_the_jacoco_version_is_the_build_s(self):
        with tempfile.TemporaryDirectory() as folder:
            toml = os.path.join(folder, "libs.versions.toml")
            with open(toml, "w", encoding="utf-8") as f:
                f.write('[versions]\nagp = "9.5.1"\njacoco = "0.8.15"\n')
            self.assertEqual(jacoco_version(toml), "0.8.15")

    def test_the_tool_uses_jacoco_and_the_newest_whole_asm_in_the_gradle_cache(self):
        with tempfile.TemporaryDirectory() as home:
            def jar(group, name, version):
                folder = os.path.join(home, "caches", "modules-2", "files-2.1", group, name, version, "abc")
                os.makedirs(folder)
                path = os.path.join(folder, f"{name}-{version}.jar")
                open(path, "w").close()
                return path
            core = jar("org.jacoco", "org.jacoco.core", "0.8.15")
            whole = [jar("org.ow2.asm", part, "9.9") for part in ("asm", "asm-commons", "asm-tree", "asm-analysis")]
            # 9.10.1 is newer, but without asm-analysis.
            for part in ("asm", "asm-commons", "asm-tree"):
                jar("org.ow2.asm", part, "9.10.1")
            self.assertEqual(tool_classpath(home, "0.8.15").split(os.pathsep), [core] + whole)
            with self.assertRaises(SystemExit):
                tool_classpath(home, "0.8.99")


if __name__ == "__main__":
    unittest.main()
