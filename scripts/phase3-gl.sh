#!/usr/bin/env bash
# GL memory probe on one track (inside the toolchain container: scripts/dock bash scripts/phase3-gl.sh on|off [seconds]).
# Büro only: starts playback with the CLI, samples "GL mtrack" every 10 s without any track change, pauses Büro at the end.
set -uo pipefail
: "${FIRE_TV_IP:?}"; : "${ROON_TEST_ZONE:?}"
MODE=${1:-on}; SECS=${2:-90}
DEV="$FIRE_TV_IP:5555"; PKG=io.github.knoellp.roomscreen
CLI=roon-cli/build/install/roon-cli/bin/roon-cli
adb connect "$DEV" >/dev/null
sh() { adb -s "$DEV" shell "$@"; }
gl() { sh dumpsys meminfo $PKG | grep -E "GL mtrack|TOTAL:" | tr -s ' ' | tr '\n' '|'; echo; }
state() { sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.DUMP_STATE >/dev/null; sleep 1; adb -s "$DEV" logcat -d -s Roomscreen:V | grep "state {" | tail -1; }
sh am force-stop $PKG; sleep 2
sh am start -n $PKG/.MainActivity >/dev/null; sleep 12
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.SET_LYRICS --es value $MODE >/dev/null; sleep 3
state | grep -q '"zone":"Büro"' || { echo "ABORT: selected zone is not Büro"; exit 1; }
"$CLI" play --zone "$ROON_TEST_ZONE" 2>&1 | grep -E "control|NOT ALLOWED|ERROR"; sleep 4
echo "lyrics $MODE, one track, $SECS s"; echo "t=0: $(gl)"
for t in $(seq 10 10 $SECS); do sleep 10; echo "t=$t: $(gl)"; done
"$CLI" pause --zone "$ROON_TEST_ZONE" 2>&1 | grep -E "control|NOT ALLOWED|ERROR"
