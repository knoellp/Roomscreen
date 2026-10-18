#!/usr/bin/env bash
# Phase 4 memory and jank run (inside the toolchain container: scripts/dock bash scripts/phase4-perf.sh <sw> [changes]).
# Sharp artist background, lyrics on, fresh app process. Zone Büro only (checked before every transport command).
# 1) meminfo (TOTAL PSS, GL mtrack, Java heap) before / after N track changes / 30 s later,
# 2) gfxinfo jank of the lyrics view over 60 s (needs a track with synced lyrics; skips forward to find one).
# The label (sw) only names the output file; the hardware-bitmap variant was measured once and removed. Ends with Büro paused.
source "$(dirname "$0")/phase4-lib.sh"
MODE=${1:-sw}; N=${2:-10}
mem() { sh dumpsys meminfo $PKG | grep -E "Java Heap:|GL mtrack|TOTAL PSS:|TOTAL:" | tr -s ' ' | tr '\n' '|'; echo; }
sh am force-stop $PKG; sleep 2
sh am start -n $PKG/.MainActivity >/dev/null; sleep 12
bcast SET_BACKGROUND --es value artist; bcast SET_SHARPNESS --es value sharp; sleep 2
bcast SET_LYRICS --es value on; sleep 3
need_zone || exit 1
echo "mode=$MODE; $(state | grep -o '"settings":"[^"]*"')"
adb -s $DEV logcat -c
roon play --zone "$ROON_TEST_ZONE"; sleep 8
echo "start: $(mem) pid=$(sh pidof $PKG)"
for i in $(seq 1 $N); do need_zone || exit 1; sh input keyevent KEYCODE_MEDIA_NEXT; sleep 5; done
echo "after $N: $(mem)"
sleep 30; echo "30 s later: $(mem)"
echo "backdrop paths used: $(applog | grep -o 'backdrop sharp path=[a-z]*' | sort | uniq -c | tr '\n' ' ')"
echo "== lyrics view jank, 60 s"
for t in 1 2 3 4 5 6; do state | grep -q '"lyrics":"synced' && break; need_zone || exit 1; sh input keyevent KEYCODE_MEDIA_NEXT; sleep 8; done
state | grep -o '"title":"[^"]*","artist":"[^"]*"\|"lyrics":"[^"]*"'
sh dumpsys gfxinfo $PKG reset >/dev/null; sleep 60
sh dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th"
state | grep -o '"lyrics":"[^"]*"'
shot p4-30-lyrics-sharp-$MODE
roon pause --zone "$ROON_TEST_ZONE"; sleep 2
