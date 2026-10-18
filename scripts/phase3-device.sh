#!/usr/bin/env bash
# Phase 3 device run (inside the toolchain container: scripts/dock bash scripts/phase3-device.sh).
# Needs the debug APK installed (scripts/deploy-test.sh) and roon-cli installed (./gradlew :roon-cli:installDist).
# Zone "Büro" only: the app's selected zone is checked before every transport command. Ends with Büro paused and lyrics off.
set -uo pipefail
: "${FIRE_TV_IP:?}"; : "${ROON_TEST_ZONE:?}"
DEV="$FIRE_TV_IP:5555"; PKG=io.github.knoellp.roomscreen
CLI=roon-cli/build/install/roon-cli/bin/roon-cli
OUT=build/device-artifacts; mkdir -p "$OUT"
adb connect "$DEV" >/dev/null
sh() { adb -s "$DEV" shell "$@"; }
grab() { find /home/dev/.maestro/tests -name 'p3-*.png' -exec cp {} "$OUT/" \; 2>/dev/null; }
applog() { adb -s "$DEV" logcat -d -s Roomscreen:V; }
lyr() { applog | grep -E "lyrics (fetch|button)" | sed 's/^/  /'; }
roon() { "$CLI" "$@" 2>&1 | grep -E "control|NOT ALLOWED|REFUSED|ERROR" || true; }
mem() { sh dumpsys meminfo $PKG | grep -E "Java Heap:|Native Heap:|Graphics:|TOTAL:" | tr -s ' ' | tr '\n' '|' | sed 's/^/  /'; echo; }
state() { sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.DUMP_STATE >/dev/null; sleep 1; applog | grep "state {" | tail -1; }
need_buero() { state | grep -q '"zone":"Büro"' || { echo "ABORT: selected zone is not Büro"; roon pause --zone "$ROON_TEST_ZONE"; exit 1; }; }
next_key() { need_buero; sh input keyevent KEYCODE_MEDIA_NEXT; }
flow() { maestro --device "$DEV" test "$1" 2>&1 | grep -E "COMPLETED|FAILED|Flow|Assertion|Error" | sed 's/^/  /'; grab; }
# Maestro on this stick is timing-sensitive (the bar hides 5 s after the last key): run a flow until the app state shows its effect.
# $1 flow file, $2 pattern that DUMP_STATE must contain afterwards. The flows toggle, so a run that already had its effect is never repeated.
flow_until() {
  for try in 1 2 3; do
    flow "$1"
    if state | grep -q "$2"; then echo "  state check ok: $2"; return 0; fi
    echo "  state check failed ($2), attempt $try"
  done
  return 1
}

echo "== 0. preflight: app up, zone Büro selected, lyrics off"
sh am start -n $PKG/.MainActivity >/dev/null; sleep 8
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SELECT_ZONE --es name "$ROON_TEST_ZONE" >/dev/null; sleep 2
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SET_LRCLIB --es value on >/dev/null; sleep 1
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SET_BACKGROUND --es value cover >/dev/null; sleep 1   # the lrclib flows count the sharpness row
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SET_LYRICS --es value off >/dev/null; sleep 2
need_buero; state
roon pause --zone "$ROON_TEST_ZONE"; sleep 3

echo "== 1. lyrics off: play + 3 track changes, expect NO lyrics fetch line"
adb -s "$DEV" logcat -c
roon play --zone "$ROON_TEST_ZONE"; sleep 6
for i in 1 2 3; do next_key; sleep 4; done
echo "  lyrics lines (expected none):"; lyr
echo "  track lines:"; applog | grep -E 'track zone="Büro" state=playing' | sed 's/^/  /' | tail -4

echo "== 2. meminfo, lyrics OFF: 10 track changes, 4 s apart"
sleep 3; echo " before"; mem
for i in $(seq 1 10); do next_key; sleep 4; done
echo " after 10"; mem; sleep 30; echo " 30 s later"; mem

