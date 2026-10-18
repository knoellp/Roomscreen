#!/usr/bin/env bash
# Runs inside the toolchain container (see deploy-test.sh).
set -euo pipefail
FLOW=""; WAIT=15; VIA=""; ALLOW_KEY_CHANGE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --flow) FLOW="$2"; shift 2 ;;
    --wait) WAIT="$2"; shift 2 ;;
    --via-roon) VIA="$2"; shift 2 ;;
    --allow-key-change) ALLOW_KEY_CHANGE=1; shift ;;
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
APK=app/build/outputs/apk/debug/app-debug.apk

# Signature check before installing: the build and the installed app must carry the release key
# (config/release-fingerprint.txt). `install -r` over a different key fails, and the only way out would be an
# uninstall, which loses the Roon token. This script never uninstalls. --allow-key-change only turns the abort into a warning.
APKSIGNER=/opt/android-sdk/build-tools/36.0.0/apksigner
EXPECTED=$(grep -Eio '^[0-9a-f]{64}$' config/release-fingerprint.txt | head -n1 | tr 'A-F' 'a-f' || true)
[ -n "$EXPECTED" ] || { echo "config/release-fingerprint.txt missing or without a SHA-256 digest" >&2; exit 1; }
cert_digest() { "$APKSIGNER" verify --print-certs "$1" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1; }
key_mismatch() { # $1 = what, $2 = digest found
  echo "SIGNATURE MISMATCH: $1 is signed with $2, expected $EXPECTED (config/release-fingerprint.txt)." >&2
  if [ "$ALLOW_KEY_CHANGE" = 1 ]; then
    echo "WARNING: --allow-key-change given, continuing. The install will fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE; this script does not uninstall." >&2
  else
    echo "Aborting. Use config/signing.env for the build; an installed app with another key needs a one-time uninstall (new Enable in Roon), ask first, then pass --allow-key-change." >&2
    exit 1
  fi
}
BUILT=$(cert_digest "$APK")
[ "$BUILT" = "$EXPECTED" ] || key_mismatch "the built APK" "${BUILT:-unknown}"
if adb -s "$DEV" shell dumpsys package "$PKG" | grep -q "versionName="; then
  INSTALLED_PATH=$(adb -s "$DEV" shell pm path "$PKG" | head -n1 | sed 's/^package://' | tr -d '\r')
  adb -s "$DEV" pull "$INSTALLED_PATH" "$OUT/installed-$STAMP.apk" >/dev/null
  INSTALLED=$(cert_digest "$OUT/installed-$STAMP.apk")
  rm -f "$OUT/installed-$STAMP.apk"
  [ "$INSTALLED" = "$EXPECTED" ] || key_mismatch "the installed app" "${INSTALLED:-unknown}"
  echo "signature check ok: installed app and build carry $EXPECTED"
else
  echo "app not installed yet; signature of the build checked ($BUILT)"
fi
adb -s "$DEV" install -r "$APK"

adb -s "$DEV" logcat -c
if [ -n "$VIA" ]; then
  # Debug hook: write the "via roon" setting before the first start (works while the app is stopped).
  adb -s "$DEV" shell am broadcast --include-stopped-packages -p "$PKG" -a io.github.knoellp.roomscreen.SET_VIA_ROON --es value "$VIA" >/dev/null
  sleep 3
  adb -s "$DEV" logcat -d -s Roomscreen:V | grep "via_roon" || echo "WARNING: via_roon broadcast not confirmed" >&2
fi
adb -s "$DEV" shell am start -n "$PKG/.MainActivity"
sleep "$WAIT"

if [ -n "$FLOW" ]; then
  maestro --device "$DEV" test "$FLOW" || echo "maestro flow failed (continuing to collect artifacts)"
fi

adb -s "$DEV" exec-out screencap -p > "$OUT/screen-$STAMP.png"
adb -s "$DEV" logcat -d -s Roomscreen:V > "$OUT/logcat-$STAMP.txt"
echo "artifacts: $OUT/screen-$STAMP.png $OUT/logcat-$STAMP.txt"
tail -n 25 "$OUT/logcat-$STAMP.txt"
