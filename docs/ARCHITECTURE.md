# Roomscreen Architecture

Status: Phase 4 (sections 8 to 10). Sections 1-6 were written in Phase 0; section 7 records
what Phase 1 resolved against the real Core (2026-10-17). Items are marked **[verified]**
(read in source and/or observed on the real Core in this repo's LAN),
**[source-only]** (read in reference source, not yet observed on the wire) or
**[unverified]**.

## 1. Decision: Option A, native Kotlin implementation

Roomscreen implements SOOD, MOO and the three required services (registry,
transport, image) itself in Kotlin, using OkHttp for the WebSocket and a plain
`DatagramSocket` for discovery.

### Evaluation

| Criterion | A: native Kotlin | B: roon-rs via JNI/UniFFI | C: rust-roon-api via JNI |
|---|---|---|---|
| Needed protocol scope | SOOD (~100 lines), MOO framing (~150), registry, ping, transport (zones, control, seek), image: small | Full SDK, we use a fraction | Same |
| Build complexity | Plain Gradle, no NDK | Rust toolchain, NDK, cross-compile per ABI, reqwest/TLS deps to check | Same, plus alpha state, not on crates.io |
| Debuggability | Full: JVM CLI, fake core, standard Android tooling | Native crashes across FFI boundary | Same |
| Maintainability | One language, one build | Two languages, UniFFI glue, upstream is a small single-developer project (last push 2026-08-17) | Upstream last push 2024-08-01, explicitly alpha |
| License | Own code, Apache-2.0 | Apache-2.0 per GitHub metadata | MIT |
| Tests | Pure JVM unit tests plus fake core | Needs device/emulator for FFI | Same |

Rationale: the protocol surface we need is small and fully documented by two
independent sources (the official `node-roon-api` and `roon-rs` docs), and the
brief prefers Option A when the scope is manageable. The reference files read
for this decision total about 800 lines of JavaScript including the services
(sood.js 233, moo.js 212, moomsg.js 96, transport-websocket.js 92, core.js 17,
transport lib.js, image lib.js). No NDK, no cross-compilation, and the
protocol code is a pure JVM module that is testable without a device.

Not chosen: B and C add a second toolchain and an FFI boundary for functionality
that is cheaper to write than to bind. Neither was build-tested on Android here.

## 2. Protocol notes

Sources: `RoonLabs/node-roon-api` (Apache-2.0, package.json version 1.2.3):
`sood.js`, `moo.js`, `moomsg.js`, `transport-websocket.js`, `core.js`, `lib.js`;
`RoonLabs/node-roon-api-transport` `lib.js` + JSDoc; `RoonLabs/node-roon-api-image`
`lib.js`; `shin1ohno/roon-rs` `docs/protocols/SOOD.md` and `MOO.md`. Observations
marked "live" come from `scripts/sood-probe.py` and `scripts/moo-info-probe.py`
run on 2026-10-17 against the Core in this LAN (Roon Optimized Core Kit, 2.73
build 1700 production).

### 2.1 SOOD discovery

- UDP port 9003, multicast group 239.255.90.90, TTL 1. node-roon-api sends each
  query to the multicast group and to the per-interface subnet broadcast
  address (`sood.js` `query`). **[verified]**
- Packet: `"SOOD"` (4 bytes), version byte `0x02`, type byte `'Q'` or `'R'`, then
  properties until end of datagram. Each property: `name_len` (1 byte, must be
  > 0), name (UTF-8), `value_len` (2 bytes big-endian; `0xFFFF` = null, `0` =
  empty string), value (UTF-8). Parser must reject truncated packets
  (`sood.js` `_parse`). **[verified]**
- Query properties: `query_service_id` = `00720724-5143-4a9b-abac-0e50cba674bb`
  (Core service id, `lib.js`) and `_tid` (UUID v4; added automatically if
  missing). A 104-byte packet results. **[verified live]**
- Reserved properties: `_replyaddr` and `_replyport` override the sender
  address/port and are stripped from the props (`sood.js`). Not seen in live
  replies.
- Live reply (type `R`), sent from 192.168.1.194:9003 to our ephemeral source
  port:
  `name=Roon Optimized Core Kit, display_version=2.73 (build 1700) production,
  unique_id=b4fe5d4d-cdc1-45b6-8818-01a39eda3309, service_id=00720724-...,
  tcp_port=9150, http_port=9330, https_port=55000, _tid=<echo>`. **[verified live]**
  The client must filter on `service_id` and use `unique_id` as the Core key
  (`lib.js`). The WebSocket port is `http_port` (9330 here); TCP connect to
  9330, 9150 and 55000 succeeded.
- Observation: one multicast query produced two replies, one echoing our `_tid`
  and one with `_tid=00000000-0000-0000-0000-000000000000`. The subnet broadcast
  (192.168.1.255) produced no reply from this host in a 3 s window. Clients
  must therefore de-duplicate by `unique_id` and must not depend on broadcast.
  The cause of the second reply is unknown. **[verified live, cause unverified]**
- Scheduling in node-roon-api: query at start and on interface change, then every
  10 s; while not paired, every tick for the first 6 ticks, afterwards every
  6th tick (`lib.js` `periodic_scan`). Interfaces are re-polled every 5 s.
  **[source-only]**

### 2.2 Transport: WebSocket at `/api`

- URL `ws://<host>:<http_port>/api`; all frames binary, client frames masked
  (`transport-websocket.js`). **[verified]** Live: the HTTP upgrade to
  `/api` on 192.168.1.194:9330 returned `101 Switching Protocols` and replies
  arrive as WebSocket opcode 2 (binary). **[verified live]**
- Heartbeat: client sends a WebSocket ping every 10 s and terminates the socket if
  no pong arrived before the next tick (`transport-websocket.js`). OkHttp's
  `pingInterval(10s)` gives equivalent behaviour. **[source-only]**
- Separately, the Core calls `com.roonlabs.ping:1/ping` on the extension; node-roon-api
  always provides this service and answers `COMPLETE Success` (`lib.js`
  `init_services`). We must provide it. Consequence of not answering is
  **[unverified]**.

### 2.3 MOO framing

```
MOO/1 <VERB> <name>\n
Header: value\n
...\n
\n
<body bytes>
```

- Verbs: `REQUEST` (name = `service/method`), `CONTINUE` and `COMPLETE`
  (name = status such as `Success`, `Subscribed`, `Changed`, `Registered`,
  `InvalidRequest`). **[verified]** (`moo.js` `parse`, `moomsg.js`)
- Headers: `Request-Id` (required), `Content-Length` and `Content-Type` (both
  required if a body exists, and a `Content-Type` without `Content-Length` is
  rejected), `Logging: quiet` optional. Other headers are kept in a map.
  Header lines are matched with `([^:]+): *(.*)`. **[verified]**
- Header order is not fixed: the live `registry:1/info` reply sent
  `Content-Type` before `Request-Id` and `Content-Length`. The parser must not
  depend on order. **[verified live]**
- Body is JSON only if `Content-Type` is exactly `application/json`; any other
  type is raw bytes (images). `Content-Length` counts bytes, not characters.
  **[verified]**
- Request ids: per-side counter starting at 0 and incrementing per REQUEST;
  CONTINUE does not release an id, COMPLETE does. A reply with an unknown
  `Request-Id` makes node-roon-api close the connection (`moo.js`
  `handle_response`, `lib.js`). On close all pending callbacks are invoked
  without arguments. **[verified]**
- Subscriptions: client sends `service/subscribe_<name>` with
  `{"subscription_key": n, ...args}` (`moo.js` `_subscribe_helper`; the key is a
  per-connection counter). Reply stream: `CONTINUE Subscribed` then
  `CONTINUE Changed` until `service/unsubscribe_<name>` with the same key.
  **[source-only]**
- Live `info` exchange, request `MOO/1 REQUEST com.roonlabs.registry:1/info`
  `Request-Id: 0`; reply:
  `MOO/1 COMPLETE Success`, `Content-Type: application/json`, `Request-Id: 0`,
  `Content-Length: 140`, body
  `{"core_id":"b4fe5d4d-cdc1-45b6-8818-01a39eda3309","display_name":"Roon Optimized Core Kit","display_version":"2.73 (build 1700) production"}`.
  `core_id` equals the SOOD `unique_id`. **[verified live]**

### 2.4 Registration and authorization

- Flow (`lib.js` `ws_connect`): `registry:1/info` -> look up the stored token for
  `core_id` -> `registry:1/register` with
  `{extension_id, display_name, display_version, publisher, email, [website],
  required_services[], optional_services[], provided_services[], [token]}`.
  `email` and `publisher` are mandatory in node-roon-api. **[verified]**
- Success response is named `Registered` and carries `core_id`, `display_name`,
  `display_version`, `token`, `provided_services` (names the Core offers) and
  `http_port`. node-roon-api reacts when `msg.name == "Registered"`; `roon-rs`
  documents the verb as `CONTINUE` (request stays open for the whole
  connection). **[source-only; verb not yet observed live]**
- Token: stored keyed by `core_id` (`settings.tokens[core_id]`) and sent on later
  registrations. **[verified]** (`lib.js`)
- First authorization: the node-roon-api README says that if the extension uses
  any Roon service, the user must press "Enable" in Roon settings; with no
  services Roon auto-authorizes. Whether the `register` call stays pending until
  the user enables, or answers immediately with another status, is
  **[unverified]**; Phase 1 must observe it. We deliberately did not register
  during Phase 0 so that no unwanted extension entry appears in Roon.
- Menu path: node-roon-api README says "Settings->Setup->Extensions". The brief
  mentions "Settings -> Extensions" as an alternative. Current Roon 2.73 UI
  path is **[unverified]**; to be confirmed visually by the user and then
  documented in the README and the in-app first-run hint.
- Required services to request: `com.roonlabs.transport:2` and
  `com.roonlabs.image:1`. Provided: `com.roonlabs.ping:1`. The pairing service
  (`com.roonlabs.pairing:1`) is only needed to pin one Core; we do not provide it
  and instead remember the selected Core ourselves. Impact of omitting it is
  **[unverified]** but node-roon-api treats pairing as optional (only
  registered when `core_paired` is set).
- `ws_connect_with_token` / `register_one_time_token` is marked "DO NOT USE --
  internal only" in node-roon-api; ignore.

### 2.5 Transport service (`com.roonlabs.transport:2`)

- `subscribe_zones` reply `Subscribed` body: `{zones: [Zone...]}`. `Changed`
  body may contain `zones_removed` (array of zone ids), `zones_added` and
  `zones_changed` (arrays of full Zone objects that replace the cached entry),
  and `zones_seek_changed` (array of `{zone_id, seek_position,
  queue_time_remaining}`) which only updates `now_playing.seek_position` and
  `queue_time_remaining` on the cached zone (transport `lib.js`
  `subscribe_zones`). **[source-only]**
- Zone fields used by us: `zone_id`, `display_name`, `state`
  (`playing|paused|loading|stopped`), `outputs`, `now_playing`
  (`seek_position`, `length`, `image_key`, `one_line.line1`, `two_line.line1/line2`,
  `three_line.line1/line2/line3`), `is_play_allowed`, `is_pause_allowed`,
  `is_next_allowed`, `is_previous_allowed`, `is_seek_allowed` (JSDoc in
  transport `lib.js`, `docs/Zone.html`). In `three_line`, line1 = title, line2 =
  artist, line3 = album per Roon convention **[unverified: confirm from a
  recorded message]**.
- Control: `transport:2/control` with `{zone_or_output_id, control}`;
  controls are `play`, `pause`, `playpause`, `stop`, `previous`, `next`.
  Seek: `transport:2/seek` with `{zone_or_output_id, how: "relative"|"absolute",
  seconds}`. Replies `COMPLETE Success` on success. Check `is_*_allowed` before
  enabling a control. **[source-only]**
- Seek update frequency: the node-roon-api sources only show how seek events are
  applied, not how often the Core sends them. The brief cites roon-rs throttling
  its CLI to 1 Hz per zone. Actual rate is **[unverified]**; measure with the
  CLI recorder in Phase 1 (target: timestamps per `zones_seek_changed`
  message). Until measured the design assumes about 1 Hz and interpolates
  locally between events.

### 2.6 Image service (`com.roonlabs.image:1`)

- `image:1/get_image` with `{image_key, [scale: fit|fill|stretch, width, height,
  format: image/jpeg|image/png]}`; `width` and `height` are required when
  `scale` is set. On `Success` the body is binary with the image
  `Content-Type`. Without `scale` the original size is returned and "could be
  very large" (image `lib.js`). **[source-only]**
- The same file documents a plain HTTP form:
  `http://IP:PORT/api/image/<image_key>?scale=XXX&width=XXX&height=XXX&format=XXX`.
  Using that URL with Coil/OkHttp would avoid MOO for covers. Whether it requires
  the registered token or works unauthenticated is **[unverified]**. Phase 1
  decides between the two based on a live test. Always request a scaled size
  (e.g. 1080 px square) to keep memory low on the Fire TV.

### 2.7 Other open questions

| Question | Status |
|---|---|
| Multicast on Fire OS: need for `WifiManager.MulticastLock`, and whether the replies (sent unicast to our source port; multicast-addressed replies were not seen) arrive without it | Unverified. Android generally filters inbound multicast without the lock; our live replies were unicast, so discovery might work without it. Acquire the lock while discovering regardless; test on the device. Manual host:port fallback is a first-class path. |
| Same `extension_id` on two devices (Fire TV and Echo Show): separate entries, shared authorization, pairing effects | Unverified. node-roon-api README only states one extension can connect to multiple Cores. Plan: test with two CLI instances in Phase 1. Fallback: per-install suffix in `extension_id` and `display_name`. |
| Fire OS to API level | Unverified for the user's device. Working assumption: Fire OS 6 = Android 7.1 / API 25, Fire OS 7 = Android 9 / API 28, Fire OS 8 = Android 11 / API 30. Preliminary `minSdk 28` would exclude Fire OS 6 devices. Need the device's Fire OS version. |
| Behaviour when the register request is unauthorized or while waiting for "Enable" | Unverified, see 2.4. |
| Exact name of the "now playing" lines (title/artist/album) | Unverified until the first recorded zone message. |
| `Registered` verb (CONTINUE vs COMPLETE) | Source-only, roon-rs says CONTINUE; observe in Phase 1. Implement handling of both. |
| Reconnect behaviour after Core restart (token still valid, `core_id` stable) | Unverified; covered by the fake core and the real Core test in Phase 1/4. |
| Live broadcast discovery | Subnet broadcast got no reply on this host; multicast works. |

## 3. Environment (Phase 0 results)

Toolchain lives in `docker/android-build/Dockerfile`, run through `scripts/dock`
with `--network host`. Recorded outputs (2026-10-17):

- `java -version`: OpenJDK 17.0.20.1 (Temurin-17.0.20.1+1)
- `adb version`: Android Debug Bridge 1.0.41, Version 37.0.1-15733141
- `sdkmanager --list_installed`: build-tools;36.0.0, platform-tools 37.0.1,
  platforms;android-36 (rev 2)
- `maestro --version`: 2.11.0
- Host: Ubuntu VM, LAN IP 192.168.1.198/24 on `enp6s18`.
- `adb connect` is skipped until the Fire TV IP is known.

## 4. Candidate versions (checked 2026-10-17 on Maven metadata; to be verified by a real build in Phase 1)

| Component | Latest stable seen | Notes |
|---|---|---|
| Android Gradle Plugin | 9.4.1 (9.5.0-alpha10 exists, ignore) | AGP 9 needs JDK 17+ and Gradle 9.x; check AGP 9 built-in Kotlin behaviour |
| Gradle | 9.8.1 | from services.gradle.org/versions/current |
| Kotlin | 2.4.21 (2.5.0-Beta1 exists, ignore) | |
| Compose BOM | 2026.09.00 | |
| `androidx.tv:tv-material` | 1.1.0 (stable) | compatibility with the BOM version to be confirmed |
| Coil 3 (`coil-compose`, `coil-network-okhttp`) | 3.6.3 | use OkHttp network fetcher |
| OkHttp | 5.5.0 | |
| kotlinx-coroutines | 1.11.0 | |
| kotlinx-serialization-json | 1.11.0 (1.12.0-RC exists, ignore) | |
| Robolectric / Roborazzi | 4.17 / 1.76.0 | UI tests, Phase 2+ |
| compileSdk / build-tools | 36 / 36.0.0 | already in the Docker image |

These are Maven metadata readings only, not compatibility proof.

## 5. Planned module layout (Phase 1)

```
roon-client/   pure Kotlin/JVM, no Android dependency
  sood/        SoodCodec (encode/parse), SoodDiscovery (DatagramSocket, multicast + broadcast)
  moo/         MooMessage (parse/serialize), MooConnection (OkHttp WebSocket, request ids, subscriptions)
  RoonClient   registry handshake, token store interface, ping service, backoff reconnect
  transport/   ZoneState (incremental updates, seek merge), TransportService (control, seek)
  image/       ImageService (MOO get_image or HTTP URL builder)
  src/test/    unit tests, FakeCore (SOOD responder + MOO WebSocket server), recorded fixtures
roon-cli/      JVM CLI: discover, connect, zones, now-playing, play/pause/next/previous, --record
app/           Android TV app: Compose for TV, MVVM, DataStore, Coil, MulticastLock
docs/, scripts/, docker/, config/
```

Rules: `roon-client` has no Android imports; the clock and the socket factory are
injected so progress interpolation and backoff are unit-testable. Fixtures are
recorded from the real Core with `roon-cli --record` and must not contain tokens
(redact before commit).

## 6. Risks and next steps

1. Observe the real registration/authorization behaviour (needs the user to enable the extension once).
2. Measure `zones_seek_changed` rate.
3. Decide HTTP vs MOO for images from a live test.
4. Test two instances with the same `extension_id`.
5. Confirm Fire OS version and API level, then fix `minSdk`.
6. Confirm Roon's current extension menu path.

## 7. Phase 1 results (observed on the real Core, Roon 2.73 build 1700)

| Open point | Result |
|---|---|
| Behaviour of `register` before "Enable" | **[verified live]** The Core sends no reply at all (no `Pending`, no error). The only traffic is `com.roonlabs.ping:1/ping` requests from the Core. The connection stays open for at least 10 minutes. The client therefore shows "awaiting authorization" when `register` is unanswered for 2 s. |
| Verb of `Registered` | **[verified live]** `CONTINUE Registered` (about 20 s after the user pressed Enable). Body: `core_id`, `display_name`, `display_version`, `token`, `provided_services` (`transport:2`, `image:1`), `http_port`, `extension_host` (our own address as seen by the Core). `COMPLETE` is also accepted by the client. |
| Core ping | **[verified live]** The Core sends `com.roonlabs.ping:1/ping` (`Logging: quiet`) every 2 s; we answer `COMPLETE Success` with the same Request-Id. |
| Token reuse | **[verified live]** A second run with the stored token is answered with `Registered` immediately, with no new authorization and no new Roon prompt. The Core returns a token in every `Registered`. |
| Roon menu path | **[verified]** Roon for Mac 2.73: "Settings -> Extensions". |
| `three_line` mapping | **[verified live]** `line1` = title, `line2` = artist(s) (joined with " / "), `line3` = album. `two_line` = title / artist, `one_line.line1` = "title - artist". |
| Message right after `Subscribed` | **[verified live]** The Core sends a `Changed` with `zones_added` for all zones immediately after `Subscribed`. The reducer handles it (replaces entries). |
| Zone fields beyond the doc | `queue_items_remaining`, `settings`, `artist_image_keys`, `outputs[].volume`, `outputs[].source_controls` are present and ignored. A stopped zone without queue has no `now_playing` and `is_play_allowed=false`: it cannot be started from an extension (no browse API). |
| Image over HTTP without auth | **[verified live]** `GET http://<core>:9330/api/image/<key>?scale=fit&width=300&height=300` returns 200 `image/jpeg` (61437 bytes) without any token. An unknown key returns 404. |
| Image over MOO | **[verified live]** `image:1/get_image` returns `COMPLETE Success` with `image/jpeg`, same size as via HTTP. For an unknown key the Core sends **no reply** (client timeout). Decision: use the HTTP URL (Coil), with 404 as the clean "missing cover" signal. |
| `zones_seek_changed` rate | **[verified live]** 29 messages in 30 s = 1.00 Hz per playing zone (interval avg 996 ms, min 655 ms, max 1359 ms). Entry shape: `{"zone_id", "queue_time_remaining", "seek_position"}`. Nothing is sent while paused. Interpolating locally between events is only needed for smooth progress bars. |
| Track change sequence | **[verified live]** `next` yields `zones_changed` with `state: "loading"` and a now_playing without `seek_position` (plus a `zones_seek_changed` entry with `seek_position: null`), then `zones_changed` with `state: "playing"`. Pause and resume are single `zones_changed` messages with the full zone object. |
| Two connections with the same `extension_id` | **[observed live]** When a second connection registered with the same `extension_id` (a CLI control call while a `watch` was running), the older connection received an empty (0-byte) frame and was dropped. The older client then reconnected and in turn evicted the other. Two devices must therefore use distinct `extension_id`s: the app appends a per-installation suffix (generated once, stored in DataStore). Each distinct id needs its own "Enable" in Roon. |
| Empty frame | **[observed live]** A 0-byte WebSocket frame precedes the eviction described above. The client ignores empty frames. |
| Multicast on Fire OS | **[unverified]** Needs the device. |
| Fire OS / API level | **[verified]** Fire TV Stick HD (AFTSS "sheldon"), Fire OS 7.7.1.7, SDK 28 (Android 9), armeabi-v7a, 1920x1080, density 320. minSdk 28. This device is a 1080p stick, not a 4K stick. |

Fixtures: `roon-client/src/test/resources/fixtures/` (see its README for recorded vs derived files).

### CLI notes
- Config and token live in `$ROOMSCREEN_CLI_CONFIG`, else `$XDG_CONFIG_HOME/roomscreen-cli`, else `$HOME/.config/roomscreen-cli` (the JVM `user.home` is not usable in the container: uid without passwd entry gives `?`).
- Control commands only act on `$ROON_TEST_ZONE` and only if the zone reports the control as allowed.

### App findings (Fire TV Stick HD)
- SOOD multicast discovery works on Fire OS 7 with `WifiManager.MulticastLock` held during discovery (found the Core in about 4 s, including the full discovery window).
- Cover art: Coil loads the HTTP image URL (`/api/image/<key>?scale=fit&width=..&height=..`) without a token; cleartext HTTP needs `usesCleartextTraffic`.
- Connect order: last known endpoint first (plain TCP probe, 1.5 s), discovery only if it is unreachable, same order on every reconnect attempt. Measured on the device: `endpoint=cached` 20 ms after start versus about 4 s with discovery; no `discovery=` log line.
- A restart with a stored token registers immediately (`token_present=true`) with no authorization step.
- Maestro on the Fire TV can drive the app with `pressKey: Remote Dpad Down/Center` and `Back`; text assertions work against the Compose UI.
- Debug-keystore: `~/.android/debug.keystore` is reused across container runs (mounted), so `adb install -r` keeps working.
- Build note: current AndroidX/Coil/OkHttp AARs require compileSdk 37 (targetSdk 36).
- Keep screen on: `FLAG_KEEP_SCREEN_ON` on the activity window. Verified on the Fire TV with `dumpsys power`: while the app is in front a `SCREEN_BRIGHT_WAKE_LOCK 'WindowManager'` is held for the app uid (WorkSource 10126), still held 6 minutes later with `Display Power: state=ON` (device `screen_off_timeout` = 300000 ms, `sleep_timeout` = 1200000 ms); after `HOME` the lock is gone. Setting for "only while playing" is planned for Phase 2.

## 8. Phase 2 findings

### 8.1 Artist image (step 0, read-only check on the real Core, 2026-10-17)

`now_playing.artist_image_keys` in the zone data already holds one image key per credited artist, in the
order of the `three_line.line2` artist line ("A / B"). No Browse API call is needed. `roon-cli artist-image --zone NAME`
fetches `http://<core>:9330/api/image/<key>?scale=fit&width=1080&height=1080` for every key of every zone that has a track
(no auth, same as covers). Result of the run (13 keys, 6 zones, 5 different artist lines incl. three multi-artist lines):

| Artist line | Keys | HTTP | Time | Bytes |
|---|---|---|---|---|
| Billy Joel | 1 | 200 image/jpeg | 503 ms | 92520 |
| Roxy Music | 1 | 200 image/jpeg | 505 ms | 149988 |
| Nightmares on Wax / Oscar Jerome | 2 | 200 | 488 / 744 ms | 121453 / 323156 |
| Joaquin Rodrigo / Pepe Romero / Neville Marriner / Academy of St Martin in the Fields | 4 | 200 | 435-572 ms | 97715-286535 |
| Melody Gardot / Ken Pendergast | 2 | 200 | 566 / 357 ms | 130141 / 116298 |

Hit rate 5/5, every request below 1 s. Viewed: real artist photos (Billy Joel at the piano, Roxy Music band photo, an orchestra
for the Academy, Melody Gardot, Oscar Jerome), landscape crop (about 2.3:1 at `fit` 1080 wide: 1080x472), not the album cover.
Decision: the "Artist image" background option is offered (first key of the list; fall back to the cover if the list is empty).

Browse API route (`com.roonlabs.browse:1`, `search` hierarchy): implemented in the CLI behind `--browse true` but **not run**.
Requesting browse as an optional service makes the Core ask for authorization again ("Enable" in Settings -> Extensions);
the CLI waited in `AwaitingAuthorization` and was stopped. Not needed given the direct keys.

### 8.2 Keycodes (verified on the Fire TV Stick HD)

`KeyEvent` codes the app received for Maestro `pressKey` (logged as `key code=... action=...`):

| Maestro key | Code | Name | Mapped to |
|---|---|---|---|
| Remote Media Play Pause | 85 | KEYCODE_MEDIA_PLAY_PAUSE | `playpause` |
| Remote Media Rewind | 89 | KEYCODE_MEDIA_REWIND | `previous` |
| Remote Media Fast Forward | 90 | KEYCODE_MEDIA_FAST_FORWARD | `next` |
| Remote Media Next | 87 | KEYCODE_MEDIA_NEXT | `next` |
| Remote Media Previous | 88 | KEYCODE_MEDIA_PREVIOUS | `previous` |

`KEYCODE_MEDIA_PLAY` (126) and `KEYCODE_MEDIA_PAUSE` (127) map to `play` and `pause`. The codes the Maestro/adb injection
produces match the plan; whether the physical remote sends the same codes was not checked (nobody pressed it). The
activity handles media keys in `dispatchKeyEvent` before the focus system: down with repeat 0 sends the command, both
down and up are consumed. Commands are checked against `is_*_allowed` first.
Control bar focus: with `ON_INPUT` the first key only shows the bar and focus lands on play/pause; with `ALWAYS` the first
D-pad key focuses "Zones" (top left).

### 8.3 Backdrop (no live blur)

`RenderEffect` needs API 31, the target is API 28 with 1 GB RAM. `BackdropBlur.compute` runs once per track on a
background dispatcher: centre crop to 16:9, shrink to 32 px, double with bilinear filtering up to 128 px, three 3x3-radius-3
box blur passes (removes the visible 32 px grid), double to 256 x 144. The result (about 150 KB) is drawn with
`ContentScale.Crop` at 75 % alpha over the chosen base colour, with a dark vertical gradient on top. `BackdropCache`
keeps the last three. JVM time for the whole computation is about 3 ms for a 320 px source (`BackdropTest`, not a device number).
The source decode size is 160 px; the 480 px cover is requested separately (the small cover is shown at 200 dp).

Sharp path (setting "Background sharpness" = Sharp): no blur. The artist image is requested as 1280 x 720 `scale=fill`, a cover as
720 x 720; Coil decodes at that size (`allowHardware(false)`), `centerCrop16x9` (the crop shared with `BackdropBlur.compute`) cuts
16:9 and the bitmap is cached under its URL (`MAX_ENTRIES` stays 3; the URL differs between modes, so no mixing). A 1280 x 720 ARGB
bitmap is about 3.7 MB, three entries about 11 MB; the GPU scales it to 1080p. The image is drawn at alpha 1.0 (soft: 0.75) under the
unchanged dark gradient. The Coil memory cache was raised from 8 MB to 16 MB for this. Verified on the Fire TV (2026-10-18, zone Büro, `sharpness=SHARP`, `background=ARTIST`, lyrics off): screenshots
`build/device-artifacts/p4-01-sharp-artist.png` (sharp artist photo under the dark gradient) and `p4-02-sharp-lyrics.png` (lyrics readable on the
sharp background). Memory (`dumpsys meminfo`, TOTAL PSS): 128 MB before, 166 MB after 10 track changes (one per 12 s), 154 MB 30 s later;
GL mtrack 58.6 MB, 77.1 MB, 75.1 MB. This is higher than the soft-mode figures in 8.5 (lyrics off 115 to 127 MB before, 136 to 138 MB after
10 changes); the growth is mostly GPU memory and it falls again after the track changes stop. No out-of-memory or jank was observed; frame
timing was not measured.

Colour background (`BackgroundSource.COLOR`): `backdropUrl` is null (no image loads), no gradient is drawn, the area is the chosen
`backgroundColor`; info strip, small cover and lyrics column are unchanged. The sharpness row is hidden in the settings then.

### 8.4 Progress

`ProgressInterpolator` (roon-client, injected monotonic clock) anchors at each seek value; while `playing` it adds the elapsed
clock time, capped at `length`. A changed seek value (1 Hz drift correction, seek), state or track re-anchors. A pause without a
new seek value (the optimistic UI flip) freezes at the interpolated position. The UI reads it at 10 Hz; the bar and the elapsed text
read the state in the draw/derived scope so the rest of the screen is not recomposed.

### 8.5 Device measurements (Fire TV Stick HD, debug build, zone Büro)

- Jank: `dumpsys gfxinfo` reset after 10 warm-up track changes, 60 s of playback: 1269 frames, 6 janky (0.47 %), 90th percentile 8 ms,
  99th 16 ms. A first run right after installing the APK (JIT not warm) had 143 of 1255 janky (11.4 %); another run with a second
  script accidentally running at the same time is not valid and not used.
- Memory (`dumpsys meminfo`, TOTAL PSS), 20 track changes through the media key, 4 s apart:
  - Coil default memory cache: 108.2 MB before, 180.5 MB right after (+72), 162.9 MB 30 s later (+55).
  - Cache capped at 8 MB (`RoomscreenApp`): run A 123.4 -> 126.3 -> 124.2 MB; run B 114.0 -> 134.0 -> 126.8 MB (+20 peak, +12.8 settled).
  The graphics part (GPU texture caches) varies by about 10 MB between runs; the plan's +-10 MB is met in one run and exceeded by 3 MB (settled) in the other. No growth trend.

## 9. Phase 3: synchronized lyrics (LRCLIB)

### 9.1 Real-API check with the CLI (2026-10-17, read-only)

`roon-cli lyrics` takes title (`three_line.line1`), artist (`line2`), album (`line3`) and `now_playing.length` of each zone's
current track (all zones were paused) and runs the same `LyricsRepository` the app uses, without a cache and without sending any
control. User-Agent `Roomscreen/cli (https://github.com/knoellp/Roomscreen)`. `ms` includes the retries (1 s and 3 s pauses).

| Zone | Title | Variant that hit | Result | ms |
|---|---|---|---|---|
| Büro | Fortress Around Your Heart (Sting, 280 s) | exact | synced, 41 lines | 73 |
| MacBook Pro | If the Stars Were Mine (Melody Gardot / Ken Pendergast, 168 s) | artist (first artist only) | synced, 23 lines | 10071 |
| Kamin | The Main Thing (Roxy Music, 234 s) | exact | plain, 12 lines | 119 |
| Fernseher | The Main Thing (Roxy Music, 234 s) | exact | plain, 12 lines | 105 |
| Schlafzimmer | Echo45, We Are! (Nightmares on Wax / Oscar Jerome, 84 s) | none of exact/artist/search | none (run 3), unavailable (runs 1, 2) | 12618 / 5376 / 5135 |
| Bad | Concierto de Aranjuez ... (Rodrigo / Romero / Marriner / Academy, 373 s) | - | unavailable (3 of 3 runs) | 5103-8248 |

Hit rate 4 of 6 zone rows with text (3 of 5 distinct tracks); the remaining two tracks got `unavailable` or `none`.
`unavailable` means LRCLIB answered `503 {"name":"ServerOverloaded"}` on every attempt (checked with curl at the same time: the
`get` and `search` calls for the Bad and Schlafzimmer tracks returned 503 repeatedly, other tracks 200/404 within 0.1-0.5 s).
It is not cached on disk, so the next track change asks again. For the Schlafzimmer track one run got through with a clean
`none`: LRCLIB has no entry for it.

### 9.2 LRCLIB behaviour (verified against the live API on 2026-10-17)

- `GET /api/get?track_name&artist_name&album_name&duration` (album left out when empty, duration in whole seconds, left out when 0):
  200 with `syncedLyrics` (LRC text), `plainLyrics`, `instrumental`; 404 `{"name":"TrackNotFound"}` when there is no entry.
- `503 {"name":"ServerOverloaded"}` is frequent and not tied to the track: curl of the same `get` returned 503, 404, 404 in a row for one title, and `get` and
  `search` for the Bad zone track returned 503 every time. It is retried (pauses of 1 s and 3 s, so 3 attempts) and, if it persists, reported as
  `Unavailable`. It is never cached on disk (10 minutes in memory only) and is never shown as "No lyrics found".
  Timeouts are 5 s (connect, read, write) and 10 s per call; I/O errors and 429 are handled like 5xx.
- Titles from Roon carry additions ("Captain Jack (Album Version)" is 404, "Captain Jack" is a hit). `QueryNormalizer` builds variants that
  accumulate: (1) exact; (2) title without trailing "(...)", "[...]" or "- ..." groups that name a version/remaster/edit/mix/mono/stereo/deluxe/bonus/single
  ("(Live)" stays); (3) first artist of Roon's "A / B / C"; (4) without album. Equal variants are dropped. Each variant is tried with `get`;
  if all are 404, `/api/search?track_name&artist_name` is asked with the last variant and the hits are filtered here to
  |duration - wanted| <= 2 s (the endpoint returns the same song in 348/411/418/435 s versions); synced beats plain beats instrumental.
- A variant that ends `Unavailable` ends the lookup at once (no point in asking the same overloaded server again for the next variant).
- Lookup log line (app and CLI): `lyrics fetch=net|cache|memory variant=exact|title|artist|no-album|search|- result=synced|plain|instrumental|none|unavailable|cancelled lines=N ms=N`.

### 9.3 Cache

`FileLyricsCache`: one JSON file per track key (`title|artist|album|duration`) under `cacheDir/lyrics`, file name = SHA-1 of the key, at most 300 files
(the oldest by modification time are deleted), written through a temp file. Found: kept without limit. Not found: 7 days. Unavailable: only in
memory, 10 minutes. A corrupt file is deleted and treated as a miss. Verified on the device: after a restart the same track logged
`lyrics fetch=cache variant=exact result=synced lines=77 ms=712`.

### 9.4 Synchronization and rendering

- `LyricsSync.activeIndex(lines, positionMs)` is the last line with `timeMs <= position + 250 ms` (binary search, -1 before the first line).
- The position is the existing `ProgressInterpolator` read (10 Hz) in `NowPlayingScreen`. The lyrics list reads it inside a `derivedStateOf`, so the list
  recomposes and scrolls (`animateScrollToItem`) only when the index changes, not on every tick. The active line (34 sp bold, white) is kept in the vertical
  middle through a content padding of half the viewport height; the neighbours are 28 sp and fade with their distance from the active line. The upper edge
  fades through a dark scrim; the lower edge only through the per-line alpha. A first version used an offscreen layer with a gradient mask for both edges;
  it was replaced because it needs a full-size offscreen buffer on a 1 GB device. The memory runs below were taken with both versions and do not differ
  measurably given the run-to-run spread (the table runs 1-3 are all the scrim version).
- `[offset:+-ms]` is applied as `time - offset` (positive offset: lyrics appear earlier); stamps are clamped at 0.
- Plain text is one scrollable column; D-pad up/down scroll it (`LyricsViewTest`).
- The Lyrics button is the only switch; no request is made while it is off or while the "Lyrics (LRCLIB)" setting is off. A track change cancels the running
  lookup (`collectLatest`; the OkHttp call is cancelled with the coroutine).

### 9.5 Device measurements (Fire TV Stick HD, debug build, zone Büro; `scripts/phase3-device.sh`, `scripts/phase3-mem.sh`, `scripts/phase3-gl.sh`)

- Lookup, cancellation, no request while off (log excerpts of the final run, German UI):
  - `lyrics button=on`, then `lyrics fetch=net variant=exact result=synced lines=60 ms=953`.
  - Cancellation on a track change during a running lookup: `lyrics fetch=net variant=artist result=cancelled lines=0 ms=5945` (also `... variant=artist result=cancelled lines=0 ms=2789`, `... variant=exact result=cancelled lines=0 ms=5941` in other runs). The quick-change test sends six track changes 1.2 s apart; whether one lookup is still running when the next track arrives depends on LRCLIB's answer time, so not every run shows a cancellation.
  - With "Lyrics (LRCLIB)" off and the Lyrics button on (`"lyrics_on":true,"lyrics":"off"`, `lrclib=false` from DUMP_STATE), two track changes produced no `lyrics` log line at all. Switching the setting back on logged `lyrics fetch=net variant=artist result=synced lines=31 ms=3728` for the current track.
  - With lyrics off (button), three track changes: no `lyrics` log line.
  - After a force-stop and start: `"lyrics_on":true,"lyrics":"synced:77"`, the lyrics view is shown again (`p3-08-after-restart.png`).
  - Answer times seen on the device: 250-1100 ms typically; 2-6 s when `get` was answered with 503 and retried; `result=unavailable ms=5079..5266` when it stayed 503 (not cached, the cover view stays).
- Active line follows playback: `p3-03c-lyrics-a.png` and `p3-03d-lyrics-b-10s-later.png` (10 s apart) show different active lines and a progress bar that moved by about 12 px.
- Memory (`dumpsys meminfo`, TOTAL PSS in MB; Phase 2 reference: 114 -> 134 -> 127 and 123 -> 126 -> 124). Protocol of the step-2/6 pairs: 10 track changes, 4 s apart; step 2 runs in the warm process with lyrics off, step 6 right after a force-stop and start with lyrics on (cold process, so it also contains the normal fill-up of the image caches):

  | Run | Lyrics off, warm: before -> after 10 -> 30 s later | Lyrics on, cold: before -> after 10 -> 30 s later |
  |---|---|---|
  | 1 | 115.9 -> 137.6 -> 138.5 | 89.9 -> 175.5 -> 172.1 |
  | 2 | 126.5 -> 135.8 -> 130.8 | 107.1 -> 154.9 -> 144.1 |
  | 3 | 114.2 -> 135.8 -> 132.4 | 96.2 -> 177.5 -> 150.4 |

  Fresh process (force-stop, start, switch, play, then 10 track changes, `phase3-mem.sh`): lyrics off 80.1 -> 126.3 -> 124.0 (mask version) and 90.6 -> 134.2 -> 127.1 (scrim version);
  lyrics on 81.6 -> 123.9 -> 122.8 (mask version), 82.1 -> 163.8 -> 157.3 (scrim version) and 141.9 -> 193.9 (mask version, process had run before). With 10 warm-up changes first (mask version) the lyrics-on baseline was 198 MB and then fell (187, 178).
  The growth is the GPU side: `GL mtrack` (the Skia/HWUI resource cache) rose from 40 MB to 94 MB over 12 track changes with lyrics on, then stayed flat, and was at 73 MB 30 s after lyrics were switched off and at 61 MB 30 s after they were switched on again (the cache is trimmed while idle); on a single track (no track change) it stays at 39-41 MB for 90 s with lyrics on and off alike (`phase3-gl.sh`). Java heap stays at 8-13 MB and native heap at 7-24 MB in all runs. So there is no growth trend, but with lyrics on the settled total is 10-50 MB above lyrics off (cold-start fill-up included), which does **not** meet the plan's +-10 MB tolerance against Phase 2. The cause of the larger GPU cache per track in the lyrics view was not isolated (text drawing versus image cache; the cache is purgeable and bounded).
- Jank was not measured for the lyrics view (no `gfxinfo` run).
- **Memory criterion for version 1 (decision of 2026-10-18, fixed ceiling instead of a tolerance against Phase 2): TOTAL PSS after 30 track changes with lyrics and a sharp artist image stays under 200 MB, and there is no trend over the soak run. Measurement pending.** Plan: `scripts/phase3-mem.sh on 30 10` once (10 warm-up changes, then 30), table start / end / 30 s later, plus the 13 soak samples of section 10.5 as the trend evidence. No code change unless the measurement misses 200 MB; then `onTrimMemory` clears the Coil cache and the measurement is repeated. Status: not measured yet.

### 9.6 Tooling findings

- Maestro on the stick reads the UI hierarchy slowly while the screen animates and the control bar hides 5 s after the last key, so the flows navigate the bar while Büro is paused, keep slow steps out of key sequences, and `scripts/phase3-device.sh` repeats a flow until DUMP_STATE shows its effect (`lyrics_on:true`, `lrclib=false/true`). Key sequences from the focused play button: Left x2 = Lyrics, Left x3 = Settings (with the Lyrics button), Left x2 = Settings (without it).
- Debug broadcasts added: `SET_LYRICS` and `SET_LRCLIB` (value on|off); a manifest receiver needs the action in `app/src/debug/AndroidManifest.xml`, otherwise the broadcast is silently dropped.
- The lyrics module only depends on `roon-client` (Logger, OkHttp, coroutines, serialization); the app passes its `versionName` for the User-Agent, the CLI passes `cli`.

## 10. Phase 4: stability and polish

### 10.1 Connection handling (roon-client)

- **Silence watchdog** (`RoonClient.watchSilence`): `MooConnection` records the arrival time of every frame (also empty ones). The Core pings every
  2 s, so 30 s without any frame means the link is dead (a half-open TCP connection that OkHttp's own 10 s WebSocket ping may not notice behind
  some Wi-Fi/NAT states). The socket is then cancelled (`conn watchdog=silence seconds=30`), which runs the normal reconnect path. The limit is
  a constructor parameter (`silenceTimeoutMs`); the FakeCore test uses 800 ms.
- **Reconnect**: backoff 1 s, 2 s, 4 s, ... capped at 30 s with up to 20 % jitter; every reconnect registers again with the stored token and
  sends `subscribe_zones` again; the zone map is cleared when a session ends and replaced by the new `Subscribed` list.
- **Rediscovery**: with a cached endpoint (the app's default) `resolveEndpoint` probes it with a TCP connect and falls back to SOOD discovery on
  every attempt (`endpoint=rediscover reason=unreachable`). With a fixed (manual) endpoint the same discovery runs after `rediscoverAfter` = 5 failed
  attempts in a row (`endpoint=rediscover reason=unreachable attempts=5`), and the discovered address is saved by the app once connected.
- **Timeouts are failed attempts**: `withTimeout` throws `TimeoutCancellationException`, a `CancellationException`. The reconnect loop used to
  rethrow every `CancellationException`, so a connect timeout (an address that accepts nothing, found on the device with `SET_HOST 192.0.2.1`)
  ended the loop silently and the app stayed on "Connecting" forever. The loop now treats a timeout as a failed attempt; `Registry.register`
  throws a `RegistryException` instead of timing out. FakeCore test with a socket that accepts and never answers.
- **Lost authorization**: a stale token gets no answer from the Core (verified in Phase 1: `register` stays unanswered before "Enable"), so the state
  becomes `AwaitingAuthorization` after 2 s and stays there, without reconnecting; enabling the extension again completes the registration. FakeCore test.
- **Lifecycle** (`MainViewModel.disconnectDelayed`): `onStop` closes the connection after 2 s, `onStart` within that time cancels it
  (`lifecycle=disconnect_cancelled`). `connect()` stays idempotent. FakeCore test: stop/start/stop/start leaves exactly one open socket
  (`FakeCore.openSockets`, `maxOpenSockets`).

### 10.2 Device runs (Fire TV Stick HD, debug build, 2026-10-18, zone Büro; `scripts/phase4-resilience.sh`)

Times are device clock; "recovery" is from the dropped connection to `zone resolved ... via=id`.

| Scenario | Log (condensed) | Recovery |
|---|---|---|
| Core software restart, Büro playing | `09:19:52.865 conn=websocket_failure error=EOFException`, `conn state=Reconnecting(attempt=1)`, `09:19:53.842 endpoint=cached_unreachable`, `endpoint=rediscover reason=unreachable`, `09:19:55.919 discovery=found`, `09:19:58.086 conn=connected`, `zones_msg subscribed count=0` (Roon still starting), `09:20:32.149 zone resolved ... via=none`, `09:20:33.907 zone resolved ... via=id` | 41 s (45 s from the POST, polled) |
| Core software restart, Büro paused | `09:21:46.421 conn=websocket_failure error=EOFException`, `Reconnecting(attempt=1)`, rediscover, `09:21:51.484 conn=connected`, `09:22:26.934 zone resolved ... via=none`, `09:22:27.483 zone resolved ... via=id` | 41 s (44 s polled) |
| Wi-Fi off on the stick for 20 s (`svc wifi disable; sleep 20; svc wifi enable` on the device) | `09:25:26.646 conn=websocket_failure error=SocketException: Software caused connection abort`, `Reconnecting(attempt=1..5)` with `discovery=no_interface`, backoff 0.9, 1.8, 3.3, 7.6, 14.8 s, `09:25:55.380 endpoint=cached`, `09:25:55.620 conn=connected`, `09:25:55.829 zones_msg subscribed count=6` | 29 s after the cut started (about 9 s after Wi-Fi returned; the last backoff of 14.8 s dominates) |
| Manual address 192.0.2.1 (nothing answers), app started | `conn=session_failed error=timeout` (10 s connect timeout) x5, `09:34:10.706 endpoint=rediscover reason=unreachable attempts=5`, `09:34:16.313 discovery result=found`, `09:34:16.561 conn=connected`, `09:34:16.728 zone resolved ... via=id` | 84 s from the start (5 x 10 s timeouts plus backoff) |
| Home key, 3 s, `am start` | `lifecycle=disconnect_after_delay ms=2000`, `conn state=Idle`, then once `conn=websocket_open`, once `registry=register_sent`, `conn state=Connected` | about 0.3 s from start to Connected |
| Home key and immediate `am start` | `lifecycle=disconnect_cancelled`; no `conn state=Idle`, no `websocket_open` | no reconnect |

The restart ran the first time with a build that still had the connect-timeout bug; the restart and Wi-Fi runs above were not affected by it
(the Core answered or refused quickly), the manual-address run is the one that exposed it and was repeated with the fix.
Screenshots (all show Now Playing for Büro again): `build/device-artifacts/p4-1playing-restart-restart-playing.png`,
`p4-1paused-restart-restart-paused.png`, `p4-12-after-wifi.png`, `p4-20-after-home-restart.png`, `p4-13-dead-host.png` (hint after 20 s, German UI). `docs/*.png` and `build/` are not committed.

### 10.3 Memory and jank (sharp artist background, lyrics on)

`scripts/phase4-perf.sh`: fresh process, 10 track changes 5 s apart (Büro only), `dumpsys meminfo`; total PSS and `GL mtrack` in MB.

| Variant | start | after 10 changes | 30 s later | Notes |
|---|---|---|---|---|
| Software sharp bitmaps (before offscreen layer) | PSS 127.2, GL 57.4 | PSS 205.4, GL 116.6 | PSS 196.8, GL 109.6 | |
| Software, second run | PSS 109.8, GL 47.1 | PSS 215.5, GL 115.9 | PSS 200.2, GL 114.9 | |
| Software, final build (offscreen background layer) | PSS 112.0, GL 53.7 | PSS 212.8, GL 122.2 | PSS 197.2, GL 118.3 | offscreen layer costs roughly one 1080p texture |
| Hardware bitmaps for the sharp path | PSS 102.0, GL 42.8 | PSS 212.2, GL 120.7 (+ EGL 14.4) | PSS 179.4, GL 91.9 (+ EGL 10.8) | 10 of 10 images took the hardware path |

**Decision: hardware bitmaps are not kept.** The rule was to keep them only if `GL mtrack` after 10 track changes is lower than with software
bitmaps: 120.7 MB against 116.6 and 115.9 MB, and a separate EGL share appears. The 30 s later figure was lower (91.9 against 109.6 to 114.9) but the
spread between runs of the same variant is about 10 MB, and the one lyrics jank run with hardware bitmaps was 14.96 % janky. A hardware bitmap
can also not be cropped, so it would only work for images that are exactly 16:9 (artist images). The experiment (debug switch, log line
`backdrop sharp path=hardware`) was removed again; the software path logs `backdrop sharp path=software size=1280x720 owned=...`.
`BackdropCache` recycles the bitmaps it created itself (blur result, crop copy) when they are evicted; bitmaps shared with Coil's memory cache
(no crop needed) are only dropped. No `recycled bitmap` crash was seen in any run with the final code.

Jank (`dumpsys gfxinfo` over 60 s, `scripts/phase4-jank.sh` and `phase4-perf.sh`; the number of rendered frames is about 1270 to 1400 per run):

| Run | Janky frames | Notes |
|---|---|---|
| Lyrics on, sharp, per-line size change (before) | 1.89 % (24 of 1273), 8.02 %, 11.89 %, 15.17 % | the lower figure is the first measurement, later runs follow track changes and cold JIT |
| Lyrics off, sharp, same code | 8.62 %, 2.12 % | the plain view is janky too, so the cost is not specific to the lyrics text |
| Lyrics on / off, sharp, `graphicsLayer` lines | 11.04 % / 10.09 %, 9.57 % / 5.91 % | measured right after an install (JIT cold) |
| After the offscreen background layer, sharp, no track change in the window | lyrics on 0.81 %, 0.00 %; lyrics off 0.40 %, 0.16 % | 99th percentile 9 to 15 ms |
| Same build, soft background | lyrics on 3.48 %, 3.83 %; lyrics off 3.16 %, 0.08 % | |

Honest reading: the target (< 5 %) is met in the final build once the app is warm (all eight warm runs below 4 %), but runs right after an install
or across a track change reach 8 to 15 % in every variant, including the plain view, so the effect of the two changes (lines drawn with
a draw-phase `graphicsLayer` scale/alpha instead of a size change per line, which avoids text re-layout and recomposition of neighbouring lines;
the static backdrop and gradient rendered once into an offscreen layer instead of three full-screen passes per 10 Hz progress redraw) is not
cleanly separated from JIT warm-up. Both were kept because they remove work per frame and the final numbers meet the target.
The active line is the 30 sp text scaled by 1.13 (about the former 34 sp bold); neighbours use alpha only.

Lyrics on sharp backgrounds: a horizontal gradient (transparent over the cover column, 55 % black under the text column) is drawn when the sharpness
is SHARP and the background is not a plain colour: Roborazzi `9e-lyrics-sharp.png` with a bright backdrop, and device screenshots
`p4-30-lyrics-sharp-sw.png`, `p4-41-offset0-3.png`.

### 10.4 Other Phase 4 items

- **Lyrics timing** (`AppSettings.lyricsOffsetMs`, -3000 to 3000 in 500 ms steps): `LyricsSync.activeIndex(lines, position, offsetMs)`; positive shows the
  line earlier. On the device with offset +3000 the active line moved at once: `lyrics active=41 offset_ms=0` then `lyrics active=42 offset_ms=3000`
  (68 ms after the broadcast), back to 0 moved it down again. Screenshots `p4-41-offset0-N.png` / `p4-41-offset3-N.png` (the sample lyric repeats a line, so the
  difference is visible in the neighbouring lines and the scroll position).
- **Screen on, only while playing**: `dumpsys power` on the stick: playing with "Always": `SCREEN_BRIGHT_WAKE_LOCK 'WindowManager' ... ws=WorkSource{10126}`;
  setting "Only while playing" and Büro paused: no such lock; Büro playing: lock present; paused again: gone; setting back to "Always": lock present.
- **Control bar**: `AnimatedVisibility` with a 200 ms fade (Roborazzi end states unchanged apart from the offscreen background rendering).
- **Connecting screen**: after 20 s a hint "Core not reachable, still searching..." and the last known address (screenshot `1b-connecting-slow.png`, device `p4-13-dead-host.png`).

### 10.5 Soak (2 hours)

`scripts/phase4-soak.sh`, 2026-10-18 09:39 to 11:42 device time: Büro playing, lyrics on, artist image, sharp. After 10 warm-up track changes
(so the cold-start cache fill-up is not counted) a sample every 10 minutes: TOTAL PSS, GL mtrack and Java heap from `dumpsys meminfo`, a `DUMP_STATE`
summary and a screenshot (`build/device-artifacts/soak-NN.png`); a `KEYCODE_MEDIA_NEXT` on Büro every 20 minutes. The app log was harvested into
`build/device-artifacts/soak-logcat.txt` (6323 app lines, covering the whole run) and scanned at the end. Values in MB.

| Sample | t (min) | PSS | GL mtrack | Java heap | Lyrics |
|---|---|---|---|---|---|
| 00 | 0 | 194.2 | 99.2 | 8.0 | synced |
| 01 | 10 | 149.7 | 66.2 | 10.8 | synced |
| 02 | 20 | 154.1 | 74.9 | 11.0 | synced |
| 03 | 30 | 136.8 | 68.0 | 6.9 | synced |
| 04 | 40 | 164.6 | 86.5 | 9.1 | plain |
| 05 | 50 | 117.3 | 41.5 | 7.7 | synced |
| 06 | 60 | 161.0 | 75.9 | 11.9 | synced |
| 07 | 70 | 103.7 | 38.1 | 9.7 | synced |
| 08 | 80 | 141.2 | 67.2 | 10.4 | synced |
| 09 | 91 | 110.4 | 45.0 | 10.6 | synced |
| 10 | 101 | 151.1 | 68.9 | 12.2 | synced |
| 11 | 111 | 145.3 | 70.0 | 10.5 | synced |
| 12 | 121 | 157.2 | 84.5 | 8.1 | synced |

Result: all 13 samples `connection=Connected`, zone Büro, state playing; the app process kept its pid (31978) for the whole run. The log scan found
0 `Reconnecting`, 0 `watchdog`, 0 `conn=websocket_failure`, 0 `session_failed`, 0 `FATAL`, 0 ANR; the `failed`/`Exception` hits belong to
system processes (`storaged`, `ion`, Amazon services), none to the app. Last PSS 157.2 MB against first 194.2 MB (criterion: at most first + 15 MB),
GL mtrack between 38 and 99 MB with no trend: the GPU share follows the current track (image size, lyrics length) and is released again. Java heap
7 to 12 MB. LRCLIB over the run: 24 synced, 4 plain, 3 unavailable (not cached), 2 cache hits. The script paused Büro at the end
(`control pause ok`, final state `paused`). Screenshots `soak-00.png` to `soak-12.png` all show Now Playing with lyrics; no screensaver.

## 11. Phase F3: release signing, remaining proofs, hand-over

State of this phase: the code and document part and the device runs are done (2026-10-18/19). Open: nothing for the Fire TV; the one-time key change was done before these runs (release key installed, token reused: `token_present=true`).

### 11.1 Release signing (done; installed on the stick)

- One RSA 4096 key (`config/release.jks`, alias `roomscreen`, valid 100 years, PKCS12, gitignored together with `config/signing.env`) signs the debug **and** the
  release build (`buildTypes.debug.signingConfig = release` when `RELEASE_KEYSTORE` is set). Reason: the debug hooks used for device tests and the release APK used for the
  acceptance must replace each other with `adb install -r`, so the stick needs the one-time uninstall only once. Without `config/signing.env` the debug build still builds
  with the Android debug key and the release build stays unsigned.
- Both APKs carry the same certificate; SHA-256 `76bd1d41f70843b362806bbb81cc1a7dce77c51d87ccbd27059edafb18b3c6fb` (`apksigner verify --print-certs`, also in
  `config/release-fingerprint.txt`). Version 1.0.0, versionCode 2.
- PKCS12 keystores have one password for store and key, so `RELEASE_KEYSTORE_PASSWORD` and `RELEASE_KEY_PASSWORD` hold the same value.
- No R8 in version 1 (`isMinifyEnabled = false`): the APK is sideloaded, so its size does not matter, a shrinker would need keep rules for kotlinx.serialization and Coil that
  were never tested, and every measurement in this document (jank, memory, soak) was taken on an unminified build; the release build must behave the same. Revisit when the APK goes to a store.
- `scripts/device-steps.sh` (and so `scripts/deploy-test.sh`) checks the signature before every install: the built APK and the installed package (pulled with `adb pull`, read with
  `apksigner`) must carry the digest from `config/release-fingerprint.txt`; otherwise it aborts. `--allow-key-change` turns the abort into a warning; the script never uninstalls.
  `dumpsys package` on Fire OS 7 prints only a hash of the signature, not the SHA-256 digest, so the installed APK is compared instead.
- **One-time key change on the stick: done before the runs of this section** (the app on the stick carries the release key, `signature check ok` in `device-steps.sh`). It needed the user's approval, then `adb uninstall`, install of the release APK, a new "Enable" in Roon
  (Settings, Extensions) and the zone chosen again. `docs/RELEASING.md` has the key handling and the commands for the GitHub secrets (set on 2026-10-18).

### 11.2 Loading state (done; device photo taken)

`ConnectingContent` shows a 48 dp `CircularProgressIndicator` under the status line from the first frame (the 20 s hint stays). Roborazzi `1-connecting` and
`1b-connecting-slow` were re-recorded and looked at; no other screenshot changed. 
Device photo (2026-10-18, 22:45 device time, German UI): `p5-01-connecting.png` (taken while the app was held in the connecting state with `SET_HOST 192.0.2.1`, because the real connect
is faster than a screenshot) shows the title "Roomscreen", the line "Verbinde...", a circular progress indicator under it and the button "Adresse manuell eingeben". A first
photo right after `am start` was black: the activity had not drawn yet.

### 11.3 Manual host/port entry (done on the device, 2026-10-18)

Debug hook `CLEAR_ENDPOINT` removes only host, port and the manual flag (`Prefs.clearEndpoint`, covered by `SettingsPrefsTest`); token and zone stay.
`scripts/f3-manual-entry.sh` and `maestro/f3-manual-entry.yaml`: read the Core host from `DUMP_STATE`, `SET_HOST 192.0.2.1`, Connecting screen, press
"Enter address manually", type the host, "Test connection", expect `endpoint=<Core>:9330` and Connected; then `CLEAR_ENDPOINT`, restart, expect
`discovery result=found` and the endpoint saved again. A successful test closes the manual screen at once, so the flow waits for Now Playing of the test zone instead of the
result line.

First run findings (both fixed in the flow, no app change): (1) the buttons do not react to touch (`adb input tap` and Maestro `tapOn` on a button do nothing), the app is driven
by the remote, so the flow presses `Remote Dpad Center` (the first press only focuses the button, the second opens the screen; the second is skipped when the entry screen is already open);
(2) typing into the host field opens the Fire OS system keyboard over the form and the following Down/Center presses went into the keyboard (host became `192.168.1.194n`), so the
flow closes it with `hideKeyboard` and then moves Down, Down to "Test connection" and presses Center. Also a stale APK (built before the step) lacked the spinner and the hook; it was replaced with
`scripts/device-steps.sh` (`install -r`, signature check ok, token kept: `registry=register_sent ... token_present=true`).

Green run (`scripts/f3-manual-entry.sh`, final): preflight Connected with `endpoint=192.168.1.194:9330` (from `DUMP_STATE`); after `SET_HOST 192.0.2.1` the screen is `Connecting`; the flow
typed the host and tested; then `"screen":"NowPlaying","connection":"Connected","zones":5,"endpoint":"192.168.1.194:9330"` and `RESULT endpoint=192.168.1.194:9330 saved: ok`,
`RESULT Connected: ok`. After `CLEAR_ENDPOINT` and a restart:

```
22:48:11.460 debug endpoint=cleared
22:48:22.855 conn state=Discovering
22:48:26.952 discovery result=found host=192.168.1.194 port=9330
22:48:27.552 conn state=Connected
```

`RESULT discovery result=found: ok`, `RESULT endpoint saved again: ok`, `RESULT token and zone kept: ok (zone Büro, no new Enable)`. Screenshots: `p5-02-manual-entry.png` (form "Manuell verbinden"
with host 192.168.1.194 and port 9330, buttons "Verbindung testen" and "Zurück"), `p5-03-manual-connected.png` (Now Playing for Büro with lyrics, cover and "via roon"), `p5-04-after-clear-endpoint.png`.

### 11.4 Two instances (done, 2026-10-18); physical remote (done, 2026-10-18/19)

Two Roon clients at once: the app on the stick (zone Büro, app pid 25207, unchanged to the end) and `roon-cli watch --zone "$ROON_TEST_ZONE" --duration 1800` (own `extension_id`).
`logcat -c` right before the start; the CLI ran from 22:53:58 to 23:23:58 device time (30 min, exit code 0, no abort, no error line; `build/device-artifacts/f3-watch.log`).

```
20:53:58.313 conn=connected host=192.168.1.194 port=9330          (CLI, UTC)
20:53:58.356 watch zone=Büro (Ctrl-C to stop)
seek_rate: 0 event(s) in 1800.0s                                  (CLI summary, Büro paused during the run)
```

| Check | Result |
|---|---|
| App log (`logcat -d -s Roomscreen:V`, saved as `f3-two-instances-logcat.txt`) lines matching `websocket_failure`, `Reconnecting`, `conn state=` in the 30 min | 0 |
| App log content in the window | 2 x `zone count=6` (23:13:25, 23:18:24) and one `zones_msg removed=[] added=[...:MacBook Pr...]` (another Roon client appeared; Büro untouched) |
| `DUMP_STATE` at the end (23:24:08) | `"screen":"NowPlaying","connection":"Connected","zones":6,"endpoint":"192.168.1.194:9330","zone":"Büro"` |
| CLI exit | 0, no abort |

Physical remote (the user at the TV, 2026-10-18 23:59 to 2026-10-19 00:04 device time, zone Büro). The Fire TV remote sends `KEYCODE_MEDIA_FAST_FORWARD` (90) and
`KEYCODE_MEDIA_REWIND` (89), not `MEDIA_NEXT`/`MEDIA_PREVIOUS`; `KeyMapping` maps both pairs to next/previous (`KeyMapping.kt` lines 15 and 16). The user pressed play/pause, fast forward and rewind
on the physical remote; app log read at 23:59:49 (`logcat -d -s Roomscreen:V`, verbatim, key-up lines omitted):

```
10-18 23:59:30.663 22038 22038 I Roomscreen: key code=85 name=KEYCODE_MEDIA_PLAY_PAUSE action=0 repeat=0
10-18 23:59:30.670 22038 22038 I Roomscreen: control send=playpause zone="Büro" state=paused optimistic=playing
10-18 23:59:30.762 22038 22038 I Roomscreen: control ok=playpause
10-18 23:59:36.544 22038 22038 I Roomscreen: key code=90 name=KEYCODE_MEDIA_FAST_FORWARD action=0 repeat=0
10-18 23:59:36.544 22038 22038 I Roomscreen: control send=next zone="Büro" state=playing
10-18 23:59:36.916 22038 22038 I Roomscreen: control ok=next
10-18 23:59:44.855 22038 22038 I Roomscreen: key code=89 name=KEYCODE_MEDIA_REWIND action=0 repeat=0
10-18 23:59:44.855 22038 22038 I Roomscreen: control send=previous zone="Büro" state=playing
10-18 23:59:45.209 22038 22038 I Roomscreen: control ok=previous
10-18 23:59:51.105 22038 22038 I Roomscreen: control send=playpause zone="Büro" state=playing optimistic=paused
10-18 23:59:51.585 22038 22038 I Roomscreen: control ok=playpause
```

The user reported that the display reacted at once. Later presses of the same session (D-pad Left/Right/Up/Center/Back, 00:01 to 00:03) are in `build/device-artifacts/p5-remote-user-logcat.txt`
(for example `key code=19 name=KEYCODE_DPAD_UP action=0` and `control send=next zone="Büro" state=paused` at 00:02:13): the D-pad ring navigates the control bar and Center triggers the focused button,
only the media keys act directly. The user asked for direct D-pad control (Left/Right = previous/next, Center = play/pause, Up/Down = show the bar); that is a planned change, not part of version 1 as measured here.
The injected key codes 85, 87, 88 are covered by the acceptance run (11.6).
The same user presses disturbed one acceptance-time Maestro run (phase2) because they changed Büro's state under the flow; that flow was re-run after the session.

### 11.5 Memory criterion (measured 2026-10-18)

Version 1: TOTAL PSS after 30 track changes with lyrics and a sharp artist image under 200 MB, no trend over the soak (decision 14.1 a, a fixed ceiling instead of a tolerance
against Phase 2). `scripts/phase3-mem.sh on 30 10` once plus the 13 soak samples of 10.5. Run 2026-10-18 20:49 to 20:53 UTC, debug build 1.0.0 (release key), lyrics on, sharp artist image, Büro; fresh
process (pid 25207, unchanged to the end), 10 warm-up changes, then 30 measured `KEYCODE_MEDIA_NEXT`. The "TOTAL:" line of `dumpsys meminfo` is the total PSS (same column as the 194.2 MB start sample of 10.5). KB values, MB = KB / 1000.

| Point | TOTAL PSS (KB) | Graphics (KB) | Java heap (KB) | Native heap (KB) |
|---|---|---|---|---|
| start (after 10 warm-up changes) | 194007 | 107374 | 8100 | 27368 |
| after 10 changes | 206449 | 113519 | 8568 | 31516 |
| after 20 changes | 213641 | 122918 | 8964 | 29820 |
| after 30 changes | **196260** | 106639 | 9428 | 30080 |
| 30 s later | 189262 | 104043 | 9848 | 25192 |
| maximum during the run (after change 8) | 217995 | 126758 | 9440 | 29356 |
| minimum during the run (30 s later) | 189262 | 104043 | 9848 | 25192 |

Result: the criterion as written (after 30 changes under 200 MB, no trend) is met: 196.3 MB (191.7 MiB), 189.3 MB after 30 s. Trend: the 30 values fluctuate between 194 and 218 MB with the graphics
share (101 to 127 MB, GPU cache released in steps: 125 MB after change 6, 112 MB after change 9, 102 MB after change 22); Java heap stays at 8 to 10 MB, native heap at 27 to 32 MB, code 29 to 32 MB;
the last 9 values (changes 22 to 30) are 194 to 196 MB, lower than the middle of the run. No upward trend. **Caveat:** between changes 1 and 21 the total was above 200 MB in every sample (201 to 218 MB, max 218.0 MB
after change 8), so the criterion holds at the defined measuring points (after 30 changes) but not at every moment of the run; the margin at the defined point is 3.7 MB. The soak of 10.5 (13 samples, 104 to 194 MB, 157 MB at the end) is the trend evidence over two hours.
No code change was made.

### 11.6 Acceptance flow and Maestro flows (run 2026-10-18/19)

- `scripts/acceptance-firetv.sh` (criteria 1 to 12 of `docs/PLAN.md` section 13, one result line and screenshot `p5-acc-NN.png` each, ends with Büro paused). Criterion 1 installs the
  **release** APK and starts it; since only the debug build has `DUMP_STATE`, the state checks run on the debug build with the same key (installed with `install -r`).
  Criterion 9 uses injected key codes 85, 87, 88; the physical remote is separate. Criterion 12 refers to 10.2. `DUMP_STATE` got a `seek` field (seconds) for criterion 7.
- Six Maestro flows were stale after the sharpness row, the Lyrics bar button and the lyrics timing row were added. Repaired: the bar needs one more Left press to reach
  Settings (Zones, Settings, Lyrics, previous, play/pause, next), and the settings rows are Background, Sharpness, Colour, Controls, Screen on, via roon, Lyrics (LRCLIB),
  Lyrics timing, Search for Core again, Switch zone. `phase1.yaml` and `phase1-zones.yaml` looked for "Choose a zone" but the title is "Select zone" (English UI): fixed.
  The scripts that run the flows now set a defined state first (`SET_LRCLIB on`, `SET_BACKGROUND cover`, `SET_SCREEN_ON always`, `SET_LYRICS off`); `scripts/maestro-regression.sh` runs all
  ten flows in order.

Acceptance run (`scripts/acceptance-firetv.sh`, 2026-10-18 23:26 to 23:29 device time, release APK built and installed with `install -r`, then the debug APK with the same key; `p5-acceptance.txt`),
11 pass, 0 fail, 1 reference, Büro paused at the end, debug APK 1.0.0 left installed. Result lines verbatim (screenshots `p5-acc-01.png` to `p5-acc-12.png`):

```
ACC 01 PASS release APK versionName=1.0.0 installed with certificate 76bd1d41f70843b362806bbb81cc1a7dce77c51d87ccbd27059edafb18b3c6fb, running
ACC 02 PASS Connected, endpoint 192.168.1.194:9330, log: endpoint=cached
ACC 03 PASS no authorization screen; registry log: 10-18 23:26:50.953 29598 29598 I Roomscreen: registry=register_sent core_id=b4fe5d4d-cdc1-45b6-8818-01a39eda3309 token_present=true|10-18 23:26:51.007 29598 29727 I Roomscreen: registry=token_saved core_id=b4fe5d4d-cdc1-45b6-8818-01a39eda3309 verb=CONTINUE|
ACC 04 PASS Now Playing for zone Büro
ACC 05 PASS state=playing
ACC 06 PASS title="Have A Talk With God" artist="Stevie Wonder" cover url=http://192.168.1.194:9330/api/image/e823689cd53deace8ab194f37fea68ed?scale=fill&width=1280&height=720
ACC 07 PASS position 45 s -> 52 s after 5 s
ACC 08 PASS title "Have A Talk With God" -> "The Long Run (2013 Remaster)"
ACC 09 PASS 85: playing->paused->playing; 87: "The Long Run (2013 Remaster)" -> "Tracks of My Tears"; 88: "Tracks of My Tears" -> "Tracks of My Tears" (seek 4 s). 23:27:51.931 ... key code=85 name=KEYCODE_MEDIA_PLAY_PAUSE ... key code=87 name=KEYCODE_MEDIA_NEXT ... key code=88 name=KEYCODE_MEDIA_PREVIOUS
ACC 10 PASS lyrics=synced:14 for "Tracks of My Tears" (log: lyrics fetch=cache variant=exact result=synced lines=30)
ACC 11 PASS same zone Büro, settings unchanged, connection Connected, no authorization screen
ACC 12 REF met on the real Core on 2026-10-18 (docs/ARCHITECTURE.md section 10.2, scripts/phase4-resilience.sh); this script does not restart the Core
```

(ACC 09 key 88 on the second track restarted the same track: the title stays, the position is 4 s, which the script accepts as "previous".) `p5-acc-01.png` shows Now Playing with lyrics; `p5-acc-10.png`
shows "Tracks of My Tears" with synchronized lyrics over the artist background.

Maestro regression (`scripts/maestro-regression.sh`, all ten flows). The first runs failed (see below), the last full run was 10 of 10:

| Flow | Result (final run) |
|---|---|
| phase1 | PASS |
| phase1-zones | PASS |
| phase2 | PASS |
| phase2-settings | PASS |
| phase2-settings-restore | PASS |
| phase2-artist | PASS |
| phase2-connection | PASS |
| phase3-lyrics-on | PASS |
| phase3-lrclib-off | PASS |
| phase3-lrclib-on | PASS |

Flow problems found and fixed (no app change): `phase1` and `phase1-zones` still pressed Down and Center, which shows the bar with the focus on play/pause and so toggled Büro; they now press Up, then Left four times
(previous, Lyrics, Settings, Zones) and Center. `phase2-artist` expected two background values, the row cycles Albumcover, Künstlerbild, Farbe, so the way back needs two presses. `phase2` pressed Center at the end after the
5 s bar window had closed (Maestro needs several seconds per assertion): it now waits until the bar is hidden and shows it again with a retry. `maestro-regression.sh` now parks Büro paused before each flow
(`park`: pause when playing, play and pause when stopped; Büro reached the end of its queue once, state `stopped`, and every flow reading "Büro · Pausiert" failed). Runs 1 and 2 failed 10 of 10 (run 1: the
remote was in use by the user and the flows were stale; run 2: same cause, the user was still pressing keys), run 3 aborted after phase2-artist, run 4 had 4 failures from Büro state, run 5 had 1 failure (phase2, flaky wait), run 6 passed 10 of 10.

### 11.7 Hand-over structure (done)

- `docs/SPEC.md`: platform-neutral specification (screens and transitions, connection, zones, progress, control, control bar, settings, lyrics, screen on, platform interface).
- `shared/fixtures/` (moved with `git mv` from `roon-client/src/test/resources/fixtures`; `roon-client` and `lyrics` add `shared/` as a test resource directory, so `/fixtures/<name>` and
  `/testvectors/<name>` stay on the classpath).
- `shared/testvectors/`: `progress.json` (11 cases), `zone-resolver.json` (6), `zone-groups.json` (4 steps), `lrc.json` (11), `lyrics-sync.json` (8), `query-variants.json`
  (12 title cleanings, 2 first-artist, 3 variant lists, 1 key), `lrclib-responses.json` (13). Derived 1:1 from the inline tests, which stay for what is not a vector (timeouts, retry
  timing, file cache, optimistic state). JUnit 5 `@TestFactory` readers: `ProgressVectorsTest`, `ZoneVectorsTest` (roon-client), `LyricsVectorsTest` (lyrics); they use the
  kotlinx.serialization already present in both modules.
- `shared/design/tokens.json` and `app/.../Tokens.kt`: colours, text sizes and line heights, five dimensions (page padding, cover sizes, bar button, lyrics line height), durations,
  factors. All colour literals, all text-size literals and the named duration constants were replaced by `Tokens`; spacing and corner values that occur once stay inline.
  Values are registered by the property delegates, so `SharedConsistencyTest` compares both ways without reflection. Roborazzi images did not change (checked with `git status` on
  `app/src/test/screenshots` before and after the refactor).
- `shared/strings/en.json`, `de.json`: generated from the Android resources (71 strings each); `SharedConsistencyTest` compares names (both ways, against `strings.xml` and `values-de`)
  and values (against the Robolectric resources of both locales).

### 11.8 Test result of the code and document part

`scripts/dock ./gradlew test`: 251 tests, 0 failures (roon-client 93, lyrics 100, app 58; before this phase part: 175). New: 71 vector tests, 4 `SharedConsistencyTest`, 1 `clearEndpointKeepsTokenAndZone`.
`git status` on `app/src/test/screenshots` after the run: no changed image (the two `1-connecting*` images were re-recorded and committed in the loading-indicator step; the `Tokens` refactor changed none).
