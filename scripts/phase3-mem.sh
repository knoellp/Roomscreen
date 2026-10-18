#!/usr/bin/env bash
# Phase 3 memory check (inside the toolchain container: scripts/dock bash scripts/phase3-mem.sh [on|off] [changes]).
# Optional third argument: number of warm-up track changes before the baseline (Phase 2 used 10). Zone Büro only. Fresh app process, lyrics on or off, N track changes via the app's media key, meminfo after each.
set -uo pipefail
: "${FIRE_TV_IP:?}"; : "${ROON_TEST_ZONE:?}"
MODE=${1:-on}; N=${2:-10}; WARM=${3:-0}
DEV="$FIRE_TV_IP:5555"; PKG=io.github.knoellp.roomscreen
CLI=roon-cli/build/install/roon-cli/bin/roon-cli
adb connect "$DEV" >/dev/null
sh() { adb -s "$DEV" shell "$@"; }
roon() { "$CLI" "$@" 2>&1 | grep -E "control|NOT ALLOWED|REFUSED|ERROR" || true; }
mem() { sh dumpsys meminfo $PKG | grep -E "Java Heap:|Native Heap:|Graphics:|Code:|TOTAL PSS:|TOTAL:" | tr -s ' ' | tr '\n' '|'; echo; }
state() { sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.DUMP_STATE >/dev/null; sleep 1; adb -s "$DEV" logcat -d -s Roomscreen:V | grep "state {" | tail -1; }
sh am force-stop $PKG; sleep 2
sh am start -n $PKG/.MainActivity >/dev/null; sleep 12
# The debug hook needs the running view model, so switch lyrics only after the start.
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SET_LYRICS --es value $MODE >/dev/null; sleep 3
state | grep -q '"zone":"Büro"' || { echo "ABORT: selected zone is not Büro"; exit 1; }
state | grep -o '"lyrics_on":[a-z]*'
roon play --zone "$ROON_TEST_ZONE"; sleep 6
echo "== lyrics $MODE, $WARM warm-up track changes, then $N measured track changes"
for i in $(seq 1 $WARM); do sh input keyevent KEYCODE_MEDIA_NEXT; sleep 4; done
echo "start: $(mem) pid=$(sh pidof $PKG)"
for i in $(seq 1 $N); do
  sh input keyevent KEYCODE_MEDIA_NEXT; sleep 4
  echo "after $i: $(mem)"
done
sleep 30; echo "30 s later: $(mem) pid=$(sh pidof $PKG) focus=$(sh dumpsys window | grep mCurrentFocus | tr -s " ")"
roon pause --zone "$ROON_TEST_ZONE"; sleep 2
