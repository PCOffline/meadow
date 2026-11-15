#!/usr/bin/env bash
# Draws a checkout's pull request pictures on the running emulator, and copies them to a folder.
#
#   .github/scripts/draw-pictures.sh <checkout> <folder>
#
# Runs the phone tests in dev.pcoffline.meadow.pictures (the gallery and the stories) with
# pictures=true. Used by the Pictures workflow, once for the pull request and once for its base.
# The default, basic, draws one theme by day, large text and a narrow phone. LOOKS adds more, such
# as "themes,night" (.github/scripts/pictures_scope.py), and LOOKS=all draws every look and weather.
#
# The emulator can stop partway through. So:
# - While the tests run, each picture is copied to the folder once it's written (every
#   COPY_EVERY seconds, 30 by default).
# - Before the tests run, the emulator is told what the folder already has (drawn.txt). The tests
#   skip it, so a fresh emulator draws only what an earlier one didn't.
# - When the tests pass, the folder gets a .complete file.
# Exits with the tests' result. Fails when the folder has no pictures, so nothing empty is published.
set -uo pipefail

CHECKOUT="$1"
OUT="$(mkdir -p "$2" && cd "$2" && pwd)"
DIR=/sdcard/Pictures/meadow-pictures
COPY_EVERY="${COPY_EVERY:-30}"

adb shell rm -rf "$DIR"
adb shell mkdir -p "$DIR"
drawn="$(mktemp)"
(cd "$OUT" && find . -type f ! -name .complete | sed 's|^\./||') > "$drawn"
if [ -s "$drawn" ]; then
  echo "Already drawn: $(wc -l < "$drawn") files"
  adb push "$drawn" "$DIR/drawn.txt" > /dev/null
fi

# Copies each file that had the same size on the last pass, so it's finished, and isn't here yet.
last=""
copy_written() {
  local now size name
  now="$(adb shell "cd $DIR && find . -type f -exec stat -c '%s %n' {} +" 2>/dev/null | tr -d '\r')" || return 0
  while read -r size name; do
    [ -n "$name" ] && [ "$name" != ./drawn.txt ] && [ ! -e "$OUT/$name" ] || continue
    grep -qxF "$size $name" <<< "$last" || continue
    mkdir -p "$(dirname "$OUT/$name")"
    adb pull "$DIR/$name" "$OUT/$name" > /dev/null 2>&1 || rm -f "$OUT/$name"
  done <<< "$now"
  last="$now"
}
(while sleep "$COPY_EVERY"; do copy_written; done) &
copier=$!

status=0
# The apps stay installed after the tests: the emulator has gone offline while Gradle removed them,
# and the pictures it held were lost.
(cd "$CHECKOUT" && ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.pictures=true \
  -Pandroid.testInstrumentationRunnerArguments.package=dev.pcoffline.meadow.pictures \
  -Pandroid.testInstrumentationRunnerArguments.looks="${LOOKS:-basic}" \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  --stacktrace) || status=$?
kill "$copier" 2> /dev/null
wait "$copier" 2> /dev/null
adb pull "$DIR/." "$OUT" > /dev/null || echo "Couldn't copy every picture from the emulator"
rm -f "$OUT/drawn.txt" "$drawn"
total=$(find "$OUT" -name '*.png' | wc -l)
echo "Has $total pictures in $OUT"
if [ "$total" = 0 ]; then
  echo "::error::No pictures were copied from the emulator."
  status=1
fi
if [ "$status" = 0 ]; then
  touch "$OUT/.complete"
else
  # Names the tests that failed, last in the log where it's easy to find.
  python3 "$(dirname "$0")/failed_tests.py" "$CHECKOUT/app/build/outputs/androidTest-results" || true
fi
exit "$status"
