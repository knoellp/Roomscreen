#!/usr/bin/env bash
# Lifecycle with a system overlay (docs/PLAN-F3b.md section 2.3): the voice assistant overlay of Fire OS pauses the activity,
# a track change happens on the test zone meanwhile, the app returns and shows the new state with a single connection.
# Run inside the toolchain container: scripts/dock bash scripts/f3b-lifecycle.sh
# Needs the debug build, zone Büro selected, roon-cli installed. Büro is played (next track) and paused again at the end.
source "$(dirname "$0")/phase4-lib.sh"
FAILED=0
check() { if [ "$2" = "$3" ]; then echo "RESULT $1 PASS ($3)"; else echo "RESULT $1 FAIL (expected $2, got $3)"; FAILED=1; fi; }
field() { state | grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
focus() { sh dumpsys window 2>/dev/null | grep -o "mCurrentFocus=.*" | head -1 | tr -d '\r'; }

echo "== 0. preflight"
need_zone || exit 1
check "connected" Connected "$(field connection)"
TITLE0=$(field title); echo "title before: $TITLE0"
adb -s "$DEV" logcat -c

echo "== 1. voice assistant overlay (KEYCODE_VOICE_ASSIST 231), fallback Home"
USED=""
for code in 231 84; do   # KEYCODE_VOICE_ASSIST, KEYCODE_SEARCH
  sh input keyevent $code; sleep 3
  F=$(focus); echo "focus after keyevent $code: $F"
  echo "$F" | grep -q "$PKG" || { USED="key$code"; break; }
done
if [ -z "$USED" ]; then
  echo "no system overlay took the focus; using Home instead"; sh input keyevent 3; sleep 3; echo "focus after Home: $(focus)"; USED=home
fi
echo "== 2. track change on $ROON_TEST_ZONE while the app is in the background"
roon next --zone "$ROON_TEST_ZONE"; sleep 5
sh dumpsys activity activities | grep -E "mResumedActivity" | head -1

echo "== 3. return to the app"
case "$USED" in key*) sh input keyevent 4; sleep 2 ;; esac
sh am start -n $PKG/.MainActivity >/dev/null; sleep 6
check "app resumed on Now Playing" NowPlaying "$(field screen)"
check "connected after return" Connected "$(field connection)"
check "zone still $ROON_TEST_ZONE" "$ROON_TEST_ZONE" "$(field zone)"
TITLE1=$(field title); echo "title after: $TITLE1"
[ "$TITLE1" != "$TITLE0" ] && echo "RESULT new track shown PASS" || { echo "RESULT new track shown FAIL (title unchanged)"; FAILED=1; }
roon pause --zone "$ROON_TEST_ZONE"; sleep 2

echo "== 4. single connection: at most one open socket at any time (log order)"
applog > "$OUT/f3b-lifecycle-log.txt"
OPENS=$(grep -c "conn=websocket_open" "$OUT/f3b-lifecycle-log.txt" || true)
CANCEL=$(grep -c "lifecycle=disconnect_cancelled" "$OUT/f3b-lifecycle-log.txt" || true)
DELAYED=$(grep -c "lifecycle=disconnect_after_delay" "$OUT/f3b-lifecycle-log.txt" || true)
echo "connects=$OPENS disconnect_after_delay=$DELAYED disconnect_cancelled=$CANCEL"
# Either the pause was short (connection kept, no reconnect) or it was long (one disconnect, one reconnect). Never two sockets.
if [ "$DELAYED" -ge 1 ] && [ "$OPENS" -le "$DELAYED" ]; then echo "RESULT one connection per return PASS"
elif [ "$DELAYED" = 0 ] && [ "$OPENS" = 0 ]; then echo "RESULT connection kept, no reconnect PASS"
else echo "RESULT connection count FAIL (connects=$OPENS delayed=$DELAYED)"; FAILED=1; fi
grep -E "lifecycle=|conn state=|conn=connected|websocket|track zone=\"$ROON_TEST_ZONE\"" "$OUT/f3b-lifecycle-log.txt" | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /' | head -20
echo "overlay used: $USED"
[ "$FAILED" = 0 ] && echo "RESULT all PASS" || echo "RESULT some FAIL"
exit $FAILED
