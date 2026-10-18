#!/usr/bin/env bash
# Phase F3: manual host/port entry on the device, then the cached endpoint is forgotten and discovery finds the Core again.
# Run inside the toolchain container: scripts/dock bash scripts/f3-manual-entry.sh
# Needs the debug build installed (same key) and the app connected to the Core with zone Büro selected.
# Not run yet (device run pending). The Core host is read from the app's DUMP_STATE, never hardcoded. No transport command is sent.
source "$(dirname "$0")/phase4-lib.sh"
PATTERN='endpoint=|discovery|conn state|conn=|registry=|debug endpoint'

echo "== 0. preflight: connected, Core address from the app"
need_zone || exit 1
CORE=$(core_host); [ -n "$CORE" ] || { echo "ABORT: no Core address in the app state"; exit 1; }
echo "Core host (from DUMP_STATE): $CORE"; state | grep -o '"connection":"[^"]*"'

echo "== 1. SET_HOST to an unused address (TEST-NET-1): the app must show the connecting screen"
adb -s "$DEV" logcat -c
bcast SET_HOST --es host 192.0.2.1 --ei port 9330; sleep 6
state | grep -o '"screen":"[A-Za-z]*","connection":"[^"]*"'
shot p5-01-connecting

echo "== 2. Maestro: press 'Enter address manually', type the Core host, test"
maestro --device "$DEV" test -e CORE_HOST="$CORE" -e ROON_TEST_ZONE="$ROON_TEST_ZONE" maestro/f3-manual-entry.yaml 2>&1 | grep -E "COMPLETED|FAILED|Flow|Assertion|Error" | sed 's/^/  /'
find /home/dev/.maestro/tests -name 'p5-*.png' -exec cp {} "$OUT/" \; 2>/dev/null
sleep 8
ST=$(state); echo "$ST" | grep -o '"screen":"[A-Za-z]*","connection":"[^"]*","zones":[0-9]*,"endpoint":"[^"]*"'
echo "$ST" | grep -q "\"endpoint\":\"$CORE:9330\"" && echo "RESULT endpoint=$CORE:9330 saved: ok" || echo "RESULT endpoint check FAILED"
echo "$ST" | grep -q '"connection":"Connected"' && echo "RESULT Connected: ok" || echo "RESULT Connected check FAILED"
shot p5-03-manual-connected-state
applog | grep -E "$PATTERN" | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /'

echo "== 3. CLEAR_ENDPOINT (only host/port/manual) and restart: discovery must find the Core"
adb -s "$DEV" logcat -c
bcast CLEAR_ENDPOINT; sleep 2
sh am force-stop $PKG; sleep 2
sh am start -n $PKG/.MainActivity >/dev/null; sleep 25
applog | grep -E "$PATTERN" | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /'
applog | grep -q "discovery result=found" && echo "RESULT discovery result=found: ok" || echo "RESULT discovery check FAILED"
ST=$(state); echo "$ST" | grep -o '"screen":"[A-Za-z]*","connection":"[^"]*","zones":[0-9]*,"endpoint":"[^"]*"'
echo "$ST" | grep -q "\"endpoint\":\"$CORE:9330\"" && echo "RESULT endpoint saved again: ok" || echo "RESULT endpoint-saved check FAILED"
need_zone && echo "RESULT token and zone kept: ok (zone $ROON_TEST_ZONE, no new Enable)"
shot p5-04-after-clear-endpoint
