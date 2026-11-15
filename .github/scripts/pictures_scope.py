"""
Which looks a pull request's gallery draws, for the Pictures workflow:

    python3 .github/scripts/pictures_scope.py <base commit>

Prints "all" when the pull request changes the themes, the sky or the weather: then every theme by
day and by night, and every weather. Otherwise prints "basic": one theme by day, large text and a
narrow phone, under a clear sky. A change elsewhere looks the same in each theme and weather.
"""

import subprocess
import sys

APP = "app/src/main/"
KOTLIN = APP + "java/dev/pcoffline/meadow/"

# What draws a theme, the sky, the weather or day and night.
EVERY_LOOK = (
    KOTLIN + "ui/theme/",
    KOTLIN + "ui/components/Ambient.kt",
    KOTLIN + "ui/components/Butterfly.kt",
    KOTLIN + "ui/components/Flowers.kt",
    KOTLIN + "ui/settings/Appearance.kt",
    KOTLIN + "data/Weather.kt",
    KOTLIN + "data/SunTimes.kt",
    APP + "res/values/colors.xml",
    APP + "res/values/themes.xml",
    APP + "res/values-night/",
)


def scope(changed: list[str]) -> str:
    """ "all" if any of the [changed] paths draws a look, otherwise "basic". """
    return "all" if any(path.startswith(EVERY_LOOK) for path in changed) else "basic"


def changed_since(base: str) -> list[str]:
    out = subprocess.run(["git", "diff", "--name-only", f"{base}...HEAD"], capture_output=True, text=True, check=True)
    return [line for line in out.stdout.splitlines() if line]


if __name__ == "__main__":
    print(scope(changed_since(sys.argv[1])))
