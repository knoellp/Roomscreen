#!/usr/bin/env bash
# Shared helpers for the Phase 4 device scripts. Source it inside the toolchain container
# (scripts/dock bash -c 'source scripts/phase4-lib.sh; ...'). Needs FIRE_TV_IP and ROON_TEST_ZONE (config/local.env).
: "${FIRE_TV_IP:?}"; : "${ROON_TEST_ZONE:?}"
DEV="$FIRE_TV_IP:5555"; PKG=io.github.knoellp.roomscreen
CLI=roon-cli/build/install/roon-cli/bin/roon-cli
OUT=build/device-artifacts; mkdir -p "$OUT"
adb connect "$DEV" >/dev/null 2>&1
sh() { adb -s "$DEV" shell "$@"; }
applog() { adb -s "$DEV" logcat -d -s Roomscreen:V; }
bcast() { sh am broadcast -p $PKG -a "io.github.knoellp.roomscreen.$1" "${@:2}" >/dev/null; }
state() { bcast DUMP_STATE; sleep 1; applog | grep "state {" | tail -1; }
roon() { "$CLI" "$@" 2>&1 | grep -E "control|NOT ALLOWED|REFUSED|ERROR" || true; }
# Transport commands only on the test zone, and only if the app shows exactly that zone.
need_zone() { state | grep -q "\"zone\":\"$ROON_TEST_ZONE\"" || { echo "ABORT: selected zone is not $ROON_TEST_ZONE"; return 1; }; }
shot() { adb -s "$DEV" exec-out screencap -p > "$OUT/$1.png"; echo "$OUT/$1.png"; }
# Core address from the app's own state (never hardcoded).
core_host() { state | grep -o '"endpoint":"[^"]*"' | head -1 | cut -d'"' -f4 | cut -d: -f1; }
ms_now() { date +%s%3N; }
