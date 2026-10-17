#!/usr/bin/env bash
# Runs inside the toolchain container (see deploy-test.sh).
set -euo pipefail
FLOW=""; WAIT=15
while [ $# -gt 0 ]; do
  case "$1" in
    --flow) FLOW="$2"; shift 2 ;;
    --wait) WAIT="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
: "${FIRE_TV_IP:?FIRE_TV_IP not set (config/local.env)}"
DEV="$FIRE_TV_IP:5555"
OUT=build/device-artifacts
STAMP=$(date +%Y%m%d-%H%M%S)
PKG=io.github.knoellp.roomscreen
mkdir -p "$OUT"

adb connect "$DEV"
adb -s "$DEV" get-state | grep -q device || { echo "device not authorized/reachable" >&2; exit 1; }
adb -s "$DEV" shell getprop ro.build.version.sdk

./gradlew --no-daemon :app:assembleDebug -q
adb -s "$DEV" install -r app/build/outputs/apk/debug/app-debug.apk

adb -s "$DEV" logcat -c
adb -s "$DEV" shell am start -n "$PKG/.MainActivity"
sleep "$WAIT"

if [ -n "$FLOW" ]; then
  maestro --device "$DEV" test "$FLOW" || echo "maestro flow failed (continuing to collect artifacts)"
fi

adb -s "$DEV" exec-out screencap -p > "$OUT/screen-$STAMP.png"
adb -s "$DEV" logcat -d -s Roomscreen:V > "$OUT/logcat-$STAMP.txt"
echo "artifacts: $OUT/screen-$STAMP.png $OUT/logcat-$STAMP.txt"
tail -n 25 "$OUT/logcat-$STAMP.txt"
