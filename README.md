# Roomscreen

*A Now Playing display for every room - for Roon on Fire TV and Android TV.*

A native Android TV app (Kotlin, Compose for TV) that shows what is playing in one Roon zone.
It talks directly to the Roon Core (SOOD discovery and MOO over WebSocket). There is no helper
service, no bridge and no WebView.

> Roomscreen is not affiliated with or endorsed by Roon Labs. Roon is a trademark of Roon Labs LLC.

**Platform note.** This APK runs only on devices with Fire OS (Android based), for example the Fire TV Stick HD (2020) and Android TV
devices. It does **not** run on devices with Vega OS (Fire TV Stick 4K Select, Fire TV Stick HD 2026).

Full project brief: `docs/PLAN.md`. Protocol findings and decisions: `docs/ARCHITECTURE.md`.

## Status (Phase 4)

| Area | Automated tests | Verified on device (Fire TV Stick HD, Fire OS 7.7.1.7, API 28) | Open |
|---|---|---|---|
| SOOD discovery | codec, responder (JVM) | finds the Core on the Fire TV | multicast lock necessity not isolated |
| Cached endpoint first, discovery as fallback | fake Core | restart connects in ~20 ms with `endpoint=cached` | - |
| Registration, "Enable", token reuse | fake Core (recorded messages) | enabled once, later starts reuse the token | - |
| Zones, track change, pause, seek | recorded fixtures | now playing updates after a track change | - |
| Cover art | - | shown (HTTP image URL, no auth needed) | missing-cover case only in tests |
| Zone list, Zones button, saved zone | screenshots (Roborazzi) | Maestro flows `maestro/phase1*.yaml` | - |
| Reconnect after Core restart (acceptance criterion 10) | fake Core: socket close, server down for 3 attempts then up, full restart, silence watchdog, token rejected | **met on the real Core, 2026-10-18**: software restart of the ROCK (`/1/restartsoftware`) with Büro playing and with Büro paused; the app reconnected without any input, the zone was back 41 s after the connection dropped (Roon needs about 35 s to publish zones after it restarted); `scripts/phase4-resilience.sh`, `docs/ARCHITECTURE.md` section 10 | - |
| Wi-Fi cut on the stick (20 s) | - | connection loss noticed at once, reconnect with backoff, Connected 29 s after the cut started (about 9 s after Wi-Fi was back), Now Playing correct | - |
| Core address wrong or unreachable | fake Core (blackhole endpoint, discovery fallback) | manual address 192.0.2.1: after 5 failed attempts discovery found the Core again (84 s), hint "Core not reachable" after 20 s | - |
| Lifecycle (Home key, Activity restart) | fake Core: start/stop/start never has two sockets open | Home, 3 s, start: one `websocket_open` and one `register_sent`; Home with immediate start: connection kept (`lifecycle=disconnect_cancelled`) | - |
| Lyrics timing setting (-3.0 to +3.0 s) | `LyricsSyncTest`, `SettingsPrefsTest`, screenshot `7d-settings-offset` | +3 s moved the active line by one at once (`lyrics active=41 offset_ms=0` then `lyrics active=42 offset_ms=3000`), screenshots `p4-41-*` | - |
| Soak, 2 hours, Büro playing, lyrics on, sharp artist image | - | **done 2026-10-18**: 13 samples over 121 min, connection stayed Connected without any reconnect, no crash or ANR, PSS 194 MB at the start (after warm-up) and 157 MB at the end, GL mtrack 38 to 99 MB without trend (`scripts/phase4-soak.sh`) | see `docs/ARCHITECTURE.md` section 10.5 |
| Manual host/port entry | screenshot only | not exercised | open |
| Several devices with the same Roon | - | - | two installs not tested together |
| Now Playing layout (blurred backdrop, info strip, cover, progress) | Roborazzi: playing, paused, stopped, no cover, long texts, German | screenshots on the device, 0.5 % janky frames over 60 s of playback | - |
| Progress between the 1 Hz seek events | `ProgressInterpolatorTest` (10 cases) | bar and time follow playback (screenshots) | - |
| Remote media keys (play/pause, rewind, fast forward, next, previous) | key mapping tests | Maestro `pressKey` (codes 85, 89, 90, 87, 88) and `adb input keyevent`; commands arrive at the Core | the physical Fire TV remote was not pressed |
| Control bar (Zones, Settings, previous, play/pause, next) | screenshot | `maestro/phase2.yaml`: appears on input, hides after 5 s, D-pad navigation, focus ring | - |
| Settings screen and persistence | `SettingsPrefsTest`, screenshots (EN, DE) | `maestro/phase2-settings.yaml`, value survives a force-stop and restart; "Screen on: only while playing" checked with `dumpsys power`: no `SCREEN_BRIGHT_WAKE_LOCK` for the app while Büro was paused, the lock came back when it played (setting restored to "Always") | - |
| Artist image background | - | switched on in the settings, screenshot | - |
| Sharp background and colour background | unit tests (crop, sharp decode size), Roborazzi screenshots `6i`, `6j`, `7c` | `p4-01-sharp-artist.png`, `p4-02-sharp-lyrics.png` (sharp artist image, lyrics readable), `p4-03-color-background.png` (flat anthracite, no gradient), `DUMP_STATE` shows `sharpness=SHARP` and `background=COLOR` / `ARTIST` | sharp mode: TOTAL PSS 128 MB before, 166 MB after 10 track changes, 154 MB 30 s later (soft mode: 136 to 138 MB after 10 changes); frame timing not measured |
| "via roon" hint | screenshots | - (see below) | the user has to approve the look first |
| Synchronized lyrics (LRCLIB): lookup, cache, LRC parser, line sync | 48 JVM tests in `lyrics` (parser, sync, query variants, MockWebServer client, repository); `roon-cli lyrics` against the real LRCLIB (`docs/ARCHITECTURE.md` section 9) | "Lyrics" bar button, lookup log lines, cancellation on track change, no request while off, setting persists over a restart (`maestro/phase3-*.yaml`, `scripts/phase3-device.sh`) | see "Not verified" below |
| Lyrics screens (synchronized, plain, none) | Roborazzi: `9-lyrics-synced`, `9b-lyrics-plain`, `9c-lyrics-none`, `6h-now-playing-controls-lyrics`, `9d-lyrics-none-de`, `7-settings`; `LyricsViewTest` (D-pad scroll of plain text) | synchronized view on the device (screenshots) | plain text and "No lyrics found" only in screenshots/tests (depends on the track Büro happens to play) |
| Reconnect with "Search for Core again" | - | `maestro/phase2-connection.yaml`, log shows `endpoint=cache_skipped`, discovery, connected | - |

