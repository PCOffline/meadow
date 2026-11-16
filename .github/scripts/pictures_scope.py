"""
What a pull request's pictures show, for the Pictures workflow:

    python3 .github/scripts/pictures_scope.py looks <base commit>
    python3 .github/scripts/pictures_scope.py stories <base commit>

"looks" prints the looks the gallery draws, beyond one theme by day, large text and a narrow phone
under a clear sky: "themes" (each theme by day) when the pull request changes the themes or the
flowers, "night" (each theme by night) when it changes the night or the sky, and "weather" (each
weather) when it changes the weather. It prints "basic" when it changes none of them. A change
elsewhere looks the same in each look.

"stories" prints the stories that the pull request adds or changes, by class name: only those show
in its description. It prints "*" (each story that looks different) when the pull request changes
how every story is drawn.
"""

import subprocess
import sys

APP = "app/src/main/"
KOTLIN = APP + "java/dev/pcoffline/meadow/"
STORIES = "app/src/androidTest/java/dev/pcoffline/meadow/pictures/stories/"

# What draws each look: its colours, flowers and sky.
LOOKS = {
    "themes": (
        KOTLIN + "ui/theme/",
        KOTLIN + "ui/components/Butterfly.kt",
        KOTLIN + "ui/components/Flowers.kt",
        KOTLIN + "ui/components/flora/",
        KOTLIN + "ui/settings/Appearance.kt",
        APP + "res/values/colors.xml",
        APP + "res/values/themes.xml",
    ),
    "night": (
        KOTLIN + "ui/components/Ambient.kt",
        KOTLIN + "data/SunTimes.kt",
        APP + "res/values-night/",
    ),
    "weather": (
        KOTLIN + "ui/components/Ambient.kt",
        KOTLIN + "data/Weather.kt",
    ),
}

# How every story is drawn: a change here can change each story's pictures.
EVERY_STORY = (STORIES + "Story.kt",)


def looks(changed: list[str]) -> str:
    """The looks that the [changed] paths draw, such as "themes,night", or "basic"."""
    found = [look for look, paths in LOOKS.items() if any(path.startswith(paths) for path in changed)]
    return ",".join(found) or "basic"


def stories(changed: list[str]) -> str:
    """The stories that the [changed] paths add or change, such as "ThemePreviewStory", or "*"."""
    if any(path.startswith(EVERY_STORY) for path in changed):
        return "*"
    names = {
        path[len(STORIES):-len(".kt")]
        for path in changed
        if path.startswith(STORIES) and path.endswith(".kt") and "/" not in path[len(STORIES):]
    }
    return ",".join(sorted(names))


def changed_since(base: str) -> list[str]:
    out = subprocess.run(["git", "diff", "--name-only", f"{base}...HEAD"], capture_output=True, text=True, check=True)
    return [line for line in out.stdout.splitlines() if line]


if __name__ == "__main__":
    what, base = sys.argv[1], sys.argv[2]
    print({"looks": looks, "stories": stories}[what](changed_since(base)))
