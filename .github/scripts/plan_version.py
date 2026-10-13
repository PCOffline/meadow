"""Decides whether the Deliver workflow ships the app or the backend, and at which version.

The app and the backend are versioned separately, each with semantic versioning, because each
ships on its own: the app as a GitHub release tagged v0.4.2, the backend as a deploy tagged
backend-v0.7.0. A tag marks what was shipped, so the next run knows what changed since.

    python3 plan_version.py app|backend

Writes `ship`, `version`, `tag` (empty when nothing new is tagged) and `reason` to $GITHUB_OUTPUT
(or prints them when run by hand). Environment: EVENT (push, workflow_dispatch or workflow_run),
REF_NAME, SHA, BUMP (patch, minor or major; empty to read it from the commit messages).

- A pushed tag (v0.4.2) releases exactly that version of the app.
- By hand: the app goes up by the chosen part; the backend deploys even when unchanged, as the
  version it already has.
- Automatically, after Build passes on main: ships only when the component changed since its last
  tag, unless the last commit says [skip release] (app) or [skip deploy] (backend). Commits that
  touch the component decide the bump: [major] or [breaking] for incompatible changes, [minor] for
  new features, anything else a patch.

Meadow stays below 1.0.0 for now (MAX_MAJOR). Under SemVer's rule for 0.x versions, an
incompatible change raises the minor part (0.4.2 → 0.5.0) instead of the major.
"""
import os
import re
import subprocess
import sys
from dataclasses import dataclass

MAX_MAJOR = 0
FIRST_VERSION = (0, 1, 0)

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


def latest(component: Component) -> tuple[str, Version] | None:
    pattern = tag_pattern(component)
    tags = [(t, tuple(map(int, m.groups()))) for t in git("tag", "--list", f"{component.prefix}*").split() if (m := pattern.match(t))]
    return max(tags, key=lambda t: t[1]) if tags else None  # type: ignore[return-value]


def bumped(version: Version, part: str) -> tuple[Version, str]:
    """[version] raised by [part], and the part actually raised: below 1.0.0, major becomes minor."""
    major, minor, patch = version
    if part == "major" and major + 1 > MAX_MAJOR:
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
    return subprocess.run(["git", "merge-base", "--is-ancestor", sha, tag], capture_output=True).returncode == 0


def result(ship: bool, reason: str, version: str = "", tag: str = "") -> dict[str, str]:
    return {"ship": "true" if ship else "false", "version": version, "tag": tag, "reason": reason}


def plan(name: str, event: str, ref_name: str, sha: str, bump: str) -> dict[str, str]:
    component = COMPONENTS[name]
    last = latest(component)

    if event == "push":
        match = tag_pattern(component).match(ref_name)
        if name != "app" or not match:
            raise SystemExit(f"::error::Tag {ref_name} must look like v0.1.2")
        version: Version = tuple(map(int, match.groups()))  # type: ignore[assignment]
        if version[0] > MAX_MAJOR:
            raise SystemExit(f"::error::Meadow stays below {MAX_MAJOR + 1}.0.0 for now; tag {ref_name} is too high")
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

    if name == "app" and already:
        return result(False, f"{sha[:7]} is already in {last[0]}")  # type: ignore[index]
    if name == "backend" and last and not changed:
        # Asked for by hand: deploy again, as the version already deployed.
        return result(True, f"redeploying {last[0]}, unchanged since", text(last[1]))

    if not last:
        return result(True, "first version", text(FIRST_VERSION), component.prefix + text(FIRST_VERSION))

    part = bump or bump_from_messages(git("log", "--format=%B%x00", since, "--", *component.paths))
    version, raised = bumped(last[1], part)
    reason = f"{raised} after {last[0]}" + (" (a major change, while below 1.0.0)" if raised != part else "")
    return result(True, reason, text(version), component.prefix + text(version))


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in COMPONENTS:
        raise SystemExit(f"Usage: {sys.argv[0]} {'|'.join(COMPONENTS)}")
    sha = os.environ.get("SHA") or git("rev-parse", "HEAD")
    planned = plan(sys.argv[1], os.environ["EVENT"], os.environ.get("REF_NAME", ""), sha, os.environ.get("BUMP", ""))
    planned["sha"] = sha
    lines = "".join(f"{key}={value}\n" for key, value in planned.items())
    if out := os.environ.get("GITHUB_OUTPUT"):
        with open(out, "a") as f:
            f.write(lines)
    sys.stdout.write(lines)


if __name__ == "__main__":
    main()