Phase 4 (stability and polish): silence watchdog (30 s without a frame from the Core closes the connection), backoff capped at 30 s,
rediscovery after 5 failed attempts, delayed disconnect (2 s) when the activity stops, evicted backdrop bitmaps are recycled, lyrics
view without per-line text re-layout, dark scrim under the lyrics on sharp backgrounds, "Lyrics timing" setting, fading control bar,
connection hint after 20 s. Measurements: `docs/ARCHITECTURE.md` section 10.

Not implemented yet: Echo Show 15 (Live App Testing, touch layout), the release pipeline (GitHub secrets, phase F3b). Release signing exists (`docs/RELEASING.md`); the stick still runs a build with the old debug key until the one-time key change.

Not verified (Phase 4): the Maestro zone-list flow `maestro/phase1-zones.yaml` passes up to
the zone name assertion with `-e ROON_TEST_ZONE=...` but its last step (title of the zone list) failed on the stick once and was not investigated;
the Core was restarted only through the web UI's software restart, not a reboot of the machine; Wi-Fi cut tested once with playback running.

Not verified (Phase 3): the plain-text lyrics view and the "No lyrics found" line were only checked in screenshots and tests, not on the
device (they depend on which track Büro plays); the D-pad scrolling of plain text is covered by a Compose test only; the physical remote was not
pressed; the German lyrics strings were seen on the device only for the button and the settings row.
LRCLIB often answers 503 (overloaded): the app then shows the normal view and asks again at the next track, so on some tracks no text appears.
Memory: with lyrics on, the total PSS after 10 track changes was 123 to 193 MB (lyrics off: 121 to 138 MB), mostly purgeable GPU cache; the plan's
+-10 MB tolerance against Phase 2 is **not** met (details in `docs/ARCHITECTURE.md` section 9.5). Memory: after the image cache was capped to 8 MB, 20 track changes moved the total PSS by
+3 to +13 MB after settling (two runs); the earlier uncapped cache grew by about 55 MB.

## Acceptance criteria (version 1, `docs/PLAN.md` section 12)

