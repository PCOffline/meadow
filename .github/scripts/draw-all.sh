#!/usr/bin/env bash
# Draws the pictures for the Pictures workflow on the running emulator: the base's when DRAW_BASE
# is true, then this checkout's. A part that is complete is skipped, and a part that an earlier
# emulator started goes on from what it drew (draw-pictures.sh).
#
#   .github/scripts/draw-all.sh
set -uo pipefail

adb shell settings put global hide_error_dialogs 1
sleep 20
if [ "${DRAW_BASE:-}" = true ] && [ ! -e build/pictures/before/.complete ]; then
  # The base may not build, or may have no pictures yet: then every picture is new.
  .github/scripts/draw-pictures.sh ../base build/pictures/before || echo "The base drew no pictures, or not all of them"
fi
if [ ! -e build/pictures/after/.complete ]; then
  .github/scripts/draw-pictures.sh . build/pictures/after
fi
