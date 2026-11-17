"""Fails when JaCoCo coverage drops below a floor, and prints coverage per package.

Usage: coverage_floor.py REPORT.xml --lines 60 --branches 45 [--sources app/src/main/java]

Generated classes (R, BuildConfig, Compose singletons, serializers) are left out: no test
could cover them and they would only blur the numbers.

With --sources, branches are counted from the source lines, and the lines of each @Composable
function's signature are left out. The Compose compiler puts branches there for default
arguments and for skipping a recomposition, which no test of what the code does reaches.
"""
import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

GENERATED = re.compile(r"(^|/)(R|R\$.*|BuildConfig|Manifest.*|ComposableSingletons\$.*)$|\$\$serializer$|\$Companion\$serializer")


COMPOSABLE_FUN = re.compile(r"@Composable\b.*\bfun\b")


def composable_signatures(source: str) -> set[int]:
    """The line numbers (from 1) of each @Composable function's signature, up to its body."""
    lines = source.split("\n")
    found: set[int] = set()
    annotated = False
    i = 0
    while i < len(lines):
        text = lines[i].strip()
        if text.startswith("@Composable") and not re.search(r"\bfun\b", text):
            annotated = True
            i += 1
            continue
        if annotated and text.startswith("@"):
            i += 1
            continue
        if (annotated and re.search(r"\bfun\b", text)) or COMPOSABLE_FUN.search(text):
            depth = 0
            j = i
            while j < len(lines):
                found.add(j + 1)
                depth += lines[j].count("(") - lines[j].count(")")
                if depth <= 0 and ("{" in lines[j] or "=" in lines[j] or j > i):
                    break
                j += 1
            i = j + 1
        else:
            i += 1
        annotated = False
    return found


def counts(report_xml: str, sources: str | None = None) -> dict[str, dict[str, list[int]]]:
    """
    Missed and covered LINE and BRANCH counts per package, without generated classes. With
    [sources], BRANCH counts come from the source lines, without @Composable signatures.
    """
    packages: dict[str, dict[str, list[int]]] = {}
    root = ET.fromstring(report_xml)
    for package in root.iter("package"):
        totals = {"LINE": [0, 0], "BRANCH": [0, 0]}
        for cls in package.iter("class"):
            if GENERATED.search(cls.get("name", "")):
                continue
            for counter in cls.findall("counter"):
                kind = counter.get("type")
                if kind in totals and not (kind == "BRANCH" and sources):
                    totals[kind][0] += int(counter.get("missed", 0))
                    totals[kind][1] += int(counter.get("covered", 0))
        if sources:
            for sourcefile in package.iter("sourcefile"):
                path = os.path.join(sources, package.get("name", ""), sourcefile.get("name", ""))
                skipped = set()
                if os.path.exists(path):
                    with open(path, encoding="utf-8") as f:
                        skipped = composable_signatures(f.read())
                for line in sourcefile.findall("line"):
                    if int(line.get("nr", 0)) in skipped:
                        continue
                    totals["BRANCH"][0] += int(line.get("mb", 0))
                    totals["BRANCH"][1] += int(line.get("cb", 0))
        if any(sum(v) for v in totals.values()):
            packages[package.get("name", "").replace("/", ".")] = totals
    return packages


def percent(missed: int, covered: int) -> float:
    total = missed + covered
    return 100.0 if total == 0 else 100.0 * covered / total


def check(packages: dict[str, dict[str, list[int]]], lines: float, branches: float) -> tuple[list[str], list[str]]:
    """Returns the report lines and the failures."""
    report = [f"{'package':60} {'lines':>7} {'branches':>9}"]
    overall = {"LINE": [0, 0], "BRANCH": [0, 0]}
    for name, p in sorted(packages.items()):
        for kind, total in overall.items():
            total[0] += p[kind][0]
            total[1] += p[kind][1]
        report.append(f"{name:60} {percent(*p['LINE']):6.1f}% {percent(*p['BRANCH']):8.1f}%")
    line_pct, branch_pct = percent(*overall["LINE"]), percent(*overall["BRANCH"])
    report.append(f"{'all':60} {line_pct:6.1f}% {branch_pct:8.1f}%")
    failures = []
    if line_pct < lines:
        failures.append(f"Line coverage {line_pct:.1f}% is below the floor of {lines}%")
    if branch_pct < branches:
        failures.append(f"Branch coverage {branch_pct:.1f}% is below the floor of {branches}%")
    return report, failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report")
    parser.add_argument("--lines", type=float, required=True)
    parser.add_argument("--branches", type=float, required=True)
    parser.add_argument("--sources", help="The Kotlin sources, to leave out the branches of @Composable signatures")
    args = parser.parse_args()
    with open(args.report, encoding="utf-8") as f:
        report, failures = check(counts(f.read(), args.sources), args.lines, args.branches)
    print("\n".join(report))
    for failure in failures:
        print(f"::error::{failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