Criteria 1 to 9 were met in Phases 1 to 3 (see the table above). **Criterion 10 (reconnect after a restart of the Roon Server) is met since 2026-10-18**:
the Roon software on the ROCK was restarted through its web UI (`POST /1/restartsoftware`, no reboot) twice, once while Büro played and once
while it was paused. In both runs the app noticed the loss at once (`conn=websocket_failure error=EOFException`), reconnected without any
input, re-registered with the stored token, and showed Now Playing for Büro again. Recovery time from the dropped connection to the zone
shown again: 41 s (playing) and 41 s (paused); about 35 s of this is the Core itself, which accepts connections 5 s after the restart but
publishes zones only after Roon has started up. Roon pauses playback when it restarts, so both runs ended with Büro paused.

## Remote control

| Key | Action |
|---|---|
| Play/Pause | play or pause the selected zone (the label switches at once, the Core confirms) |
| Rewind / Media previous | previous track |
| Fast forward / Media next | next track |
| D-pad, select | shows the control bar; move between its buttons; select presses the focused one |
| Back | closes Settings and the zone list, otherwise leaves the app |

The control bar (Zones, Settings, Lyrics, previous, play/pause, next) is hidden by default and appears for 5 seconds
after any key press (or stays visible, see the setting). Commands are only sent if the zone reports the control as
allowed (`is_play_allowed` and so on); otherwise they are ignored and logged.

## Lyrics

The control bar has a **Lyrics** button (German: **Liedtexte**) right of Settings. It switches the lyrics view on and off;
the last choice is remembered. The text comes from [LRCLIB](https://lrclib.net) and is looked up per track (title, artist,
album, duration):

- Synchronized text (`syncedLyrics`): the active line is white and enlarged in the vertical middle, neighbouring lines are dimmed,
  the list moves when the line changes. The line appears 250 ms before its time stamp.
- Plain text only: static text; D-pad up/down scrolls it.
- Nothing found: the normal Now Playing view with a small dim line "No lyrics found" under the album.
- LRCLIB not reachable (overloaded, offline): the normal view, no message; the next track asks again.

**Privacy.** Only when lyrics are switched on, the app sends title, artist, album, duration and the User-Agent
(`Roomscreen/<version> (https://github.com/knoellp/Roomscreen)`) to lrclib.net. Nothing else is stored or transmitted beyond the Roon
token and the settings, locally on the device. While the button is off no request is made, and the setting
**Lyrics (LRCLIB)** turns LRCLIB off completely (the button disappears too). Results are cached in the app's cache directory
(at most 300 files; "not found" for 7 days, found text without limit, "unreachable" is never stored on disk).

## Settings

Open **Settings** from the control bar; select changes a value, Back returns. Values are stored on the device.

| Setting | Values (default first) |
|---|---|
| Background | album cover, artist image (falls back to the cover when the track has no artist image), colour (no image and no gradient: the whole area is the chosen background colour) |
| Background sharpness | Soft (default, blurred 256 px backdrop) or Sharp (artist image loaded at 1280 x 720, cover at 720 x 720 and centre-cropped to 16:9, no blur, full opacity; the dark gradient stays so text remains readable). Hidden while the background is "Colour". |
| Background colour | Anthracite, Black, Midnight blue, Dark green, Wine red, Warm grey (base under the image, and the colour without cover) |
| Controls | show on input, always visible |
| Screen on | always, only while playing |
| Show "via roon" | on, off |
| Lyrics (LRCLIB) | on, off (off: no requests to lrclib.net at all, no Lyrics button) |
| Lyrics timing | 0 s (default), then -3.0 s to +3.0 s in 0.5 s steps (select cycles; shown only while LRCLIB is on). The offset is added to the playback position: **plus shows the lines earlier, minus later**. |
| Core | shows the address; "Search for Core again" drops the manual address and runs discovery; "Switch zone" |

The language follows the system (English, German). The "via roon" text is a plain text hint, not a Roon logo.

## Behaviour

- Start: tries the last known Core address with a quick TCP probe; only if that fails it runs SOOD
  discovery (and holds a multicast lock for that time). A manual address skips discovery.
- First run: the Core asks for authorization. The app shows where to enable it; once enabled the
  token is stored and reused.
- Zone: pick it once; it is remembered. Press any remote key on Now Playing to show the **Zones**
  button in the control bar, select it to switch zones. Back from the zone list returns to Now Playing unchanged.

