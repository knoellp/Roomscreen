#!/usr/bin/env bash
# Diagnostics overlay and touch delivery on the device (docs/PLAN-F3b.md sections 2.1 and 2.4).
# Run inside the toolchain container: scripts/dock bash scripts/f3b-diagnostics.sh
# Needs the debug build installed (same key) and the app on Now Playing. No transport command is sent; nothing is played or paused.
# Checks: a tap injected with `input tap` reaches the app (log line `touch action=down`), OPEN_DIAGNOSTICS opens the overlay
# (DUMP_STATE diagnostics_open=true, screenshot), D-pad Down scrolls it without side effects, Back closes it, five taps on the
# bottom-right corner within 3 s open it, four do not, and the app requests no audio focus.
source "$(dirname "$0")/phase4-lib.sh"
FAILED=0
check() { if [ "$2" = "$3" ]; then echo "RESULT $1 PASS ($3)"; else echo "RESULT $1 FAIL (expected $2, got $3)"; FAILED=1; fi; }
diag_open() { state | grep -o '"diagnostics_open":[a-z]*' | head -1 | cut -d: -f2; }
# Corner box: 160 x 60 dp at the bottom right, 48 dp from the right edge and 14 dp from the bottom (NowPlaying.kt); xhdpi = 2 px/dp.
TAP_X=1700; TAP_Y=1000
tap() { sh input tap "$1" "$2"; }
# `input tap` needs about 0.75 s per call on the stick, too slow for five taps within 3 s. A monkey script injects the
# pointer events directly (down, up, 200 ms wait per tap; the run takes about 1.2 s).
monkey_taps() { # $1 = number of taps
  { echo "type= raw events"; echo "count= $(( $1 * 3 ))"; echo "speed= 1.0"; echo "start data >>"
    for _ in $(seq "$1"); do
      echo "DispatchPointer(0,0,0,$TAP_X,$TAP_Y,1,1,0,1,1,0,0)"; echo "DispatchPointer(0,0,1,$TAP_X,$TAP_Y,1,1,0,1,1,0,0)"; echo "UserWait(200)"
    done; } > "$OUT/taps$1.txt"
  adb -s "$DEV" push "$OUT/taps$1.txt" /data/local/tmp/taps.txt >/dev/null
  sh monkey -f /data/local/tmp/taps.txt 1 2>&1 | grep -E "Events injected" || true
}
# Back on Now Playing without the overlay leaves the app, so Back is only pressed while the overlay is open.
back_if_open() { if [ "$(diag_open)" = true ]; then sh input keyevent 4; sleep 1; check "$1" false "$(diag_open)"; else echo "SKIP $1 (overlay not open)"; fi; }

echo "== 0. preflight"
state | grep -q '"screen":"NowPlaying"' || { echo "ABORT: app is not on Now Playing"; exit 1; }
check "overlay closed at start" false "$(diag_open)"
adb -s "$DEV" logcat -c

echo "== 1. touch delivery (input tap 960 540)"
tap 960 540; sleep 1
applog | grep -E "touch action=down" | tail -1
applog | grep -q "touch action=down x=960 y=540" && echo "RESULT touch reaches the app PASS" || { echo "RESULT touch reaches the app FAIL"; FAILED=1; }

echo "== 2. OPEN_DIAGNOSTICS, scroll, Back"
bcast OPEN_DIAGNOSTICS; sleep 2
check "overlay open after OPEN_DIAGNOSTICS" true "$(diag_open)"
shot p9-01-diagnostics
sh input keyevent 20; sh input keyevent 20; sleep 1   # Down x2 scrolls the log; must not reach the D-pad control
check "overlay still open after Down x2" true "$(diag_open)"
applog | grep -qE "dpad direct|volume panel=open" && { echo "RESULT keys stayed in the overlay FAIL"; FAILED=1; } || echo "RESULT keys stayed in the overlay PASS"
sh input keyevent 4; sleep 1
check "overlay closed after Back" false "$(diag_open)"
state | grep -q '"screen":"NowPlaying"' && echo "RESULT app still on Now Playing PASS" || { echo "RESULT app still on Now Playing FAIL"; FAILED=1; }

echo "== 3. four corner taps do not open, five do"
monkey_taps 4; sleep 1
check "overlay closed after 4 taps" false "$(diag_open)"
sleep 3   # window over
monkey_taps 5; sleep 2
check "overlay open after 5 taps" true "$(diag_open)"
[ "$(diag_open)" = true ] && shot p9-02-diagnostics-gesture
back_if_open "overlay closed after Back (gesture)"

echo "== 4. audio focus and device facts"
FOCUS=$(sh dumpsys audio | grep -ic "$PKG" || true)
check "no audio focus entry for the app" 0 "$FOCUS"
echo "ro.build.version.fireos=$(sh getprop ro.build.version.fireos | tr -d '\r') density=$(sh wm density | tr -d '\r') size=$(sh wm size | tr -d '\r')"

echo "== 5. log evidence"
applog > "$OUT/f3b-diagnostics-log.txt"
grep -E "touch action|diagnostics" "$OUT/f3b-diagnostics-log.txt" | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /' | head -20
[ "$FAILED" = 0 ] && echo "RESULT all PASS" || echo "RESULT some FAIL"
exit $FAILED
