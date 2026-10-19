#!/usr/bin/env bash
# Phase F3: the D-pad ring controls playback directly while the control bar is hidden.
# Run inside the toolchain container: scripts/dock bash scripts/f3-dpad.sh
# Needs the debug build installed (same key), the app connected, zone Büro selected. Only zone Büro is controlled; it ends paused.
source "$(dirname "$0")/phase4-lib.sh"

echo "== 0. preflight: zone Büro selected (controls on input, the default), Büro plays"
need_zone || exit 1
sh am start -n $PKG/.MainActivity >/dev/null; sleep 4
roon play --zone "$ROON_TEST_ZONE"; sleep 6
state | grep -o '"zone":"[^"]*","state":"[a-z]*","title":"[^"]*"'
need_zone || exit 1
sleep 6   # the bar is hidden (no key for more than 5 s)
adb -s "$DEV" logcat -c

echo "== 1. Maestro flow maestro/f3-dpad.yaml"
if maestro --device "$DEV" test maestro/f3-dpad.yaml > "$OUT/f3-dpad-maestro.txt" 2>&1; then echo "RESULT flow PASS"; else echo "RESULT flow FAIL"; grep -E "FAILED|Assertion|Error" "$OUT/f3-dpad-maestro.txt" | head -5; fi
find /home/dev/.maestro/tests -name 'p6-*.png' -exec cp {} "$OUT/" \; 2>/dev/null

echo "== 2. log evidence"
applog > "$OUT/f3-dpad-log.txt"
grep -E "key code=(19|20|21|22|23|66) .*action=0|dpad direct|control (send|ok|ignored)|track zone=\"$ROON_TEST_ZONE\"" "$OUT/f3-dpad-log.txt" | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /'

echo "== 3. park Büro paused"
state | grep -o '"zone":"[^"]*","state":"[a-z]*","title":"[^"]*"'
st=$(state | grep -o '"state":"[a-z]*"' | head -1 | cut -d'"' -f4)
[ "$st" = playing ] && { need_zone && roon pause --zone "$ROON_TEST_ZONE"; sleep 3; }
state | grep -o '"zone":"[^"]*","state":"[a-z]*","title":"[^"]*"'
