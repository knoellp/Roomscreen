#!/usr/bin/env bash
# Phase 2 device run (inside the toolchain container: scripts/dock bash scripts/phase2-device.sh).
# Zone "Büro" only: the app must already have Büro selected (debug broadcast SELECT_ZONE). Ends with Büro paused.
set -uo pipefail
: "${FIRE_TV_IP:?}"; : "${ROON_TEST_ZONE:?}"
DEV="$FIRE_TV_IP:5555"; PKG=io.github.knoellp.roomscreen
CLI=roon-cli/build/install/roon-cli/bin/roon-cli
OUT=build/device-artifacts; mkdir -p "$OUT"
adb connect "$DEV" >/dev/null
# Maestro keeps screenshots in its own (container-local) output folder: copy them out after every run.
grab() { find /home/dev/.maestro/tests -name 'p2-*.png' -exec cp {} "$OUT/" \; 2>/dev/null; }
log() { adb -s "$DEV" logcat -d -s Roomscreen:V; }
roon() { "$CLI" "$@" 2>&1 | grep -E "control|NOT ALLOWED|REFUSED|ERROR" || true; }

echo "== prepare: Büro paused"
roon play --zone "$ROON_TEST_ZONE"; sleep 4; roon pause --zone "$ROON_TEST_ZONE"; sleep 3

echo "== flow phase2.yaml"
adb -s "$DEV" logcat -c
maestro --device "$DEV" test maestro/phase2.yaml 2>&1 | grep -E "COMPLETED|FAILED|Flow|Assertion" | sed 's/^/  /'; grab
log | grep -E "key code=(85|89|90|87|88|19|23)|control |track zone=\"Büro\"" > "$OUT/phase2-log.txt"
cat "$OUT/phase2-log.txt"

echo "== flow phase2-settings.yaml"
adb -s "$DEV" logcat -c
maestro --device "$DEV" test maestro/phase2-settings.yaml 2>&1 | grep -E "FAILED|Flow|Assertion" | sed 's/^/  /'; grab
log | grep -E "settings AppSettings|control " 

echo "== restart check (force-stop, start, DUMP_STATE)"
adb -s "$DEV" shell am force-stop "$PKG"; sleep 2
adb -s "$DEV" logcat -c
adb -s "$DEV" shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 12
adb -s "$DEV" shell am broadcast -p "$PKG" -a io.github.knoellp.roomscreen.DUMP_STATE >/dev/null; sleep 1
log | grep "state {" | tail -1
adb -s "$DEV" exec-out screencap -p > "$OUT/p2-13-after-restart.png"

echo "== restore colour"
maestro --device "$DEV" test maestro/phase2-settings-restore.yaml 2>&1 | grep -E "FAILED|Flow|Assertion" | sed 's/^/  /'
adb -s "$DEV" shell am broadcast -p "$PKG" -a io.github.knoellp.roomscreen.DUMP_STATE >/dev/null; sleep 1
log | grep "state {" | tail -1

echo "== end: Büro paused"
roon pause --zone "$ROON_TEST_ZONE"; sleep 2
"$CLI" now-playing --zone "$ROON_TEST_ZONE" 2>&1 | grep "^zone "