echo "== 3. maestro: Liedtexte button on (Büro paused for the navigation, then started with the app's media key)"
need_buero; roon pause --zone "$ROON_TEST_ZONE"; sleep 3
adb -s "$DEV" logcat -c
flow_until maestro/phase3-lyrics-on.yaml '"lyrics_on":true'
lyr
state | grep -o '"lyrics_on":[a-z]*,"lyrics":"[a-z:0-9]*"' | sed 's/^/  after flow: /'
state | grep -q '"lyrics_on":true' || echo "WARNING: lyrics are not on after the flow"
echo "  two more screenshots 10 s apart while Büro plays (active line must change)"
adb -s "$DEV" exec-out screencap -p > "$OUT/p3-03c-lyrics-a.png"; sleep 10
adb -s "$DEV" exec-out screencap -p > "$OUT/p3-03d-lyrics-b-10s-later.png"

echo "== 4. cancellation: 6 quick track changes, 1.2 s apart (lyrics on)"
adb -s "$DEV" logcat -c
for i in $(seq 1 6); do next_key; sleep 1.2; done
sleep 8
lyr
adb -s "$DEV" exec-out screencap -p > "$OUT/p3-04-after-burst.png"

echo "== 5. restart: force-stop, start, lyrics mode must still be on"
sh am force-stop $PKG; sleep 2
adb -s "$DEV" logcat -c
sh am start -n $PKG/.MainActivity >/dev/null; sleep 14
state
lyr
adb -s "$DEV" exec-out screencap -p > "$OUT/p3-08-after-restart.png"

echo "== 6. meminfo, lyrics ON: 10 track changes, 4 s apart"
adb -s "$DEV" logcat -c
echo " before"; mem
for i in $(seq 1 10); do next_key; sleep 4; done
echo " after 10"; mem; sleep 30; echo " 30 s later"; mem
lyr
adb -s "$DEV" exec-out screencap -p > "$OUT/p3-09-after-10-changes.png"

echo "== 7. LRCLIB setting off: button gone, no lyrics fetch on track change"
need_buero; roon pause --zone "$ROON_TEST_ZONE"; sleep 3
flow_until maestro/phase3-lrclib-off.yaml 'lrclib=false'
# Make sure the Lyrics button is ON while LRCLIB is off, so that "no request" is caused by the setting alone.
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SET_LYRICS --es value on >/dev/null; sleep 2
state | grep -o '"lyrics_on":[a-z]*,"lyrics":"[a-z:0-9]*"' | sed 's/^/  before track changes: /'
state | grep -o 'lrclib=[a-z]*' | sed 's/^/  before track changes: /'
need_buero; roon play --zone "$ROON_TEST_ZONE"; sleep 5
adb -s "$DEV" logcat -c
for i in 1 2; do next_key; sleep 5; done
echo "  lyrics lines with LRCLIB off (expected none):"; lyr
echo "  track lines:"; applog | grep -E 'track zone="Büro" state=playing' | sed 's/^/  /' | tail -3
adb -s "$DEV" exec-out screencap -p > "$OUT/p3-06b-lrclib-off-track.png"
echo "== 7b. LRCLIB setting back on"
need_buero; roon pause --zone "$ROON_TEST_ZONE"; sleep 3
adb -s "$DEV" logcat -c
flow_until maestro/phase3-lrclib-on.yaml 'lrclib=true'
need_buero; roon play --zone "$ROON_TEST_ZONE"; sleep 6
echo "  lyrics lines after switching back on (lookup for the current track):"; lyr
state

echo "== 8. end: lyrics button back to off, Büro paused"
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SET_LYRICS --es value off >/dev/null; sleep 2
need_buero
roon pause --zone "$ROON_TEST_ZONE"; sleep 3
state
"$CLI" now-playing --zone "$ROON_TEST_ZONE" 2>&1 | grep "^zone "
