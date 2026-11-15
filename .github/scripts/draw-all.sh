#!/usr/bin/env bash
# Draws the pictures for the Pictures workflow on the running emulator: the base's when DRAW_BASE
# is true, then this checkout's. A part that is complete is skipped, and this checkout's part goes
# on from what an earlier emulator drew (draw-pictures.sh).
#
#   .github/scripts/draw-all.sh
#
# The base is drawn on the first emulator only. Its code may be older than this checkout's and
# not go on from an earlier emulator: it would draw from the start, and stop at the same point,
# on each one. Without some of the base's pictures, those screens show as new.
set -uo pipefail

here="$(dirname "$0")"
OUT="${PICTURES:-build/pictures}"

adb shell settings put global hide_error_dialogs 1
sleep "${SETTLE_SECONDS:-20}"
if [ "${DRAW_BASE:-}" = true ] && [ ! -e "$OUT/before/.complete" ] && [ ! -e "$OUT/before/.tried" ]; then
  mkdir -p "$OUT/before" && touch "$OUT/before/.tried"
  # The base may not build, or may have no pictures yet: then every picture is new.
  "$here/draw-pictures.sh" ../base "$OUT/before" || echo "The base drew no pictures, or not all of them"
fi
if [ ! -e "$OUT/after/.complete" ]; then
  "$here/draw-pictures.sh" . "$OUT/after"
fi
