"""Decides whether the Release workflow publishes, and which version.

Reads the trigger from the environment and writes `release`, `version`, `tag`, `sha` and `reason`
to $GITHUB_OUTPUT (or prints them when run by hand).

- A pushed tag (v1.2.3) releases exactly that version.
- A manual run raises the latest version by the chosen part (patch, minor or major).
- An automatic run, after Build passes on main, raises the patch, or the minor or major when a
  commit since the last release says [minor] or [major]. It skips when the app didn't change since
  the last release, when the commit is already released, or when the commit says [skip release].

Environment: EVENT (push, workflow_dispatch or workflow_run), REF_NAME, SHA, BUMP.
"""
import os
import re
import subprocess
import sys

TAG = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
# Changes to anything else (docs, backend, workflows) don't change the APK.
APP_PATHS = ("app/", "gradle/", "build.gradle.kts", "settings.gradle.kts", "gradle.properties")
FIRST_VERSION = (1, 0, 0)


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def latest_tag() -> tuple[str, tuple[int, int, int]] | None:
    tags = [(t, tuple(map(int, m.groups()))) for t in git("tag", "--list", "v*").split() if (m := TAG.match(t))]
    return max(tags, key=lambda t: t[1]) if tags else None


def bumped(version: tuple[int, int, int], part: str) -> tuple[int, int, int]:
    major, minor, patch = version
    if part == "major":
        return major + 1, 0, 0
    if part == "minor":
        return major, minor + 1, 0
    return major, minor, patch + 1


def plan(event: str, ref_name: str, sha: str, bump: str) -> dict[str, str]:
    if event == "push":
        if not TAG.match(ref_name):
            raise SystemExit(f"::error::Tag {ref_name} must look like v1.2.3")
        return {"release": "true", "version": ref_name[1:], "tag": ref_name, "reason": "tag pushed"}

    last = latest_tag()
    if last and is_ancestor(sha, last[0]):
        return skip(f"{sha[:7]} is already in {last[0]}")

    since = f"{last[0]}..{sha}" if last else sha
    messages = git("log", "--format=%B%x00", since)

    if event == "workflow_run":
        if "[skip release]" in git("log", "-1", "--format=%B", sha):
            return skip("the commit says [skip release]")
        changed = git("diff", "--name-only", last[0], sha).splitlines() if last else ["app/"]
        if not any(path.startswith(APP_PATHS) for path in changed):
            return skip(f"the app hasn't changed since {last[0]}")
        bump = "major" if "[major]" in messages else "minor" if "[minor]" in messages else "patch"

    version = bumped(last[1], bump) if last else FIRST_VERSION
    text = ".".join(map(str, version))
    return {"release": "true", "version": text, "tag": f"v{text}", "reason": f"{bump} release" if last else "first release"}


def is_ancestor(sha: str, tag: str) -> bool:
    return subprocess.run(["git", "merge-base", "--is-ancestor", sha, tag], capture_output=True).returncode == 0


def skip(reason: str) -> dict[str, str]:
    return {"release": "false", "version": "", "tag": "", "reason": reason}


def main() -> None:
    sha = os.environ.get("SHA") or git("rev-parse", "HEAD")
    result = plan(os.environ["EVENT"], os.environ.get("REF_NAME", ""), sha, os.environ.get("BUMP") or "patch")
    result["sha"] = sha
    lines = "".join(f"{key}={value}\n" for key, value in result.items())
    if out := os.environ.get("GITHUB_OUTPUT"):
        with open(out, "a") as f:
            f.write(lines)
    sys.stdout.write(lines)


if __name__ == "__main__":
    main()
