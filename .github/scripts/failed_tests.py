"""Prints a short summary of failed tests from JUnit XML results, last in the log where it's easy to find.

Usage: failed_tests.py DIR [DIR...]   (searched recursively for *.xml)
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

MAX_LINES = 20
SKIPPED = "AssumptionViolatedException"


def failures(xml_text: str) -> list[tuple[str, str]]:
    """(test, first lines of its failure) for each failed or errored test case."""
    found = []
    root = ET.fromstring(xml_text)
    for case in root.iter("testcase"):
        if case.find("skipped") is not None:
            continue
        for problem in [*case.findall("failure"), *case.findall("error")]:
            name = f"{case.get('classname', '').rsplit('.', 1)[-1]} > {case.get('name', '')}"
            text = (problem.get("message") or problem.text or "").strip()
            if SKIPPED in text or SKIPPED in (problem.get("type") or ""):
                continue  # A test that chose not to run (an unmet assumption) didn't fail.
            lines = text.splitlines()
            shown = "\n".join(lines[:MAX_LINES]) + (f"\n... ({len(lines) - MAX_LINES} more lines)" if len(lines) > MAX_LINES else "")
            found.append((name, shown))
    return found


def main(dirs: list[str]) -> int:
    found = []
    for directory in dirs:
        for path in sorted(pathlib.Path(directory).rglob("*.xml")):
            try:
                found += failures(path.read_text(encoding="utf-8"))
            except ET.ParseError:
                continue
    if not found:
        print("No failed tests in the results.")
        return 0
    print(f"{len(found)} failed test(s):")
    for name, text in found:
        print(f"\n✖ {name}\n{text}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
