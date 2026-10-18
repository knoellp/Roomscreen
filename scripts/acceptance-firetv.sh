#!/usr/bin/env bash
# Acceptance run for Fire TV version 1 (docs/PLAN.md section 13), one result line and one screenshot per criterion.
# Run inside the toolchain container:  scripts/dock bash scripts/acceptance-firetv.sh
# Needs config/local.env (FIRE_TV_IP, ROON_TEST_ZONE) and config/signing.env (release key).
# Zone Büro ($ROON_TEST_ZONE) only; every transport command is preceded by a check that the app shows exactly that zone.
# Ends with Büro paused. Screenshots: build/device-artifacts/p5-acc-NN.png. Results: build/device-artifacts/p5-acceptance.txt
#
# Written but NOT run yet (device run pending).
#
# Criterion 1 installs the RELEASE APK (signature checked against config/release-fingerprint.txt, `install -r`, never an uninstall)
# and starts it. The release build has no debug hooks (DUMP_STATE), so the state checks of criteria 2 to 11 run on the DEBUG build,
# which carries the same key and replaces the release APK with `install -r`; the preferences (token, zone, settings) are kept.
# Criterion 9 here uses injected media key codes (85/87/88); the physical remote is a separate step.
# Criterion 12 (Core restart) is covered by docs/ARCHITECTURE.md section 10.2; this script does not restart the Core.
set -uo pipefail
source "$(dirname "$0")/phase4-lib.sh"
APKSIGNER=/opt/android-sdk/build-tools/36.0.0/apksigner
REL=app/build/outputs/apk/release/app-release.apk
DBG=app/build/outputs/apk/debug/app-debug.apk
RESULTS="$OUT/p5-acceptance.txt"; : > "$RESULTS"
FAILS=0
acc() { # $1 number, $2 PASS|FAIL|REF, $3 text
  local n; n=$(printf %02d "$1")
  [ "$2" = FAIL ] && FAILS=$((FAILS+1))
  shot "p5-acc-$n" >/dev/null
  echo "ACC $n $2 $3" | tee -a "$RESULTS"
}
field() { echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }
num() { echo "$1" | grep -o "\"$2\":-\?[0-9]*" | head -1 | cut -d: -f2; }
wait_state() { # $1 grep pattern in DUMP_STATE, $2 timeout s; prints the last state
  local t=0 st
  while [ $t -lt "$2" ]; do st=$(state); if echo "$st" | grep -q "$1"; then echo "$st"; return 0; fi; sleep 2; t=$((t+2)); done
  echo "$st"; return 1
}
cert_digest() { "$APKSIGNER" verify --print-certs "$1" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1; }
EXPECTED=$(grep -Eio '^[0-9a-f]{64}$' config/release-fingerprint.txt | head -n1)
buero() { need_zone || { echo "ABORT: zone is not $ROON_TEST_ZONE"; roon pause --zone "$ROON_TEST_ZONE"; exit 1; }; }

echo "== 1. release APK: build, signature, install -r, start"
./gradlew --no-daemon :app:assembleRelease :app:assembleDebug :roon-cli:installDist -q || { echo "build failed"; exit 1; }
RD=$(cert_digest "$REL"); DD=$(cert_digest "$DBG")
if [ "$RD" != "$EXPECTED" ] || [ "$DD" != "$EXPECTED" ]; then
  acc 1 FAIL "signature mismatch: release=$RD debug=$DD expected=$EXPECTED (no install done)"; exit 1
fi
adb -s "$DEV" install -r "$REL" || { acc 1 FAIL "install -r of the release APK failed (a different installed key needs the one-time key change, ask first)"; exit 1; }
sh am force-stop $PKG; sleep 1; adb -s "$DEV" logcat -c
sh am start -n $PKG/.MainActivity >/dev/null; sleep 10
VN=$(sh dumpsys package $PKG | grep -m1 versionName= | tr -d ' \r')
sh pidof $PKG >/dev/null && RUN=running || RUN="NOT running"
[ "$RUN" = running ] && [ "$VN" = "versionName=1.0.0" ] && acc 1 PASS "release APK $VN installed with certificate $RD, $RUN" || acc 1 FAIL "release APK: $VN, $RUN"

echo "== switching to the debug build (same key) for the state checks"
adb -s "$DEV" install -r "$DBG" >/dev/null || { echo "debug install failed"; exit 1; }
sh am force-stop $PKG; adb -s "$DEV" logcat -c
sh am start -n $PKG/.MainActivity >/dev/null; sleep 5

echo "== 2. connected (Core found or cached endpoint)"
ST=$(wait_state '"connection":"Connected"' 60); EP=$(field "$ST" endpoint)
[ -n "$EP" ] && echo "$ST" | grep -q '"connection":"Connected"' && acc 2 PASS "Connected, endpoint $EP, log: $(applog | grep -m1 -oE 'endpoint=[a-z_]+|discovery result=[a-z]+')" || acc 2 FAIL "not connected: $ST"

echo "== 3. extension enabled once (token reused, no new Enable)"
if echo "$ST" | grep -q '"screen":"Authorization"'; then acc 3 FAIL "app waits for the Enable in Roon"
else acc 3 PASS "no authorization screen; registry log: $(applog | grep -m2 'registry=' | tr '\n' '|')"; fi

echo "== 4. zone Büro selected"
bcast SELECT_ZONE --es name "$ROON_TEST_ZONE"; sleep 3
ST=$(wait_state "\"zone\":\"$ROON_TEST_ZONE\"" 20)
echo "$ST" | grep -q "\"screen\":\"NowPlaying\"" && echo "$ST" | grep -q "\"zone\":\"$ROON_TEST_ZONE\"" && acc 4 PASS "Now Playing for zone $ROON_TEST_ZONE" || acc 4 FAIL "zone not selected: $ST"
buero || exit 1

