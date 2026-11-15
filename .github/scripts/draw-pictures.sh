#!/usr/bin/env bash
# Draws a checkout's pull request pictures on the running emulator, and copies them to a folder.
#
#   .github/scripts/draw-pictures.sh <checkout> <folder>
#
# Runs the phone tests in dev.pcoffline.meadow.pictures (the gallery and the stories) with
# pictures=true. Used by the Pictures workflow, once for the pull request and once for its base.
# Copies whatever was drawn, even when a test fails, and then exits with the tests' result. Fails
# when nothing could be copied (the emulator stopped), so no empty result is published.
set -uo pipefail

CHECKOUT="$1"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
DIR=/sdcard/Pictures/meadow-pictures

adb shell rm -rf "$DIR"
status=0
# The apps stay installed after the tests: the emulator has gone offline while Gradle removed them,
# and the pictures it held were lost.
(cd "$CHECKOUT" && ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.pictures=true \
  -Pandroid.testInstrumentationRunnerArguments.package=dev.pcoffline.meadow.pictures \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  --stacktrace) || status=$?
adb pull "$DIR/." "$OUT" || echo "Couldn't copy every picture from the emulator"
drawn=$(find "$OUT" -name '*.png' | wc -l)
echo "Drew $drawn pictures in $OUT"
if [ "$drawn" = 0 ]; then
  echo "::error::No pictures were copied from the emulator."
  status=1
fi
# Names the tests that failed, last in the log where it's easy to find.
if [ "$status" != 0 ]; then
  python3 "$(dirname "$0")/failed_tests.py" "$CHECKOUT/app/build/outputs/androidTest-results" || true
fi
exit "$status"
