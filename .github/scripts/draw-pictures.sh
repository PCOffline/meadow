#!/usr/bin/env bash
# Draws a checkout's pull request pictures on the running emulator, and copies them to a folder.
#
#   .github/scripts/draw-pictures.sh <checkout> <folder>
#
# Runs the phone tests in dev.pcoffline.meadow.pictures (the gallery and the stories) with
# pictures=true. Used by the Pictures workflow, once for the pull request and once for its base.
# Copies whatever was drawn, even when a test fails, and then exits with the tests' result.
set -uo pipefail

CHECKOUT="$1"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
DIR=/sdcard/Pictures/meadow-pictures

adb shell rm -rf "$DIR"
status=0
(cd "$CHECKOUT" && ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.pictures=true \
  -Pandroid.testInstrumentationRunnerArguments.package=dev.pcoffline.meadow.pictures \
  --stacktrace) || status=$?
adb pull "$DIR/." "$OUT" || true
echo "Drew $(find "$OUT" -name '*.png' | wc -l) pictures in $OUT"
exit "$status"
