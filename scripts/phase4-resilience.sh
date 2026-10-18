#!/usr/bin/env bash
# Phase 4 resilience scenarios on the Fire TV (inside the toolchain container:
#   scripts/dock bash scripts/phase4-resilience.sh restart-playing|restart-paused|wifi|dead-host)
# Zone Büro only. The Core address is read from the app's DUMP_STATE, never hardcoded.
#  restart-*   POST /1/restartsoftware on the ROCK web UI (software restart only; getstate before/after) while Büro plays or is paused.
#  wifi        the stick switches its own Wi-Fi off for 20 s and on again (run on the device so it recovers by itself).
#  dead-host   SET_HOST to an unused address, restart the app, expect discovery to find the Core again.
source "$(dirname "$0")/phase4-lib.sh"
SCEN=${1:?scenario}
PATTERN='conn state|conn=|conn watchdog|endpoint=|discovery|registry=register_sent|zones_msg subscribed|zone resolved|zone count|lifecycle'
need_zone || exit 1
HOST=$(core_host); [ -n "$HOST" ] || { echo "no core address in app state"; exit 1; }
getstate() { curl -s -m 5 -X POST "http://$HOST/1/getstate" -d "" | python3 -c 'import sys,json; d=json.load(sys.stdin); s=d["data"]["state"]; print("getstate status=%s roon_running=%s roon_version=%s uptime=%s" % (d["status"], s.get("roon_running"), s.get("roon_version"), s.get("uptime")))' 2>&1 || echo "getstate: no answer"; }
# Wait until the app shows Now Playing on the test zone again; prints seconds since $1 (epoch ms).
wait_recovered() {
  local t0=$1 limit=${2:-180}
  while :; do
    local now; now=$(ms_now)
    local st; st=$(state)
    if echo "$st" | grep -q "\"screen\":\"NowPlaying\",\"connection\":\"Connected\"" && echo "$st" | grep -q "\"zone\":\"$ROON_TEST_ZONE\""; then
      echo "recovered: app shows Now Playing for $ROON_TEST_ZONE, $(( ($(ms_now) - t0) / 1000 )) s after the trigger"; return 0
    fi
    [ $(( (now - t0) / 1000 )) -gt "$limit" ] && { echo "NOT recovered after ${limit} s"; return 1; }
    sleep 2
  done
}
show_log() { echo "--- app log (filtered)"; applog | grep -E "$PATTERN" | sed 's/^[0-9-]* //;s/ [0-9]* [0-9]* I Roomscreen:/ /'; echo "---"; }

case "$SCEN" in
  restart-playing|restart-paused)
    if [ "$SCEN" = restart-playing ]; then roon play --zone "$ROON_TEST_ZONE"; else roon pause --zone "$ROON_TEST_ZONE"; fi
    sleep 6; echo "before: $(getstate)"; echo "app: $(state | grep -o '"state":"[a-z]*","title":"[^"]*"')"
    adb -s $DEV logcat -c
    T0=$(ms_now); echo "POST /1/restartsoftware: $(curl -s -m 10 -X POST "http://$HOST/1/restartsoftware" -d "")"
    sleep 3; wait_recovered $T0 240; RC=$?
    echo "after: $(getstate)"; sleep 4; show_log; need_zone && shot "p4-1${SCEN#restart-}-restart-$SCEN" ; echo "app: $(state | grep -o '"state":"[a-z]*","title":"[^"]*"')"
    roon pause --zone "$ROON_TEST_ZONE"; exit $RC ;;
  wifi)
    roon play --zone "$ROON_TEST_ZONE"; sleep 6; echo "app: $(state | grep -o '"state":"[a-z]*","title":"[^"]*"')"
    adb -s $DEV logcat -c
    # The log is also written on the device itself, so nothing depends on the adb link during the cut.
    sh "rm -f /sdcard/phase4-wifi.log; nohup logcat -v threadtime -s Roomscreen:V -f /sdcard/phase4-wifi.log >/dev/null 2>&1 &"
    sleep 1
    T0=$(ms_now); echo "wifi off for 20 s (on the device)"
    sh "nohup sh -c 'svc wifi disable; sleep 20; svc wifi enable' >/dev/null 2>&1 &"
    sleep 40; adb disconnect "$DEV" >/dev/null 2>&1; adb connect "$DEV" | tail -1
    wait_recovered $T0 240; RC=$?
    sleep 3; adb -s $DEV pull /sdcard/phase4-wifi.log $OUT/phase4-wifi.log >/dev/null
    echo "--- device-side log (filtered)"; grep -E "$PATTERN|websocket|reconnect" $OUT/phase4-wifi.log | grep -v "zone count" | sed 's/^[0-9-]* //;s/ [0-9]* *[0-9]* I Roomscreen:/ /'; echo "---"
    need_zone && shot p4-12-after-wifi; echo "app: $(state | grep -o '"state":"[a-z]*","title":"[^"]*"')"
    roon pause --zone "$ROON_TEST_ZONE"; exit $RC ;;
  dead-host)
    # 192.0.2.0/24 is reserved for documentation (TEST-NET-1): nothing answers there.
    adb -s $DEV logcat -c
    T0=$(ms_now); bcast SET_HOST --es host 192.0.2.1 --ei port 9330
    sleep 25; echo "while the manual address is dead: $(state | grep -o '"screen":"[A-Za-z]*","connection":"[^"]*"')"; shot p4-13-dead-host
    wait_recovered $T0 240; RC=$?
    show_log; need_zone && shot p4-14-after-rediscover; echo "saved endpoint now: $(state | grep -o '"endpoint":"[^"]*"')"
    exit $RC ;;
  *) echo "unknown scenario"; exit 2 ;;
esac