- Groups: the selection follows Roon groups. Roon gives a group a new zone id but keeps the member output ids, so
  when the selected zone is grouped the app shows the group, and switches back when the group dissolves. The saved
  selection is never cleared automatically; if no zone matches, a hint is shown and the zone reappears by itself.
- Screen: by default the display is kept on (no screensaver) for as long as Roomscreen is in the foreground, in every zone state; the setting "Screen on" can limit this to playback.

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

Signing: debug and release builds are signed with the same release key when `config/signing.env` is present (see `docs/RELEASING.md`;
without it the debug build uses the Android debug key). Certificate SHA-256 digest (also in `config/release-fingerprint.txt`):
`76bd1d41f70843b362806bbb81cc1a7dce77c51d87ccbd27059edafb18b3c6fb`. Check an APK with
`scripts/dock /opt/android-sdk/build-tools/36.0.0/apksigner verify --print-certs <apk>`. Release build: `scripts/dock ./gradlew :app:assembleRelease`
(`app/build/outputs/apk/release/app-release.apk`, version 1.0.0, no R8). The key move from the old debug key to this key needs a one-time
uninstall on the stick and a new "Enable" in Roon (pending, see `docs/ARCHITECTURE.md` section 11).
Always sign with the same key and use `install -r` so updates keep the stored token.
`scripts/deploy-test.sh [--flow maestro/phase1.yaml]` does build, install, start, flow, screenshot and logcat in one go
(set `FIRE_TV_IP` and `ROON_TEST_ZONE` in `config/local.env`, see `config/local.env.example`).
`scripts/phase2-device.sh` and `scripts/phase2-perf.sh` run the Phase 2 flows and the jank/memory measurements (zone Büro only).
`scripts/phase4-resilience.sh restart-playing|restart-paused|wifi|dead-host` (Core software restart, Wi-Fi cut, dead address), `scripts/phase4-perf.sh`, `scripts/phase4-jank.sh` (memory and jank) and `scripts/phase4-soak.sh` (2 h soak) are the Phase 4 device runs; they share `scripts/phase4-lib.sh`. The Core address always comes from the app's `DUMP_STATE`, never from a script constant.
`scripts/phase3-device.sh` runs the Phase 3 lyrics flows (`maestro/phase3-*.yaml`), `scripts/phase3-mem.sh` and `scripts/phase3-gl.sh` the memory probes (zone Büro only; Büro ends paused).

## Development

- All tooling runs in Docker: `scripts/dock ./gradlew test` (JVM tests and Roborazzi screenshots).
- `roon-client`: protocol library (no Android). `roon-cli`: command line tool for the real Core
  (`discover`, `info`, `register`, `zones`, `now-playing`, `watch`, `image`, `artist-image`, `lyrics`; playback commands are limited to `$ROON_TEST_ZONE`;
  `lyrics [--zone NAME]` is read-only and asks the real LRCLIB for the current track of each zone).
- `lyrics`: pure JVM module (LRC parser, line sync, query variants, LRCLIB client, cache), used by the app and the CLI.
- `app`: the Android TV app. Debug builds accept broadcasts that must name the package (an implicit broadcast is
  silently dropped): `adb shell am broadcast -p io.github.knoellp.roomscreen -a io.github.knoellp.roomscreen.DUMP_STATE`
  (also `DUMP_ZONES`, `SET_HOST` with `--es host H --ei port P`, `SELECT_ZONE` with `--es name N`, `SET_VIA_ROON` with `--es value on|off`, `SET_LYRICS` with `--es value on|off` (presses the Lyrics button; `DUMP_STATE` then shows `lyrics_on` and `lyrics`), `SET_LRCLIB` with `--es value on|off` (the "Lyrics (LRCLIB)" setting), `SET_LYRICS_OFFSET` with `--es value MS` (-3000 to 3000), `SET_SCREEN_ON` with `--es value always|playing`, `SET_SHARPNESS` with `--es value soft|sharp`, `SET_BACKGROUND` with `--es value cover|artist|color`, `RESET`). Log tag `Roomscreen`; debug builds also log each zone change as `zones_msg`.

Privacy: the app stores only the Roon token, the Core address, the chosen zone and the display settings, locally.

License: Apache-2.0.

Bundled font (SIL Open Font License 1.1, text in `docs/fonts/comfortaa-OFL.txt`): Comfortaa from
https://github.com/google/fonts, used for the "roon" word of the "via roon" hint.
