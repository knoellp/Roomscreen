#!/usr/bin/env bash
# Volume panel on the device (docs/SPEC.md section 6.1; maestro/f4-volume.yaml). Every flow step opens the panel itself
# (the panel closes 8 s after the last key; see the notes in the flow), so the DUMP_STATE panel check comes right after the step.
# Run inside the toolchain container: scripts/dock bash scripts/f4-volume.sh
# Needs the debug build installed (same key), the app connected, zone Büro selected, roon-cli installed (./gradlew :roon-cli:installDist).
# Only zone Büro is touched. Its volume is read first and set back to exactly that value at the end; Büro is not started or stopped.
source "$(dirname "$0")/phase4-lib.sh"

vol_state() { state | grep -o '"volume":\[[^]]*\]' | head -1; }
vol_value() { state | grep -o '"name":"'"$ROON_TEST_ZONE"'","value":[-0-9.]*' | head -1 | grep -o '[-0-9.]*$'; }
panel() { state | grep -o '"volume_panel":-\?[0-9]*' | head -1 | cut -d: -f2; }
cli_value() { "$CLI" zones 2>&1 | grep "zone name=\"$ROON_TEST_ZONE\"" | grep -o 'volume=[^ ]*' | head -1; }
flow() {
  maestro --device "$DEV" test -e STEP="$1" -e VALUE="${2:-x}" maestro/f4-volume.yaml > "$OUT/f4-volume-$1.txt" 2>&1 && echo "RESULT flow $1 PASS" || { echo "RESULT flow $1 FAIL"; grep -E "FAILED|Assertion|Error" "$OUT/f4-volume-$1.txt" | head -5; FAILED=1; }
  find /home/dev/.maestro/tests -name 'p7-*.png' -exec cp {} "$OUT/" \; 2>/dev/null
}
check() { # $1 name, $2 expected, $3 actual
  if [ "$2" = "$3" ]; then echo "RESULT $1 PASS ($3)"; else echo "RESULT $1 FAIL (expected $2, got $3)"; FAILED=1; fi
}
FAILED=0

echo "== 0. preflight: zone Büro selected, volume object present"
need_zone || exit 1
sh am start -n $PKG/.MainActivity >/dev/null; sleep 4
START=$(vol_value); echo "start volume=$START"
[ -n "$START" ] || { echo "ABORT: no volume object for $ROON_TEST_ZONE"; exit 1; }
[ "$(panel)" = "-1" ] || { echo "ABORT: panel already open"; exit 1; }
sleep 6   # the control bar is hidden (no key for more than 5 s)
adb -s "$DEV" logcat -G 4M 2>/dev/null || true   # Maestro logs a lot; the default buffer rotates within a minute
adb -s "$DEV" logcat -c

echo "== 1. open the panel through the bar (Up, Right, Right, Center)"
flow open   # the flow asserts the panel row and the hidden bar itself (the panel may close before DUMP_STATE runs)
OPENED=$(applog | grep -c "volume panel=open")
[ "$OPENED" -ge 1 ] && echo "RESULT panel opened (log) PASS" || { echo "RESULT panel opened (log) FAIL"; FAILED=1; }

echo "== 2. Right x3: +3, app and Core agree"
WANT=$((START + 3))
flow up3 "$WANT"
sleep 2
check "DUMP_STATE volume after +3" "$WANT" "$(vol_value)"
echo "roon-cli: $(cli_value)"
check "roon-cli volume after +3" "volume=$ROON_TEST_ZONE:$WANT" "$(cli_value)"

echo "== 3. Left x3: back to the start value"
flow down3 "$START"
sleep 2
check "DUMP_STATE volume after -3" "$START" "$(vol_value)"
check "roon-cli volume after -3" "volume=$ROON_TEST_ZONE:$START" "$(cli_value)"

echo "== 4. Back closes the panel and the app stays"
flow back
check "panel closed (volume_panel=-1)" -1 "$(panel)"
state | grep -q '"screen":"NowPlaying"' && echo "RESULT app still on Now Playing PASS" || { echo "RESULT app still on Now Playing FAIL"; FAILED=1; }

echo "== 5. auto close after 8 s without a key"
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.OPEN_VOLUME >/dev/null; sleep 2
check "panel open after OPEN_VOLUME" 0 "$(panel)"
sleep 9
check "panel closed after 8 s" -1 "$(panel)"

echo "== 6. log evidence"
applog > "$OUT/f4-volume-log.txt"
grep -E "volume (panel|send|ok|failed)" "$OUT/f4-volume-log.txt" | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /'

echo "== 7. end value"
check "volume restored" "$START" "$(vol_value)"
[ "$FAILED" = 0 ] && echo "RESULT all PASS" || echo "RESULT some FAIL"
exit $FAILED
