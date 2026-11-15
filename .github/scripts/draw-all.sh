#!/usr/bin/env bash
# Draws the pictures for the Pictures workflow on the running emulator: the base's when DRAW_BASE
# is true, then this checkout's. A part that already has pictures is skipped, so a second run on a
# fresh emulator draws only what the first one lost.
#
#   .github/scripts/draw-all.sh
set -uo pipefail

has_pictures() { [ -n "$(find "$1" -name '*.png' -print -quit 2>/dev/null)" ]; }

adb shell settings put global hide_error_dialogs 1
sleep 20
if [ "${DRAW_BASE:-}" = true ] && ! has_pictures build/pictures/before; then
  # The base may not build, or may have no pictures yet: then every picture is new.
  .github/scripts/draw-pictures.sh ../base build/pictures/before || echo "The base drew no pictures, or not all of them"
fi
if ! has_pictures build/pictures/after; then
  .github/scripts/draw-pictures.sh . build/pictures/after
fi
