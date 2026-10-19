# Development

Building, signing, tests, debug hooks and device scripts (moved from the README). Hard constraints and rules: `CLAUDE.md`. Test and device status: `STATUS.md`.

## Build, install and signing (Fire TV, ADB)

1. On the Fire TV: Settings, My Fire TV, Developer options: turn on ADB debugging (and "Apps from unknown sources").
2. Build the debug APK (needs Docker; no local JDK/SDK): `docker build -t roomscreen-android docker/android-build`, then
   `scripts/dock ./gradlew :app:assembleDebug`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.
3. `adb connect <fire-tv-ip>:5555` and confirm the dialog on the TV, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Start it from the apps list, or `adb shell am start -n io.github.knoellp.roomscreen/.MainActivity`.

Signing: debug and release builds are signed with the same release key when `config/signing.env` is present (see `RELEASING.md`;
without it the debug build uses the Android debug key). Certificate SHA-256 digest (also in `config/release-fingerprint.txt`):
`76bd1d41f70843b362806bbb81cc1a7dce77c51d87ccbd27059edafb18b3c6fb`. Check an APK with
`scripts/dock /opt/android-sdk/build-tools/36.0.0/apksigner verify --print-certs <apk>`. Release build: `scripts/dock ./gradlew :app:assembleRelease`
(`app/build/outputs/apk/release/app-release.apk`, version 1.0.0, no R8). The key move from the old debug key to this key needs a one-time
uninstall on the stick and a new "Enable" in Roon (done once on 2026-10-18, see `ARCHITECTURE.md` section 11).
Always sign with the same key and use `install -r` so updates keep the stored token.
`scripts/deploy-test.sh [--flow maestro/phase1.yaml]` does build, install, start, flow, screenshot and logcat in one go
(set `FIRE_TV_IP` and `ROON_TEST_ZONE` in `config/local.env`, see `config/local.env.example`).
`scripts/phase2-device.sh` and `scripts/phase2-perf.sh` run the Phase 2 flows and the jank/memory measurements (zone Büro only).
`scripts/phase4-resilience.sh restart-playing|restart-paused|wifi|dead-host` (Core software restart, Wi-Fi cut, dead address), `scripts/phase4-perf.sh`, `scripts/phase4-jank.sh` (memory and jank) and `scripts/phase4-soak.sh` (2 h soak) are the Phase 4 device runs; they share `scripts/phase4-lib.sh`. The Core address always comes from the app's `DUMP_STATE`, never from a script constant.
`scripts/acceptance-firetv.sh` (Fire TV version 1 acceptance, criteria 1 to 12), `scripts/f3-manual-entry.sh` (manual entry and `CLEAR_ENDPOINT`) and `scripts/maestro-regression.sh` (all eleven Maestro flows with a defined start state; the eleventh, `maestro/f3-dpad.yaml`, is also run alone by `scripts/f3-dpad.sh` and checks the direct D-pad control, see `ARCHITECTURE.md` section 11.9) were run on the stick on 2026-10-18/19 (`ARCHITECTURE.md` section 11). `scripts/device-steps.sh` refuses to install when the built or the installed app is signed with another key than `config/release-fingerprint.txt` (`--allow-key-change` only turns the abort into a warning; nothing is ever uninstalled).
`scripts/phase3-device.sh` runs the Phase 3 lyrics flows (`maestro/phase3-*.yaml`), `scripts/phase3-mem.sh` and `scripts/phase3-gl.sh` the memory probes (zone Büro only; Büro ends paused).

## Tests, modules and debug hooks

- All tooling runs in Docker: `scripts/dock ./gradlew test` (JVM tests and Roborazzi screenshots).
- `roon-client`: protocol library (no Android). `roon-cli`: command line tool for the real Core
  (`discover`, `info`, `register`, `zones`, `now-playing`, `watch`, `image`, `artist-image`, `lyrics`; playback commands are limited to `$ROON_TEST_ZONE`;
  `lyrics [--zone NAME]` is read-only and asks the real LRCLIB for the current track of each zone).
- `lyrics`: pure JVM module (LRC parser, line sync, query variants, LRCLIB client, cache), used by the app and the CLI.
- `app`: the Android TV app. Debug builds accept broadcasts that must name the package (an implicit broadcast is
  silently dropped): `adb shell am broadcast -p io.github.knoellp.roomscreen -a io.github.knoellp.roomscreen.DUMP_STATE`
  (also `DUMP_ZONES`, `SET_HOST` with `--es host H --ei port P`, `SELECT_ZONE` with `--es name N`, `SET_VIA_ROON` with `--es value on|off`, `SET_LYRICS` with `--es value on|off` (presses the Lyrics button; `DUMP_STATE` then shows `lyrics_on` and `lyrics`), `SET_LRCLIB` with `--es value on|off` (the "Lyrics (LRCLIB)" setting), `CLEAR_ENDPOINT` (forgets only host, port and the manual flag; token and zone stay), `SET_LYRICS_OFFSET` with `--es value MS` (-3000 to 3000), `SET_SCREEN_ON` with `--es value always|playing`, `SET_SHARPNESS` with `--es value soft|sharp`, `SET_BACKGROUND` with `--es value cover|artist|color`, `RESET`). `DUMP_STATE` also reports `seek` (seconds). Log tag `Roomscreen`; debug builds also log each zone change as `zones_msg`.

## Shared specification and test data

`SPEC.md` is the platform-neutral specification derived from this app (screens, connection, zones, progress, control, settings, lyrics, platform interface); it is the
reference for the planned web core (Samsung Tizen, Fire TV Vega, `PLAN.md` sections 4 and 6). `shared/` holds data both implementations use:
`fixtures/` (recorded Core messages), `testvectors/` (JSON cases for the pure logic, read by JUnit 5 `@TestFactory` tests in `roon-client` and `lyrics`),
`design/tokens.json` (checked against `Tokens.kt` by `SharedConsistencyTest`) and `strings/en.json`, `de.json` (checked against the Android string resources).

## Behaviour (connection, zones, screen)

- Start: tries the last known Core address with a quick TCP probe; only if that fails it runs SOOD
  discovery (and holds a multicast lock for that time). A manual address skips discovery.
- First run: the Core asks for authorization. The app shows where to enable it; once enabled the
  token is stored and reused.
- Zone: pick it once; it is remembered. Press Up or Down on Now Playing to show the control bar with the **Zones**
  button, select it to switch zones. Back from the zone list returns to Now Playing unchanged.

- Groups: the selection follows Roon groups. Roon gives a group a new zone id but keeps the member output ids, so
  when the selected zone is grouped the app shows the group, and switches back when the group dissolves. The saved
  selection is never cleared automatically; if no zone matches, a hint is shown and the zone reappears by itself.
- Screen: by default the display is kept on (no screensaver) for as long as Roomscreen is in the foreground, in every zone state; the setting "Screen on" can limit this to playback.

Privacy: the app stores only the Roon token, the Core address, the chosen zone and the display settings, locally.
