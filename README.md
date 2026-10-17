# Roomscreen

*A Now Playing display for every room - for Roon on Fire TV and Android TV.*

A native Android TV app (Kotlin, Compose for TV) that shows what is playing in one Roon zone.
It talks directly to the Roon Core (SOOD discovery and MOO over WebSocket). There is no helper
service, no bridge and no WebView.

> Roomscreen is not affiliated with or endorsed by Roon Labs. Roon is a trademark of Roon Labs LLC.

Full project brief: `docs/PLAN.md`. Protocol findings and decisions: `docs/ARCHITECTURE.md`.

## Status (Phase 1)

| Area | Automated tests | Verified on device (Fire TV Stick HD, Fire OS 7.7.1.7, API 28) | Open |
|---|---|---|---|
| SOOD discovery | codec, responder (JVM) | finds the Core on the Fire TV | multicast lock necessity not isolated |
| Cached endpoint first, discovery as fallback | fake Core | restart connects in ~20 ms with `endpoint=cached` | - |
| Registration, "Enable", token reuse | fake Core (recorded messages) | enabled once, later starts reuse the token | - |
| Zones, track change, pause, seek | recorded fixtures | now playing updates after a track change | - |
| Cover art | - | shown (HTTP image URL, no auth needed) | missing-cover case only in tests |
| Zone list, Zones button, saved zone | screenshots (Roborazzi) | Maestro flows `maestro/phase1*.yaml` | - |
| Reconnect after Core restart | fake Core | not tried on the real Core | needs a Roon Server restart |
| Manual host/port entry | screenshot only | not exercised | open |
| Several devices with the same Roon | - | - | two installs not tested together |

Not implemented yet: the full "now playing" design (large cover, progress, backgrounds), media-key
control and a playback controls UI, synchronized lyrics (LRCLIB), Echo Show 15 (Live App Testing,
touch layout), release signing. The app never sends playback commands in this phase.

## Behaviour

- Start: tries the last known Core address with a quick TCP probe; only if that fails it runs SOOD
  discovery (and holds a multicast lock for that time). A manual address skips discovery.
- First run: the Core asks for authorization. The app shows where to enable it; once enabled the
  token is stored and reused.
- Zone: pick it once; it is remembered. Press any remote key on Now Playing to show the **Zones**
  button, select it to switch zones. Back from the zone list returns to Now Playing unchanged.

## Roon setup

On first connection the app registers as an extension named "Roomscreen (<device name>)". In Roon
open **Settings → Extensions** (verified on Roon for Mac 2.73), find it and press **Enable**.
The app continues by itself.

## Install on a Fire TV (ADB)

1. On the Fire TV: Settings, My Fire TV, Developer options: turn on ADB debugging (and "Apps from unknown sources").
2. Build the debug APK (needs Docker; no local JDK/SDK): `docker build -t roomscreen-android docker/android-build`, then
   `scripts/dock ./gradlew :app:assembleDebug`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.
3. `adb connect <fire-tv-ip>:5555` and confirm the dialog on the TV, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Start it from the apps list, or `adb shell am start -n io.github.knoellp.roomscreen/.MainActivity`.

Always sign with the same key and use `install -r` so updates keep the stored token.
`scripts/deploy-test.sh [--flow maestro/phase1.yaml]` does build, install, start, flow, screenshot and logcat in one go
(set `FIRE_TV_IP` and `ROON_TEST_ZONE` in `config/local.env`, see `config/local.env.example`).

## Development

- All tooling runs in Docker: `scripts/dock ./gradlew test` (JVM tests and Roborazzi screenshots).
- `roon-client`: protocol library (no Android). `roon-cli`: command line tool for the real Core
  (`discover`, `info`, `register`, `zones`, `now-playing`, `watch`, `image`; playback commands are limited to `$ROON_TEST_ZONE`).
- `app`: the Android TV app. Debug builds accept `adb shell am broadcast -a io.github.knoellp.roomscreen.DUMP_STATE`
  (also `SET_HOST`, `RESET`); log tag `Roomscreen`.

Privacy: the app stores only the Roon token, the Core address and the chosen zone, locally.

License: Apache-2.0.
