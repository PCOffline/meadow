"""Fails when JaCoCo coverage drops below a floor, and prints coverage per package.

Usage: coverage_floor.py REPORT.xml --lines 60 --branches 45

Generated classes (R, BuildConfig, Compose singletons, serializers) are left out: no test
could cover them and they would only blur the numbers.
"""
import argparse
import re
import sys
import xml.etree.ElementTree as ET

GENERATED = re.compile(r"(^|/)(R|R\$.*|BuildConfig|Manifest.*|ComposableSingletons\$.*)$|\$\$serializer$|\$Companion\$serializer")


def counts(report_xml: str) -> dict[str, dict[str, list[int]]]:
    """Missed and covered LINE and BRANCH counts per package, without generated classes."""
    packages: dict[str, dict[str, list[int]]] = {}
    root = ET.fromstring(report_xml)
    for package in root.iter("package"):
        totals = {"LINE": [0, 0], "BRANCH": [0, 0]}
        for cls in package.iter("class"):
            if GENERATED.search(cls.get("name", "")):
                continue
            for counter in cls.findall("counter"):
                kind = counter.get("type")
                if kind in totals:
                    totals[kind][0] += int(counter.get("missed", 0))
                    totals[kind][1] += int(counter.get("covered", 0))
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
    args = parser.parse_args()
    with open(args.report, encoding="utf-8") as f:
        report, failures = check(counts(f.read()), args.lines, args.branches)
    print("\n".join(report))
    for failure in failures:
        print(f"::error::{failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
