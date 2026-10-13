#!/usr/bin/env bash
# Starts a headless Android emulator on a GitHub-hosted runner and waits until it has booted, or
# stops it. Used instead of a third-party action, so the repository can allow only actions by
# GitHub and verified creators.
#
#   android-emulator.sh start [--keep-animations]
#   android-emulator.sh stop
#
# API_LEVEL, TARGET, ARCH and PROFILE choose the system image and the device; the defaults match a
# Pixel 6 on Android 14. KVM must already be enabled.
set -euo pipefail

API_LEVEL=${API_LEVEL:-34}
TARGET=${TARGET:-google_apis}
ARCH=${ARCH:-x86_64}
# A phone's screen, not the default AVD's low density: text contrast is measured from pixels, and
# at mdpi thin strokes are all anti-aliased edge (real phones are ~420dpi).
PROFILE=${PROFILE:-pixel_6}
AVD=ci
SDK=${ANDROID_HOME:?ANDROID_HOME is not set}
TOOLS=$SDK/cmdline-tools/latest/bin
ADB=$SDK/platform-tools/adb

start() {
  local image="system-images;android-$API_LEVEL;$TARGET;$ARCH"
  yes | "$TOOLS/sdkmanager" --licenses > /dev/null || true
  "$TOOLS/sdkmanager" --install emulator platform-tools "$image" > /dev/null
  echo no | "$TOOLS/avdmanager" create avd --force --name "$AVD" --package "$image" --device "$PROFILE"

  # Later steps call adb directly; the runner image doesn't put it on the PATH.
  if [ -n "${GITHUB_PATH:-}" ]; then
    echo "$SDK/platform-tools" >> "$GITHUB_PATH"
  fi

  nohup "$SDK/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect -camera-back none -camera-front none > emulator.log 2>&1 &

  if ! timeout 300 "$ADB" wait-for-device; then
    echo "The emulator didn't start." >&2
    tail -n 50 emulator.log >&2 || true
    exit 1
  fi
  local booted=""
  for _ in $(seq 1 120); do
    booted=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    [ "$booted" = 1 ] && break
    sleep 5
  done
  if [ "$booted" != 1 ]; then
    echo "The emulator didn't finish booting in 10 minutes." >&2
    tail -n 50 emulator.log >&2 || true
    exit 1
  fi
  "$ADB" shell input keyevent 82 || true # Wake and unlock the screen.

  if [ "${1:-}" != "--keep-animations" ]; then
    for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
      "$ADB" shell settings put global "$scale" 0
    done
  fi
  echo "Emulator ready: $image on $PROFILE"
}

stop() {
  "$ADB" emu kill > /dev/null 2>&1 || true
}

case "${1:-}" in
  start) shift; start "$@" ;;
  stop) stop ;;
  *) echo "Usage: $0 start [--keep-animations] | stop" >&2; exit 2 ;;
esac
