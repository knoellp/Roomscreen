#!/usr/bin/env bash
# Phase 2 performance run on the Fire TV (inside the toolchain container: scripts/dock bash scripts/phase2-perf.sh).
# Zone Büro only. 10 warm-up track changes, then 1) 60 s of playback: gfxinfo jank, 2) meminfo before/after 20 track changes via the media key.
set -uo pipefail
: "${FIRE_TV_IP:?}"; : "${ROON_TEST_ZONE:?}"
DEV="$FIRE_TV_IP:5555"; PKG=io.github.knoellp.roomscreen
CLI=roon-cli/build/install/roon-cli/bin/roon-cli
adb connect "$DEV" >/dev/null
sh() { adb -s "$DEV" shell "$@"; }
roon() { "$CLI" "$@" 2>&1 | grep -E "control|NOT ALLOWED|REFUSED|ERROR" || true; }
mem() { sh dumpsys meminfo $PKG | grep -E "Java Heap:|Native Heap:|Graphics:|TOTAL " | head -4 | tr -s ' ' | sed 's/^/  /'; }

sh am start -n $PKG/.MainActivity >/dev/null; sleep 6
sh am broadcast -p $PKG -a io.github.knoellp.roomscreen.DUMP_STATE >/dev/null; sleep 1
adb -s "$DEV" logcat -d -s Roomscreen:V | grep "state {" | tail -1 | grep -q '"zone":"Büro"' || { echo "ABORT: selected zone is not Büro"; exit 1; }

echo "== start playback on Büro"
roon pause --zone "$ROON_TEST_ZONE"; sleep 2; roon play --zone "$ROON_TEST_ZONE"; sleep 8
echo "== warm-up: 10 track changes so the image caches are filled"
for i in $(seq 1 10); do sh input keyevent KEYCODE_MEDIA_NEXT; sleep 4; done
sleep 5
echo "== gfxinfo, 60 s of playback (after warm-up, so JIT compilation is not measured)"
sh dumpsys gfxinfo $PKG reset >/dev/null
sleep 60
sh dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th"

echo "== meminfo baseline (after warm-up)"; mem
adb -s "$DEV" logcat -c
echo "== 20 track changes (KEYCODE_MEDIA_NEXT, 4 s apart)"
for i in $(seq 1 20); do sh input keyevent KEYCODE_MEDIA_NEXT; sleep 4; done
adb -s "$DEV" logcat -d -s Roomscreen:V | grep -E 'control send=next|track zone="Büro" state=playing' | sed 's/^/  /' | tail -45
echo "distinct titles: $(adb -s "$DEV" logcat -d -s Roomscreen:V | grep 'track zone="Büro" state=playing' | sed 's/.*title=//' | sort -u | wc -l)"
echo "== meminfo after 20 changes"; mem
sleep 30
echo "== meminfo 30 s later"; mem

echo "== end: Büro paused"
roon pause --zone "$ROON_TEST_ZONE"; sleep 2
"$CLI" now-playing --zone "$ROON_TEST_ZONE" 2>&1 | grep "^zone "
