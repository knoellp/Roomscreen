#!/usr/bin/env bash
# Phase 4 soak test: 2 hours of playback on the test zone with lyrics on, background artist image, sharp.
#   nohup scripts/phase4-soak.sh > build/device-artifacts/soak.log 2>&1 &
# Prepare first: Büro selected in the app and playing, lyrics button on, background = artist image, sharpness = sharp.
# Runs in the toolchain container (re-executes itself through scripts/dock). Arguments: [duration_min=120] [interval_min=10] [next_every_min=20] [warmup_changes=10]
#  * after the warm-up track changes (so the cold-start fill-up of the image caches is not counted), every interval:
#    meminfo (TOTAL PSS, GL mtrack, Java heap), DUMP_STATE summary, screenshot build/device-artifacts/soak-NN.png
#  * every next_every_min one KEYCODE_MEDIA_NEXT, only on the test zone (checked through DUMP_STATE first)
#  * the app log is harvested every interval into build/device-artifacts/soak-logcat.txt (the device ring buffer is small)
#  * at the end: scan for Reconnecting / watchdog / failed / Exception / unavailable, crash buffer, ANR, then pause the test zone.
if [ ! -f /.dockerenv ]; then cd "$(dirname "$0")/.." && exec scripts/dock bash scripts/phase4-soak.sh "$@"; fi
source "$(dirname "$0")/phase4-lib.sh"
DUR=${1:-120}; STEP=${2:-10}; NEXT_EVERY=${3:-20}; WARM=${4:-10}
LOG=$OUT/soak-logcat.txt; : > "$LOG"
harvest() {
  adb connect "$DEV" >/dev/null 2>&1
  { adb -s "$DEV" logcat -b main,system,crash -d -v threadtime -s Roomscreen:V AndroidRuntime:E ActivityManager:W '*:E'; } >> "$LOG" 2>&1
  adb -s "$DEV" logcat -c
}
mem() { sh dumpsys meminfo $PKG | tr -s ' ' | awk '/TOTAL PSS:/{pss=$3} /^ *TOTAL:/{if(!pss)pss=$2} /GL mtrack/{gl=$3} /Java Heap:/{jh=$3} END{printf "pss_kb=%s gl_mtrack_kb=%s java_heap_kb=%s", pss, gl, jh}'; }
summary() { state | grep -o '"connection":"[^"]*"\|"zones":[0-9]*\|"zone":"[^"]*"\|"state":"[a-z]*"\|"title":"[^"]*"\|"lyrics":"[^"]*"' | tr '\n' ' '; }
echo "soak start $(date '+%F %T'): duration=${DUR} min, sample every ${STEP} min, next every ${NEXT_EVERY} min, warm-up ${WARM} changes, pid=$(sh pidof $PKG)"
need_zone || exit 1
echo "settings: $(state | grep -o '"settings":"[^"]*"')"
st=$(state)
echo "$st" | grep -q '"state":"playing"' || { roon play --zone "$ROON_TEST_ZONE"; sleep 6; }
adb -s "$DEV" logcat -c
for i in $(seq 1 $WARM); do need_zone || exit 1; sh input keyevent KEYCODE_MEDIA_NEXT; sleep 6; done
echo "warm-up done: $(mem)"
T_START=$(date +%s); N=0; LAST_NEXT=$T_START
while :; do
  NOW=$(date +%s); EL=$(( (NOW - T_START) / 60 ))
  printf 'sample %02d t=%3d min %s | %s\n' "$N" "$EL" "$(mem)" "$(summary)"
  adb -s "$DEV" exec-out screencap -p > "$OUT/soak-$(printf %02d $N).png"
  harvest
  [ "$EL" -ge "$DUR" ] && break
  # keep the zone playing (Roon's queue may end): log it, then press play on the test zone only
  if ! state | grep -q '"state":"playing"'; then echo "  WARNING: zone not playing at sample $N -> play"; need_zone && roon play --zone "$ROON_TEST_ZONE"; fi
  # sleep until the next sample, with the NEXT key in between
  for tick in $(seq 1 $((STEP * 6))); do
    sleep 10
    NOW=$(date +%s)
    if [ $(( (NOW - LAST_NEXT) / 60 )) -ge "$NEXT_EVERY" ]; then
      if need_zone >/dev/null 2>&1; then sh input keyevent KEYCODE_MEDIA_NEXT; echo "  next sent at t=$(( (NOW - T_START) / 60 )) min"; fi
      LAST_NEXT=$NOW
    fi
  done
  N=$((N + 1))
done
echo "== end of run $(date '+%F %T'); scanning the harvested log ($(wc -l < "$LOG") lines)"
for pat in 'Reconnecting' 'watchdog' 'failed' 'Exception' 'unavailable' 'conn=websocket_failure' 'session_failed' 'FATAL' 'ANR'; do
  printf '  %-24s %s\n' "$pat" "$(grep -c -- "$pat" "$LOG")"
done
echo "  lines matching (first 20):"; grep -E 'Reconnecting|watchdog|failed|Exception|FATAL|ANR|websocket_failure' "$LOG" | head -20 | sed 's/^/    /'
echo "  dumpsys activity (crash/ANR):"; sh dumpsys activity processes | grep -iE "crash|anr|notResponding" | head -5 | sed 's/^/    /'
echo "  process: pid=$(sh pidof $PKG) (start pid was in the first line)"
need_zone && roon pause --zone "$ROON_TEST_ZONE"
sleep 3; echo "final: $(summary)"
echo "soak finished $(date '+%F %T')"
