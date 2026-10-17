# Roomscreen Architecture

Status: Phase 2 (section 8). Sections 1-6 were written in Phase 0; section 7 records
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