echo "== 5. start playback on Büro"
roon play --zone "$ROON_TEST_ZONE"
ST=$(wait_state '"state":"playing"' 30)
echo "$ST" | grep -q '"state":"playing"' && acc 5 PASS "state=playing" || acc 5 FAIL "not playing: $ST"

echo "== 6. cover, title, artist"
T=$(field "$ST" title); A=$(field "$ST" artist); B=$(field "$ST" backdrop)
[ -n "$T" ] && [ -n "$A" ] && [ -n "$B" ] && acc 6 PASS "title=\"$T\" artist=\"$A\" cover url=$B" || acc 6 FAIL "title=\"$T\" artist=\"$A\" cover url=\"$B\""

echo "== 7. progress runs"
S1=$(num "$(state)" seek); sleep 5; S2=$(num "$(state)" seek)
[ -n "$S1" ] && [ -n "$S2" ] && [ "$S2" -gt "$S1" ] && acc 7 PASS "position $S1 s -> $S2 s after 5 s" || acc 7 FAIL "position $S1 -> $S2"

echo "== 8. track change updates the display"
buero || exit 1
T0=$T; roon next --zone "$ROON_TEST_ZONE"; sleep 6; ST=$(state); T1=$(field "$ST" title)
[ -n "$T1" ] && [ "$T1" != "$T0" ] && acc 8 PASS "title \"$T0\" -> \"$T1\"" || acc 8 FAIL "title unchanged: \"$T0\" -> \"$T1\""

echo "== 9. media keys (injected key codes 85, 87, 88)"
buero || exit 1
sh input keyevent 85; sleep 4; P1=$(field "$(state)" state)
sh input keyevent 85; sleep 4; P2=$(field "$(state)" state)
sh input keyevent 87; sleep 6; N1=$(field "$(state)" title)
sh input keyevent 88; sleep 6; ST=$(state); N2=$(field "$ST" title); SK=$(num "$ST" seek)
KEYLOG=$(applog | grep -E "key code=(85|87|88)" | sed 's/^[0-9-]* //' | tr '\n' '|')
if [ "$P1" = paused ] && [ "$P2" = playing ] && [ "$N1" != "$T1" ] && { [ "$N2" != "$N1" ] || [ "${SK:-99}" -lt 5 ]; }; then
  acc 9 PASS "85: $P2->$P1->$P2; 87: \"$T1\" -> \"$N1\"; 88: \"$N1\" -> \"$N2\" (seek $SK s). $KEYLOG"
else acc 9 FAIL "85: $P1/$P2; 87: \"$T1\" -> \"$N1\"; 88: \"$N2\" seek=$SK. $KEYLOG"; fi

echo "== 10. synchronized lyrics (LRCLIB), up to 6 tracks until one has a hit"
bcast SET_LRCLIB --es value on; bcast SET_LYRICS --es value on; sleep 3
LY=""
for i in 1 2 3 4 5 6; do
  ST=$(wait_state '"lyrics":"synced:[0-9]*"' 15)
  if echo "$ST" | grep -q '"lyrics":"synced:[0-9]*"'; then LY=$(field "$ST" lyrics); break; fi
  buero || exit 1; roon next --zone "$ROON_TEST_ZONE"; sleep 5
done
if [ -n "$LY" ]; then
  sleep 3; acc 10 PASS "lyrics=$LY for \"$(field "$ST" title)\" (log: $(applog | grep -m1 -oE 'lyrics fetch=[^ ]+ variant=[^ ]+ result=synced lines=[0-9]+'))"
else acc 10 FAIL "no synced lyrics within 6 tracks (state: $(field "$ST" lyrics)); LRCLIB may have no hit or answers 503"; fi

echo "== 11. force-stop and restart: same zone, same settings, no new Enable"
SET_BEFORE=$(echo "$(state)" | grep -o '"settings":"[^"]*"')
sh am force-stop $PKG; sleep 2; adb -s "$DEV" logcat -c
sh am start -n $PKG/.MainActivity >/dev/null
ST=$(wait_state "\"zone\":\"$ROON_TEST_ZONE\"" 60)
SET_AFTER=$(echo "$ST" | grep -o '"settings":"[^"]*"')
if echo "$ST" | grep -q "\"screen\":\"NowPlaying\"" && [ "$SET_BEFORE" = "$SET_AFTER" ] && ! echo "$ST" | grep -q Authorization; then
  acc 11 PASS "same zone $ROON_TEST_ZONE, settings unchanged, connection $(field "$ST" connection), no authorization screen"
else acc 11 FAIL "after restart: $ST; settings before=$SET_BEFORE after=$SET_AFTER"; fi

echo "== 12. reconnect after a Core restart"
acc 12 REF "met on the real Core on 2026-10-18 (docs/ARCHITECTURE.md section 10.2, scripts/phase4-resilience.sh); this script does not restart the Core"

echo "== end: lyrics button off, Büro paused"
bcast SET_LYRICS --es value off; sleep 2
buero || exit 1
roon pause --zone "$ROON_TEST_ZONE"; sleep 4
FIN=$(field "$(state)" state); echo "final state of $ROON_TEST_ZONE: $FIN"
[ "$FIN" = paused ] || { echo "WARNING: Büro is not paused"; FAILS=$((FAILS+1)); }
echo "== summary: $(grep -c ' PASS ' "$RESULTS") pass, $FAILS fail, $(grep -c ' REF ' "$RESULTS") reference"
exit $((FAILS > 0))
