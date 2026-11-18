"""Fails when JaCoCo coverage drops below a floor, and prints coverage per package.

Usage: coverage_floor.py REPORT.xml --lines 60 --branches 45 [--sources app/src/main/java]
                         [--classes app/build/.../classes --exec app/build/.../testDebugUnitTest.exec]

Generated classes (R, BuildConfig, Compose singletons, serializers) are left out: no test
could cover them and they would only blur the numbers.

With --sources, branches are counted from the source lines, and the lines of each @Composable
function's signature are left out. The Compose compiler puts branches there for default
arguments and for skipping a recomposition, which no test of what the code does reaches.

With --classes and --exec as well, every other branch that the Compose compiler adds is left out
too: those that reuse a remembered value or a lambda, and check which arguments changed.
ComposeBranches.java counts each line's branches again with JaCoCo and ASM from the Gradle cache,
with one more filter for those branches.
"""
import argparse
import glob
import os
import re
import subprocess
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


ASM_PARTS = ("asm", "asm-commons", "asm-tree", "asm-analysis")


def jacoco_version(versions_toml: str) -> str:
    """The build's JaCoCo version. The tool uses JaCoCo's internals, so it must be the same one."""
    with open(versions_toml, encoding="utf-8") as f:
        found = re.search(r'^jacoco\s*=\s*"([^"]+)"', f.read(), re.MULTILINE)
    if not found:
        raise SystemExit(f"No JaCoCo version in {versions_toml}")
    return found.group(1)


def tool_classpath(gradle_home: str, jacoco: str) -> str:
    """JaCoCo's core and the newest ASM that has all of [ASM_PARTS], from the Gradle cache."""
    cache = os.path.join(gradle_home, "caches", "modules-2", "files-2.1")
    core = glob.glob(os.path.join(cache, "org.jacoco", "org.jacoco.core", jacoco, "*", f"org.jacoco.core-{jacoco}.jar"))
    if not core:
        raise SystemExit(f"No JaCoCo {jacoco} in {cache}")
    versions: dict[str, dict[str, str]] = {}
    for part in ASM_PARTS:
        for jar in glob.glob(os.path.join(cache, "org.ow2.asm", part, "*", "*", f"{part}-*.jar")):
            version = os.path.basename(os.path.dirname(os.path.dirname(jar)))
            if os.path.basename(jar) == f"{part}-{version}.jar":
                versions.setdefault(version, {})[part] = jar
    complete = [v for v, jars in versions.items() if len(jars) == len(ASM_PARTS)]
    if not complete:
        raise SystemExit(f"No ASM with {', '.join(ASM_PARTS)} in {cache}")
    newest = max(complete, key=lambda v: [int(n) if n.isdigit() else 0 for n in v.split(".")])
    return os.pathsep.join([core[0]] + [versions[newest][part] for part in ASM_PARTS])


def parse_lines(output: str) -> dict[tuple[str, int], tuple[int, int]]:
    """ComposeBranches.java's output: "package/File.kt LINE MISSED COVERED" for each line."""
    found = {}
    for row in output.splitlines():
        parts = row.split()
        if len(parts) == 4 and all(p.isdigit() for p in parts[1:]):
            found[(parts[0], int(parts[1]))] = (int(parts[2]), int(parts[3]))
    return found


def branches_without_compose(classes: str, exec_file: str) -> dict[tuple[str, int], tuple[int, int]]:
    """Each line's missed and covered branches, without those that the Compose compiler adds."""
    gradle_home = os.environ.get("GRADLE_USER_HOME") or os.path.join(os.path.expanduser("~"), ".gradle")
    here = os.path.dirname(os.path.abspath(__file__))
    jacoco = jacoco_version(os.path.join(here, "..", "..", "gradle", "libs.versions.toml"))
    result = subprocess.run(
        ["java", "-cp", tool_classpath(gradle_home, jacoco), os.path.join(here, "ComposeBranches.java"), classes, exec_file],
        capture_output=True, text=True, check=True,
    )
    return parse_lines(result.stdout)


def counts(
    report_xml: str, sources: str | None = None, lines: dict[tuple[str, int], tuple[int, int]] | None = None,
) -> dict[str, dict[str, list[int]]]:
    """
    Missed and covered LINE and BRANCH counts per package, without generated classes. With
    [sources], BRANCH counts come from the source lines, without @Composable signatures. With
    [lines] too, each line's branches are those in [lines] (see branches_without_compose).
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
                    number = int(line.get("nr", 0))
                    if number in skipped:
                        continue
                    missed, covered = int(line.get("mb", 0)), int(line.get("cb", 0))
                    if lines is not None:
                        missed, covered = lines.get((f"{package.get('name', '')}/{sourcefile.get('name', '')}", number), (0, 0))
                    totals["BRANCH"][0] += missed
                    totals["BRANCH"][1] += covered
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
    parser.add_argument("--classes", help="The compiled classes, to leave out every branch the Compose compiler adds (with --sources)")
    parser.add_argument("--exec", help="The coverage data that the report was made from (with --classes)")
    args = parser.parse_args()
    lines = branches_without_compose(args.classes, args.exec) if args.classes and args.exec else None
    with open(args.report, encoding="utf-8") as f:
        report, failures = check(counts(f.read(), args.sources, lines), args.lines, args.branches)
    print("\n".join(report))
    for failure in failures:
        print(f"::error::{failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
