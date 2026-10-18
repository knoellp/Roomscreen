#!/usr/bin/env bash
# Jank of the Now Playing screen over 60 s with and without the lyrics view (inside the toolchain container:
# scripts/dock bash scripts/phase4-jank.sh [rounds]). Same track, no track change in the window. Büro only; ends paused.
source "$(dirname "$0")/phase4-lib.sh"
ROUNDS=${1:-2}; SHARP=${2:-sharp}   # sharp|soft background sharpness
jank() { sh dumpsys gfxinfo $PKG | grep -E "Total frames rendered|Janky frames|90th|99th" | tr -s ' ' | tr '\n' '|'; echo; }
bcast SET_SHARPNESS --es value $SHARP; sleep 2
need_zone || exit 1
roon play --zone "$ROON_TEST_ZONE"; sleep 6
# a track with synced lyrics
for t in 1 2 3 4 5 6; do bcast SET_LYRICS --es value on; sleep 6; state | grep -q '"lyrics":"synced' && break; need_zone || exit 1; sh input keyevent KEYCODE_MEDIA_NEXT; sleep 6; done
state | grep -o '"title":"[^"]*"\|"lyrics":"[^"]*"'
for r in $(seq 1 $ROUNDS); do
  for mode in off on; do
    bcast SET_LYRICS --es value $mode; sleep 8
    adb -s $DEV logcat -c; sh dumpsys gfxinfo $PKG reset >/dev/null; sleep 60
    echo "round $r $SHARP lyrics=$mode: $(jank) track_lines=$(applog | grep -c 'track zone="Büro"') $(state | grep -o '"lyrics":"[^"]*"')"
  done
done
roon pause --zone "$ROON_TEST_ZONE"; sleep 2
