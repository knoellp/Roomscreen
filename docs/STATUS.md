# Status (version 1)

Test and device status of the Fire TV version 1 (moved from the README; the README keeps only the known limitations). Direct D-pad control (2026-10-19) is described in `ARCHITECTURE.md` section 11.9.

## Feature status

| Area | Automated tests | Verified on device (Fire TV Stick HD, Fire OS 7.7.1.7, API 28) | Open |
|---|---|---|---|
| SOOD discovery | codec, responder (JVM) | finds the Core on the Fire TV | multicast lock necessity not isolated |
| Cached endpoint first, discovery as fallback | fake Core | restart connects in ~20 ms with `endpoint=cached` | - |
| Registration, "Enable", token reuse | fake Core (recorded messages) | enabled once, later starts reuse the token | - |
| Zones, track change, pause, seek | recorded fixtures | now playing updates after a track change | - |
| Cover art | - | shown (HTTP image URL, no auth needed) | missing-cover case only in tests |
| Zone list, Zones button, saved zone | screenshots (Roborazzi) | Maestro flows `maestro/phase1*.yaml` | - |
| Reconnect after Core restart (acceptance criterion 10) | fake Core: socket close, server down for 3 attempts then up, full restart, silence watchdog, token rejected | **met on the real Core, 2026-10-18**: software restart of the ROCK (`/1/restartsoftware`) with Büro playing and with Büro paused; the app reconnected without any input, the zone was back 41 s after the connection dropped (Roon needs about 35 s to publish zones after it restarted); `scripts/phase4-resilience.sh`, `ARCHITECTURE.md` section 10 | - |
| Wi-Fi cut on the stick (20 s) | - | connection loss noticed at once, reconnect with backoff, Connected 29 s after the cut started (about 9 s after Wi-Fi was back), Now Playing correct | - |
| Core address wrong or unreachable | fake Core (blackhole endpoint, discovery fallback) | manual address 192.0.2.1: after 5 failed attempts discovery found the Core again (84 s), hint "Core not reachable" after 20 s | - |
| Lifecycle (Home key, Activity restart) | fake Core: start/stop/start never has two sockets open | Home, 3 s, start: one `websocket_open` and one `register_sent`; Home with immediate start: connection kept (`lifecycle=disconnect_cancelled`) | - |
| Lyrics timing setting (-3.0 to +3.0 s) | `LyricsSyncTest`, `SettingsPrefsTest`, screenshot `7d-settings-offset` | +3 s moved the active line by one at once (`lyrics active=41 offset_ms=0` then `lyrics active=42 offset_ms=3000`), screenshots `p4-41-*` | - |
| Soak, 2 hours, Büro playing, lyrics on, sharp artist image | - | **done 2026-10-18**: 13 samples over 121 min, connection stayed Connected without any reconnect, no crash or ANR, PSS 194 MB at the start (after warm-up) and 157 MB at the end, GL mtrack 38 to 99 MB without trend (`scripts/phase4-soak.sh`) | see `ARCHITECTURE.md` section 10.5 |
| Manual host/port entry | screenshot only | **done 2026-10-18**: `scripts/f3-manual-entry.sh` green: host typed with the remote, `endpoint=192.168.1.194:9330` saved, Connected; after `CLEAR_ENDPOINT` and restart `discovery result=found` (`ARCHITECTURE.md` section 11.3) | - |
| Several devices with the same Roon | - | **done 2026-10-18**: app and `roon-cli watch` for 30 min: 0 `websocket_failure` / `Reconnecting` / `conn state=` lines in the app log, Connected at the end, CLI exit 0 (section 11.4) | - |
| Acceptance, Fire TV version 1 (criteria 1 to 12) | - | **done 2026-10-18**: `scripts/acceptance-firetv.sh` 11 pass, 0 fail, 1 reference (12 = section 10.2); Maestro regression 10 of 10 flows pass (section 11.6) | - |
| Loading indicator on the connecting screen | Roborazzi `1-connecting*` | **done 2026-10-18**: device photo `p5-01-connecting.png` shows the spinner | - |
| Now Playing layout (blurred backdrop, info strip, cover, progress) | Roborazzi: playing, paused, stopped, no cover, long texts, German | screenshots on the device, 0.5 % janky frames over 60 s of playback | - |
| Progress between the 1 Hz seek events | `ProgressInterpolatorTest` (10 cases) | bar and time follow playback (screenshots) | - |
| Remote media keys (play/pause, rewind, fast forward, next, previous) | key mapping tests | Maestro `pressKey` (codes 85, 89, 90, 87, 88) and `adb input keyevent`; commands arrive at the Core | - (the physical remote was pressed by the user on 2026-10-18/19: Up, play/pause, fast forward, rewind acted on Büro; `ARCHITECTURE.md` section 11.4) |
| Control bar (Zones, Settings, previous, play/pause, next) | screenshot | `maestro/phase2.yaml`: appears on input, hides after 5 s, D-pad navigation, focus ring | - |
| Direct D-pad control (Left/Right = previous/next, OK = play/pause while the bar is hidden; Up/Down show the bar) | `MainViewModelKeyTest` (12 cases), Roborazzi images unchanged | **done 2026-10-19**: `scripts/f3-dpad.sh` / `maestro/f3-dpad.yaml` on the stick, log lines `dpad direct key=... control=...` (`ARCHITECTURE.md` section 11.9) | the user still has to try it at the TV |
| Settings screen and persistence | `SettingsPrefsTest`, screenshots (EN, DE) | `maestro/phase2-settings.yaml`, value survives a force-stop and restart; "Screen on: only while playing" checked with `dumpsys power`: no `SCREEN_BRIGHT_WAKE_LOCK` for the app while Büro was paused, the lock came back when it played (setting restored to "Always") | - |
| Artist image background | - | switched on in the settings, screenshot | - |
| Sharp background and colour background | unit tests (crop, sharp decode size), Roborazzi screenshots `6i`, `6j`, `7c` | `p4-01-sharp-artist.png`, `p4-02-sharp-lyrics.png` (sharp artist image, lyrics readable), `p4-03-color-background.png` (flat anthracite, no gradient), `DUMP_STATE` shows `sharpness=SHARP` and `background=COLOR` / `ARTIST` | sharp mode: TOTAL PSS 128 MB before, 166 MB after 10 track changes, 154 MB 30 s later (soft mode: 136 to 138 MB after 10 changes); frame timing not measured |
| "via roon" hint | screenshots | - (see below) | the user has to approve the look first |
| Synchronized lyrics (LRCLIB): lookup, cache, LRC parser, line sync | 48 JVM tests in `lyrics` (parser, sync, query variants, MockWebServer client, repository); `roon-cli lyrics` against the real LRCLIB (`ARCHITECTURE.md` section 9) | "Lyrics" bar button, lookup log lines, cancellation on track change, no request while off, setting persists over a restart (`maestro/phase3-*.yaml`, `scripts/phase3-device.sh`) | see "Not verified" below |
| Lyrics screens (synchronized, plain, none) | Roborazzi: `9-lyrics-synced`, `9b-lyrics-plain`, `9c-lyrics-none`, `6h-now-playing-controls-lyrics`, `9d-lyrics-none-de`, `7-settings`; `LyricsViewTest` (D-pad scroll of plain text) | synchronized view on the device (screenshots) | plain text and "No lyrics found" only in screenshots/tests (depends on the track Büro happens to play) |
| Key sounds setting (default off; Compose focus/click sounds silenced) | `SoundEffectsTest`, `SettingsPrefsTest`, Roborazzi `7-settings*` | **done 2026-10-19**: `DUMP_STATE` `keySounds=false` after start, `SET_KEY_SOUNDS on` gives `sound effects=on`; regression 11 of 11 (`ARCHITECTURE.md` section 11.10) | the user has to confirm at the TV that the sounds are gone; not verifiable over ADB |
| Active lyrics line inside the column | Roborazzi `9e-lyrics-long-active`, `LyricsViewTest` width check | synced lyrics on the stick (short lines only) | a long wrapped active line on the real device not seen (`ARCHITECTURE.md` section 11.11) |
| Volume panel (speaker button, group row, click rule 1/3/5, Sonos group maths, coalesced `change_volume`) | `volume.json` vectors (25 cases) and `change_volume` fake-Core tests in `roon-client`; `VolumeControllerTest` (19), `VolumeKeysTest` (7); Roborazzi `10-volume-single`, `10b-volume-group`, `10c-volume-group-de`, bar images `6c`, `6g`, `6h`, `9d` with the new button | **verified on the stick 2026-10-20**: real group Büro + Fernseher (group +3/-3, single row, Back, 8 s close, `DUMP_STATE` = `roon-cli zones`); `scripts/f4-volume.sh` single zone all PASS (open through the bar, +3 = 24 in app and Core, -3 = 21, Back closes, 8 s closes, start value restored); Maestro regression 11/11 PASS | the user at the TV: speaker button, click rule with a held key, group (`ARCHITECTURE.md` section 11.12) |
| Reconnect with "Search for Core again" | - | `maestro/phase2-connection.yaml`, log shows `endpoint=cache_skipped`, discovery, connected | - |
| Release pipeline (GitHub Actions: image build, keystore and certificate checks, version check, assets, `releases/`) | `scripts/release-build.sh` locally in the container (same certificate) | **dry run 2026-10-20**: run 39461590571 green, artifact only, nothing published (`ARCHITECTURE.md` 12.1) | first real release run (publish job, version check) |
| Diagnostics overlay (build, device, connection, last 20 inputs, 200 log lines; five taps bottom right, settings row, `OPEN_DIAGNOSTICS`) | `LogBufferTest`, `InputTraceTest`, `TapCounterTest`, `DiagnosticsTest`, roon-client diagnostics tests, Roborazzi `11-diagnostics`, `11b-diagnostics-de` | **done 2026-10-21**: `scripts/f3b-diagnostics.sh` all PASS: real values in the overlay, Down scrolls without side effects, Back closes, 4 taps no / 5 taps yes, no audio focus (12.2) | - |
| Lifecycle with a system overlay (onStop/onStart while a track changes) | fake Core start/stop tests | **done 2026-10-21**: `scripts/f3b-lifecycle.sh` all PASS with Home (no ADB key code opens the Alexa overlay): new title after return, one disconnect, one reconnect (12.3) | the real Alexa overlay needs the voice remote |
| Touch (tap shows the bar, every button and row tappable, Close rows, 48 dp rows, keyboard on tap) | `TouchTest` (13), `MainViewModelKeyTest` touch cases, Roborazzi `4c-zone-list-end`, `7e-settings-end` | **done 2026-10-21**: `scripts/f3b-touch.sh` all PASS (bar by tap, play/pause/next by tap, zone and Close rows, settings rows, swipe, lyrics button, mixed keys and taps, keyboard on tap in the host field); key regression 11/11 afterwards (`ARCHITECTURE.md` 12.6) | the Echo Show itself (F5); the user at the TV has no touch device |
| Manifest, densities, configuration change | `ManifestTest` (5), `DensityScreenshotTest` (18 images), `DensityLayoutTest` (6), `ConfigChangeTest` (2) | - (no device part) | real density of the Echo Show (F5) |
| LAT preparation (`docs/store/`) | `StoreAssetsTest` renders the icons | - | store screenshots, the maintainer's own data, content rating for explicit lyrics |

