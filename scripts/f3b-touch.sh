#!/usr/bin/env bash
# Touch flows on the device (docs/PLAN-F3b.md section 3.1). Inside the toolchain container:
#   scripts/dock bash scripts/f3b-touch.sh
# Needs the debug build installed with the release key, roon-cli installed, zone Büro (ROON_TEST_ZONE) selected, German UI.
# Every flow starts from a defined state (prep: Büro paused, LRCLIB on, background cover, screen always on, lyrics off, key sounds off).
# The effect of each tap is read from DUMP_STATE afterwards. Prints "RESULT <name> PASS|FAIL"; Büro is paused at the end.
source "$(dirname "$0")/phase4-lib.sh"
FAILED=0
field() { state | grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
rawfield() { state | grep -o "\"$1\":[^,}]*" | head -1 | cut -d: -f2; }
check() { if [ "$2" = "$3" ]; then echo "RESULT $1 PASS ($3)"; else echo "RESULT $1 FAIL (expected $2, got $3)"; FAILED=1; fi; }
differs() { if [ "$2" != "$3" ] && [ -n "$3" ]; then echo "RESULT $1 PASS ($2 -> $3)"; else echo "RESULT $1 FAIL (unchanged or empty: '$2' -> '$3')"; FAILED=1; fi; }
keysounds() { state | grep -o 'keySounds=[a-z]*' | head -1 | cut -d= -f2; }

run() { # $1 name, $2 flow file; copies the p10-* screenshots, reports the Maestro result
  local name=$1 f=$2
  local out; out=$(maestro --device "$DEV" test -e ROON_TEST_ZONE="$ROON_TEST_ZONE" "$f" 2>&1); local rc=$?
  find /home/dev/.maestro/tests -name 'p10-*.png' -exec cp {} "$OUT/" \; 2>/dev/null
  if [ $rc -eq 0 ]; then echo "RESULT $name maestro PASS"; else echo "RESULT $name maestro FAIL"; FAILED=1; echo "$out" | grep -E "FAILED|Assertion|Error|Unable" | head -5 | sed 's/^/    /'; fi
}
park() { # Büro must be paused; a stopped zone is started and paused again
  local st; st=$(state | grep -o '"state":"[a-z]*"' | head -1 | cut -d'"' -f4)
  case "$st" in
    playing) roon pause --zone "$ROON_TEST_ZONE"; sleep 3 ;;
    stopped) roon play --zone "$ROON_TEST_ZONE"; sleep 6; roon pause --zone "$ROON_TEST_ZONE"; sleep 3 ;;
  esac
}
prep() {
  need_zone || exit 1; park
  bcast SET_LRCLIB --es value on; bcast SET_BACKGROUND --es value cover; bcast SET_SCREEN_ON --es value always
  bcast SET_LYRICS --es value off; bcast SET_KEY_SOUNDS --es value off; sleep 2
}

sh am start -n $PKG/.MainActivity >/dev/null; sleep 8
bcast SELECT_ZONE --es name "$ROON_TEST_ZONE"; sleep 2
need_zone || exit 1
shot p10-00-start

echo "== 1. touch-bar: tap shows the bar, play/pause, next, play/pause"
prep; T0=$(field title)
run touch-bar maestro/touch-bar.yaml
check "touch-bar play by tap: zone playing" playing "$(field state)"
# While playing, Maestro is too slow for the 5 s bar (see the flow header); the next and pause taps are injected directly.
# Bar buttons on the 1920x1080 stick: y=96, play/pause x=1512, next x=1648 (from the touch log of the Maestro tap by text).
sh input tap 960 540; sh input tap 1648 96; sleep 4
differs "touch-bar next by tap changed the title" "$T0" "$(field title)"
sh input tap 960 540; sh input tap 1512 96; sleep 3
check "touch-bar pause by tap: zone paused" paused "$(field state)"
applog | grep -E "control send=(playpause|next)" | tail -3 | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /'


echo "== 2. touch-zones: Zonen -> row -> Now Playing; Zonen -> Schließen -> Now Playing"
prep
run touch-zones maestro/touch-zones.yaml
check "touch-zones screen" NowPlaying "$(field screen)"
check "touch-zones zone" "$ROON_TEST_ZONE" "$(field zone)"

echo "== 3. touch-settings: tap a row twice (on, off), scroll, Schließen"
prep
run touch-settings maestro/touch-settings.yaml
check "touch-settings row tapped: keySounds" true "$(keysounds)"
run touch-settings-2 maestro/touch-settings-2.yaml
check "touch-settings row tapped again: keySounds" false "$(keysounds)"
check "touch-settings Schließen: screen" NowPlaying "$(field screen)"

echo "== 4. touch-lyrics: Liedtexte button on, then off"
prep
if ! state | grep -q 'lrclib=true'; then echo "RESULT touch-lyrics SKIP (LRCLIB is off, the bar has no Liedtexte button)"; else
  run touch-lyrics-on maestro/touch-lyrics.yaml
  check "touch-lyrics on" true "$(rawfield lyrics_on)"
  run touch-lyrics-off maestro/touch-lyrics.yaml
  check "touch-lyrics off" false "$(rawfield lyrics_on)"
fi

echo "== 5. touch-mixed: tap, keys, tap, keys"
prep
run touch-mixed maestro/touch-mixed.yaml
check "touch-mixed screen" NowPlaying "$(field screen)"
check "touch-mixed zone" "$ROON_TEST_ZONE" "$(field zone)"
echo "touch-mixed toggled key sounds with Center: now $(keysounds) (restored below)"
bcast SET_KEY_SOUNDS --es value off; sleep 1

echo "== 6. touch-manual: the Connecting screen with an unused address, tap through the manual entry"
CORE=$(core_host); [ -n "$CORE" ] || { echo "ABORT: no Core address in the app state"; exit 1; }
echo "Core host (from DUMP_STATE): $CORE"
bcast SET_HOST --es host 192.0.2.1 --ei port 9330; sleep 6
state | grep -o '"screen":"[A-Za-z]*","connection":"[^"]*"'
run touch-manual maestro/touch-manual.yaml
check "touch-manual back on the Connecting screen" Connecting "$(field screen)"
# Recovery as in f3-manual-entry.sh: point the app at the real Core again; the saved token and zone are kept.
echo "== recovery: SET_HOST $CORE"
bcast SET_HOST --es host "$CORE" --ei port 9330; sleep 12
state | grep -o '"screen":"[A-Za-z]*","connection":"[^"]*","zones":[0-9]*,"endpoint":"[^"]*"'
# Then forget the manual override (CLEAR_ENDPOINT) and restart so discovery finds the Core again (f3-manual-entry.sh step 3).
bcast CLEAR_ENDPOINT; sleep 2
sh am force-stop $PKG; sleep 2
sh am start -n $PKG/.MainActivity >/dev/null; sleep 25
need_zone && echo "RESULT touch-manual recovery PASS (zone $ROON_TEST_ZONE)" || { echo "RESULT touch-manual recovery FAIL"; FAILED=1; }

echo "== end: Büro paused, settings as before"
prep; shot p10-99-end
echo "touch flows: $([ $FAILED -eq 0 ] && echo ALL PASS || echo SOME FAILED)"
exit $FAILED
