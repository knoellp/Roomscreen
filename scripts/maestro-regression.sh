#!/usr/bin/env bash
# Regression run of all eleven Maestro flows with a defined start state (inside the toolchain container:
#   scripts/dock bash scripts/maestro-regression.sh).
# The flows count D-pad presses, so the settings that add or remove rows and bar buttons are set first:
#   SET_LRCLIB on (Lyrics bar button and "Textversatz" row exist), SET_BACKGROUND cover (the sharpness row exists), lyrics button off.
# Zone Büro only; Büro is paused for the navigation flows and ends paused. Needs a debug build installed with the release key.
source "$(dirname "$0")/phase4-lib.sh"
RES=()
run() { # $1 flow file; extra maestro args after
  local f=$1; shift
  local out; out=$(maestro --device "$DEV" test "$@" "$f" 2>&1); local rc=$?
  find /home/dev/.maestro/tests -name 'p[0-9]*.png' -exec cp {} "$OUT/" \; 2>/dev/null
  if [ $rc -eq 0 ]; then RES+=("PASS $f"); else RES+=("FAIL $f"); echo "$out" | grep -E "FAILED|Assertion|Error" | head -5 | sed 's/^/    /'; fi
}
park() { # Büro must be paused (flows read "Büro · Pausiert"); a stopped zone is started and paused again
  local st; st=$(state | grep -o '"state":"[a-z]*"' | head -1 | cut -d'"' -f4)
  case "$st" in
    playing) roon pause --zone "$ROON_TEST_ZONE"; sleep 3 ;;
    stopped) roon play --zone "$ROON_TEST_ZONE"; sleep 6; roon pause --zone "$ROON_TEST_ZONE"; sleep 3 ;;
  esac
}
prep() { # defined start state
  need_zone || exit 1; park
  bcast SET_LRCLIB --es value on; bcast SET_BACKGROUND --es value cover; bcast SET_SCREEN_ON --es value always
  bcast SET_LYRICS --es value off; sleep 2
}

sh am start -n $PKG/.MainActivity >/dev/null; sleep 8
bcast SELECT_ZONE --es name "$ROON_TEST_ZONE"; sleep 2
need_zone || exit 1; park
prep
roon play --zone "$ROON_TEST_ZONE"; sleep 4; roon pause --zone "$ROON_TEST_ZONE"; sleep 3

run maestro/phase1.yaml
prep
run maestro/phase1-zones.yaml -e ROON_TEST_ZONE="$ROON_TEST_ZONE"
prep
run maestro/phase2.yaml
prep
run maestro/phase2-settings.yaml
sh am force-stop $PKG; sleep 2; sh am start -n $PKG/.MainActivity >/dev/null; sleep 12
run maestro/phase2-settings-restore.yaml
prep
run maestro/phase2-artist.yaml
prep
run maestro/phase2-connection.yaml
prep
run maestro/phase3-lyrics-on.yaml
bcast SET_LYRICS --es value off; sleep 2
need_zone || exit 1; park
run maestro/phase3-lrclib-off.yaml
park
run maestro/phase3-lrclib-on.yaml
# f3-dpad starts with Büro playing (the ring keys act on the playing zone directly) and is parked paused afterwards
prep
roon play --zone "$ROON_TEST_ZONE"; sleep 8
run maestro/f3-dpad.yaml
bcast SET_LRCLIB --es value on; bcast SET_LYRICS --es value off; sleep 2
need_zone || exit 1; roon pause --zone "$ROON_TEST_ZONE"; sleep 3
echo "== results"; printf '%s\n' "${RES[@]}"; state