Phase 4 (stability and polish): silence watchdog (30 s without a frame from the Core closes the connection), backoff capped at 30 s,
rediscovery after 5 failed attempts, delayed disconnect (2 s) when the activity stops, evicted backdrop bitmaps are recycled, lyrics
view without per-line text re-layout, dark scrim under the lyrics on sharp backgrounds, "Lyrics timing" setting, fading control bar,
connection hint after 20 s. Measurements: `ARCHITECTURE.md` section 10.

Phase F3 (code and documents, done): release signing with one key for debug and release (`RELEASING.md`), loading indicator on the connecting screen
(Roborazzi `1-connecting`, `1b-connecting-slow`), debug hook `CLEAR_ENDPOINT`, `scripts/acceptance-firetv.sh` and the repaired Maestro flows (`scripts/maestro-regression.sh`),
`SPEC.md`, shared test data (`shared/fixtures`, `shared/testvectors`, `shared/design/tokens.json`, `shared/strings`) with tests on the Kotlin side. **Device proofs done
(2026-10-18/19):** the one-time key change on the stick (release key installed, extension enabled again), the acceptance script (11 pass, 1 reference), the ten Maestro flows (10 of 10 after the repair), the manual-entry run, the
30 minute two-instance run, the physical remote (user at the TV), the memory criterion below. Details: `ARCHITECTURE.md` section 11.

