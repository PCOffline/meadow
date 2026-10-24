"""Decides whether the Deliver workflow ships the app or the backend, and at which version.

The app and the backend are versioned separately, each with semantic versioning, because each
ships on its own: the app as a GitHub release tagged v0.4.2, the backend as a deploy tagged
backend-v0.7.0. The latest tag is the source of truth: every new version is the latest tag raised
by one part, and the tag marks what was shipped, so the next run knows what changed since.

    python3 plan_version.py app|backend

Writes `ship`, `version`, `tag` (empty when nothing new is tagged) and `reason` to $GITHUB_OUTPUT
(or prints them when run by hand). Environment: EVENT (push, workflow_dispatch or workflow_run),
REF_NAME, SHA, BUMP (patch, minor or major to choose the part; empty or auto to read it from the
commit messages).

- A pushed tag (v1.2.0) releases exactly that version of the app; it must be above the latest.
- By hand, choosing a part: raises exactly that part, major included (0.9.3 → 1.0.0).
- By hand, auto: as below, but the backend deploys even when unchanged, as the version it has.
- Automatically, after Build passes on main: ships only when the component changed since its last
  tag, unless the last commit says [skip release] (app) or [skip deploy] (backend). Commits that
  touch the component decide the part: [major] or [breaking] for incompatible changes, [minor] for
  new features, anything else a patch.

Leaving 0.x is a choice made by hand. Until then, SemVer's rule for 0.x versions applies to
automatic runs: an incompatible change raises the minor part (0.4.2 → 0.5.0). From 1.0.0 on,
[major] and [breaking] raise the major.
"""
import os
import re
import subprocess
import sys
from dataclasses import dataclass

FIRST_VERSION = (0, 1, 0)
PARTS = ("patch", "minor", "major")

Version = tuple[int, int, int]


@dataclass(frozen=True)
class Component:
    prefix: str
    paths: tuple[str, ...]
    skip_marker: str


COMPONENTS = {
    # The APK. It bundles the legal documents and is built for the region in backend/functions/.env.
    "app": Component(
        "v",
        ("app/", "gradle/", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "legal/", "backend/functions/.env"),
        "[skip release]",
    ),
    # Rules, indexes, functions, hosting (with the legal pages) and the Storage lifecycle rule.
    "backend": Component("backend-v", ("backend/", "legal/"), "[skip deploy]"),
}


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def tag_pattern(component: Component) -> re.Pattern[str]:
    return re.compile(rf"^{re.escape(component.prefix)}(\d+)\.(\d+)\.(\d+)$")


def latest(component: Component, exclude: str = "") -> tuple[str, Version] | None:
    """The highest version tag of [component], leaving out [exclude]."""
    pattern = tag_pattern(component)
    tags = [
        (t, tuple(map(int, m.groups())))
        for t in git("tag", "--list", f"{component.prefix}*").split()
        if t != exclude and (m := pattern.match(t))
    ]
    return max(tags, key=lambda t: t[1]) if tags else None  # type: ignore[return-value]


def bumped(version: Version, part: str, chosen: bool) -> tuple[Version, str]:
    """[version] raised by [part], and the part actually raised. Below 1.0.0, only a part chosen
    by hand leaves 0.x: a major change read from the commits raises the minor instead."""
    major, minor, patch = version
    if part == "major" and major == 0 and not chosen:
        part = "minor"
    if part == "major":
        return (major + 1, 0, 0), part
    if part == "minor":
        return (major, minor + 1, 0), part
    return (major, minor, patch + 1), part


def bump_from_messages(messages: str) -> str:
    if "[major]" in messages or "[breaking]" in messages:
        return "major"
    return "minor" if "[minor]" in messages else "patch"


def text(version: Version) -> str:
    return ".".join(map(str, version))


def is_ancestor(sha: str, tag: str) -> bool:
    # Exit code 1 means "not an ancestor", so a non-zero exit isn't an error here.
    return subprocess.run(["git", "merge-base", "--is-ancestor", sha, tag], capture_output=True, check=False).returncode == 0


def result(ship: bool, reason: str, version: str = "", tag: str = "") -> dict[str, str]:
    return {"ship": "true" if ship else "false", "version": version, "tag": tag, "reason": reason}


def plan(name: str, event: str, ref_name: str, sha: str, bump: str) -> dict[str, str]:
    component = COMPONENTS[name]
    last = latest(component)
    chosen = bump in PARTS

    if event == "push":
        match = tag_pattern(component).match(ref_name)
        if name != "app" or not match:
            raise SystemExit(f"::error::Tag {ref_name} must look like v1.2.3")
        version: Version = tuple(map(int, match.groups()))  # type: ignore[assignment]
        # The checkout already has the pushed tag: compare with the releases before it.
        last = latest(component, exclude=ref_name)
        if last and version <= last[1]:
            raise SystemExit(f"::error::Tag {ref_name} must be above the latest release, {last[0]}")
        return result(True, "tag pushed", text(version), ref_name)

    already = last is not None and is_ancestor(sha, last[0])
    since = f"{last[0]}..{sha}" if last else sha
    changed = bool(git("log", "--format=%H", since, "--", *component.paths)) if not already else False

    if event == "workflow_run":
        if component.skip_marker in git("log", "-1", "--format=%B", sha):
            return result(False, f"the commit says {component.skip_marker}")
        if already:
            return result(False, f"{sha[:7]} is already in {last[0]}")  # type: ignore[index]
        if last and not changed:
            return result(False, f"nothing in it changed since {last[0]}")

    if not chosen:
        if name == "app" and already:
            return result(False, f"{sha[:7]} is already in {last[0]}")  # type: ignore[index]
        if name == "app" and last and not changed:
            return result(False, f"the app hasn't changed since {last[0]}")
        if name == "backend" and last and not changed:
            # Asked for by hand: deploy again, as the version already deployed.
            return result(True, f"redeploying {last[0]}, unchanged since", text(last[1]))

    if not last:
        first = (1, 0, 0) if bump == "major" else FIRST_VERSION
        return result(True, "first version", text(first), component.prefix + text(first))

    part = bump if chosen else bump_from_messages(git("log", "--format=%B%x00", since, "--", *component.paths))
    version, raised = bumped(last[1], part, chosen)
    how = "chosen" if chosen else "from the commits"
    reason = f"{raised} after {last[0]}, {how}" + (" (a major change, kept below 1.0.0)" if raised != part else "")
    return result(True, reason, text(version), component.prefix + text(version))


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in COMPONENTS:
        raise SystemExit(f"Usage: {sys.argv[0]} {'|'.join(COMPONENTS)}")
    sha = os.environ.get("SHA") or git("rev-parse", "HEAD")
    planned = plan(sys.argv[1], os.environ["EVENT"], os.environ.get("REF_NAME", ""), sha, os.environ.get("BUMP", "").replace("auto", ""))
    planned["sha"] = sha
    lines = "".join(f"{key}={value}\n" for key, value in planned.items())
    if out := os.environ.get("GITHUB_OUTPUT"):
        with open(out, "a") as f:
            f.write(lines)
    sys.stdout.write(lines)


if __name__ == "__main__":
    main()