Not implemented yet: Echo Show 15 (Live App Testing itself, phase F5). Phase F3b (release pipeline, diagnostics overlay, touch, LAT files) is recorded in the rows above and in `ARCHITECTURE.md` chapter 12.

Not verified (Phase 4): the Maestro zone-list flow `maestro/phase1-zones.yaml` passes up to
the zone name assertion with `-e ROON_TEST_ZONE=...` but its last step (title of the zone list) failed on the stick once and was not investigated;
the Core was restarted only through the web UI's software restart, not a reboot of the machine; Wi-Fi cut tested once with playback running.

Not verified (Phase 3): the plain-text lyrics view and the "No lyrics found" line were only checked in screenshots and tests, not on the
device (they depend on which track Büro plays); the D-pad scrolling of plain text is covered by a Compose test only; the physical remote was pressed later (section 11.4); the German lyrics strings were seen on the device only for the button and the settings row.
LRCLIB often answers 503 (overloaded): the app then shows the normal view and asks again at the next track, so on some tracks no text appears.
Memory: with lyrics on, the total PSS after 10 track changes was 123 to 193 MB (lyrics off: 121 to 138 MB), mostly purgeable GPU cache; the plan's
+-10 MB tolerance against Phase 2 is **not** met (details in `ARCHITECTURE.md` section 9.5). Memory: after the image cache was capped to 8 MB, 20 track changes moved the total PSS by
+3 to +13 MB after settling (two runs); the earlier uncapped cache grew by about 55 MB.

## Acceptance criteria (version 1, `PLAN.md` section 12)

Criteria 1 to 9 were met in Phases 1 to 3 (see the table above). **Criterion 10 (reconnect after a restart of the Roon Server) is met since 2026-10-18**:
the Roon software on the ROCK was restarted through its web UI (`POST /1/restartsoftware`, no reboot) twice, once while Büro played and once
while it was paused. In both runs the app noticed the loss at once (`conn=websocket_failure error=EOFException`), reconnected without any
input, re-registered with the stored token, and showed Now Playing for Büro again. Recovery time from the dropped connection to the zone
shown again: 41 s (playing) and 41 s (paused); about 35 s of this is the Core itself, which accepts connections 5 s after the restart but
publishes zones only after Roon has started up. Roon pauses playback when it restarts, so both runs ended with Büro paused.

**Memory criterion (version 1, measured 2026-10-18, met):** total PSS after 30 track changes with lyrics and a sharp artist image stays under 200 MB,
with no trend over the soak run (`ARCHITECTURE.md` sections 9.5 and 11.5).

| Point (`scripts/phase3-mem.sh on 30 10`) | Total PSS |
|---|---|
| start (after 10 warm-up changes) | 194.0 MB |
| after 30 changes | **196.3 MB** (criterion: under 200 MB) |
| 30 s later | 189.3 MB |
| maximum during the run (after change 8) | 218.0 MB |

The criterion holds at the defined point; between changes 1 and 21 the total was above 200 MB in every sample (transient GPU cache, 201 to 218 MB), the margin at the measuring point is 3.7 MB.
No upward trend (the last nine values are 194 to 196 MB; the 2 h soak ended at 157 MB).
