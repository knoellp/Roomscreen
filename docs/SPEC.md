# Roomscreen specification (platform neutral)

Derived from the finished Fire TV version 1 (phase F3, `docs/PLAN.md` 4.2). It is the specification for the shared web core (Tizen, Vega);
every rule below is taken from the Kotlin code and says where it lives. Wire-level protocol facts (SOOD, MOO, registration, zone
messages) are in `docs/ARCHITECTURE.md` section 2 and are not repeated here. Shared data next to this document:

- `shared/fixtures/`: recorded Core messages (see its README).
- `shared/testvectors/*.json`: cases for the pure logic (progress, zone resolver, zone groups, LRC parser, lyrics sync, query variants, LRCLIB answers). Both implementations run the same files.
- `shared/design/tokens.json`: colours, text sizes, dimensions, durations, factors.
- `shared/strings/en.json`, `de.json`: all UI texts (the Android resources are checked against them by `SharedConsistencyTest`).

Words: "must" is a requirement for every platform; "Fire TV" notes say how version 1 does it.

## 1. Screens and transitions

One screen is shown at any time. The screen is a pure function of: connection state, zone map, saved zone, "manual entry open",
"zone list open", "settings open" (`MainViewModel.compute`). Evaluation order, first match wins:

| # | Condition | Screen |
|---|---|---|
| 1 | manual entry open | **Manual entry** |
| 2 | connection state AwaitingAuthorization | **Authorization** (where to press Enable) |
| 3 | not Connected | **Connecting** with the status text of the state (see 1.1) |
| 4 | Connected, zone map empty | **Connecting**, status "Loading zones" |
| 5 | a zone is saved, no zone matches (3.2) | zone list open: **Zone list** (can go back); else **Missing zone** |
| 6 | a zone is saved and matches, zone list open | **Zone list** (current zone marked and focused, can go back) |
| 7 | matches, settings open | **Settings** |
| 8 | matches | **Now Playing** (with lyrics view when lyrics are on) |
| 9 | no zone saved | **Zone list** (first run, cannot go back) |

### 1.1 Connecting

Shows the app name, the status line, a loading indicator from the first frame (48 dp, under the status line) and the button
"Enter address manually". Status texts: Discovering "Searching for your Roon Core...", Connecting "Connecting...", Reconnecting(n)
"Connection lost. Reconnecting (attempt n)...", LoadingZones "Loading zones...". After **20 s** (`connectHintAfterMs`) on this screen a
second line "Core not reachable, still searching..." appears and, if an address is known, "Last known address: host:port".
The 20 s start when the screen is shown, not when the connection began.

### 1.2 Manual entry

Fields host (initial: saved host) and port (initial: saved port or 9330), buttons "Test connection" and "Back". Invalid input (empty host,
port outside 1..65535) shows "Enter a host and a port between 1 and 65535." and does nothing else. The test opens a WebSocket to
`ws://host:port/api` (8 s connect timeout, 10 s overall) and reads the Core info; result lines: "Testing...", ok
"Connected to <core name> (<version>)", failure "Connection failed: <reason>". On success: the address is saved as the endpoint with the
"manual" flag, the screen closes and the app reconnects to exactly that address. Back closes the screen and clears the result.

### 1.3 Authorization

Text: where to enable the extension (Roon: Settings, Extensions) and the extension name. Leaves by itself when the Core accepts the
registration (state Connected).

### 1.4 Zone list

Title "Select zone", hint "Back keeps the current zone". Zones sorted by display name with the locale collator. Each row: name, state
label (Playing, Paused, Loading, Stopped) with a dot (green while playing), a check mark on the current zone, and, while the zone plays, a detail line
"artist . title". Focus starts on the current zone, else the first row. Selecting a row stores the zone (3.1), closes the list and sends **no
command**. Back closes the list only when it can go back.

### 1.5 Missing zone

Title "Saved zone not found", body that the zone returns by itself, button "Choose another zone" (opens the zone list).

### 1.6 Now Playing

Described in sections 5 to 8. Layout (see `docs/ARCHITECTURE.md` 8): backdrop with dark gradient, info strip at the bottom (cover 200 dp,
"zone . state", title, artist, album, progress row), optional "via roon" hint, control bar.

### 1.7 Back key

| Screen | Back |
|---|---|
| Manual entry | closes manual entry |
| Zone list that can go back | closes the list |
| Settings | closes Settings |
| everything else (Connecting, Authorization, Missing zone, first-run zone list, Now Playing) | leaves the app (platform default) |

## 2. Connection

### 2.1 Order

1. If the "manual" flag is set the saved endpoint is **fixed**: it is used for the first 5 attempts of a run; after 5 failed attempts in a row
   discovery runs instead (log `endpoint=rediscover`).
2. Otherwise per attempt: the cached endpoint (last successful host and port) is probed with a plain TCP connect (**1500 ms**). If it answers it
   is used (`endpoint=cached`, no multicast needed). If not, discovery runs (`endpoint=cached_unreachable`, `endpoint=rediscover`).
3. Discovery: SOOD, hold the platform's multicast permission only while it runs. First Core found wins.
4. A platform without discovery reports "not available"; the app then stays on Connecting with the manual-entry button (section 9).
5. "Search for Core again" (Settings): clears the manual flag, skips the cached endpoint **once**, runs discovery, reconnects.

### 2.2 Session

`ws://host:port/api`, connect timeout **10 s**. Then: `info` (log core id, name, version); `register` (required services transport and image, provided service ping) with the stored token for that core id.
If the answer takes more than **2 s** the state becomes AwaitingAuthorization (the user must press Enable in Roon); authorization waits up to
**10 min**. On success: state Connected, the endpoint is saved (also for manual ones), `subscribe_zones`, every message goes through the zone
reducer (section 3.3) and replaces the zone map. A malformed zone message is logged and skipped; the socket stays open.

Extension identity: id `io.github.knoellp.roomscreen.<installId>` (installId = random, created once per installation and stored), display name
`Roomscreen (<device name>)`, publisher `knoellp`. Every installation needs its own "Enable" in Roon; a second connection with the same id
evicts the first (`docs/ARCHITECTURE.md` 7).

### 2.3 Failure, reconnect, watchdog

- **Silence watchdog:** if no frame arrives from the Core for **30 s** the connection is aborted (the Core pings every 2 s; the client pings
  too). The check runs every second.
- A session that ends (close, error, timeout, watchdog) clears the zone map, increases the attempt counter, and waits the **backoff**:
  `base 1 s`, doubling per attempt (1, 2, 4, 8, 16, 30, 30 ... s), capped at **30 s**, each delay shortened by a random 0 to 20 %
  (never longer than the cap). The state is Reconnecting(attempt); the UI shows the status text above.
- A session that reached Connected resets attempt counter and backoff to zero.
- A token the Core no longer accepts ends like a first registration: the Core answers `Pending` (state AwaitingAuthorization) until the user presses Enable; the new token is stored under the Core id.
- Timeouts (connect, info, register) count as a failed attempt, not as a stop.

### 2.4 Lifecycle

- Foreground (start): connect; idempotent, never two sockets.
- Background (stop): the connection is closed after **2 s** (`disconnectDelayMs`). If the app returns within 2 s the pending disconnect is cancelled and the
  connection and registration stay (volume overlay, screensaver preview).
- Network status lost/restored is not handled specially: the watchdog and reconnect cover it.

## 3. Zones

### 3.1 Saved selection

Stored: zone id and the set of output ids of that zone. Choosing a zone replaces both. The saved selection is **never cleared automatically**.

### 3.2 Resolving the zone to show

`shared/testvectors/zone-resolver.json`, `zone-groups.json`:

1. no saved zone id: nothing;
2. a zone with the saved zone id exists: that one (via ID);
3. else, if output ids are saved: the first zone that contains one of the saved output ids (via OUTPUT; this is how a group that contains the
   saved zone is found: Roon gives the group a new zone id and keeps the output ids);
4. else nothing (Missing zone screen).

When the saved zone is found via ID and its output set differs from the saved one, the saved output ids are updated. When a group dissolves,
the saved id matches again (via ID) and the view returns to the original zone.

### 3.3 Zone reducer

`Subscribed` replaces the map. `Changed` applies, in this order: `zones_removed` (ids), `zones_added` (objects), `zones_changed` (whole
objects replace the cached zone, including a vanished `now_playing`), then `zones_seek_changed` (only `seek_position` and
`queue_time_remaining` of known zones that have `now_playing`; other zones ignored). Unknown message names change nothing. The reducer never
mutates its input. Cases: `shared/testvectors/zone-groups.json` with `shared/fixtures/`.

## 4. Progress

`shared/testvectors/progress.json`, `docs/ARCHITECTURE.md` 8.4. The Core sends `seek_position` (whole seconds) about once per second while playing.
`ProgressInterpolator` with an injected monotonic clock:

- Track key = image key, title (line1), length. Anchor = seek position x 1000 ms at the clock time of the event.
- The anchor is replaced when the track key, the zone state or the seek value changes (drift correction, seek, pause, resume, loading).
- While the zone state is `playing`: position = anchor + (clock - anchor time). Otherwise the anchor.
- A change to non-playing without a new seek value on the same track (the optimistic UI flip) freezes at the interpolated position, not at the last
  whole second.
- A new track without a seek value starts at 0. No `now_playing`: position 0, length unknown.
- Position is capped at the track length when it is known and greater than 0; an unknown length is not capped.
- The UI reads the position at 10 Hz (`progressTickMs` 100 ms) and displays `m:ss` elapsed, a bar (fraction clamped to 0..1, 0 for unknown length), `m:ss` length (empty if unknown).

## 5. Control

- Transport controls: play, pause, playpause, next, previous. A zone *allows* a control when `is_play_allowed`, `is_pause_allowed`
  (playpause: either), `is_next_allowed`, `is_previous_allowed` is true. A control that is not allowed is **not sent**; it is logged
  (`control ignored=not_allowed`).
- Only the Now Playing screen sends commands. Selecting a zone, settings and lists never do.
- **Optimistic play/pause:** playpause shows the opposite state at once (playing <-> paused), play shows playing, pause shows paused,
  next and previous show nothing. The optimistic state is used only while the Core still reports the old state; it ends when the Core's state
  changes, after **3 s** (`pendingMs`), or when the command fails.
- Media keys (`mapMediaKey`): play/pause -> playpause, play -> play, pause -> pause, rewind and media-previous -> previous, fast-forward and
  media-next -> next. Android codes seen on the Fire TV (injected, and from a Samsung TV remote over HDMI-CEC): 85, 89, 90, 87, 88; a TV remote's volume keys are handled by the TV and never reach the app. A key acts once per press (key repeat ignored) and is
  consumed when it maps to a control on the Now Playing screen; every other key goes to focus handling, except the D-pad direct control while the bar is hidden (section 6).

## 6. Control bar

Order, left to right: **Zones**, **Settings**, **Lyrics** (only when the LRCLIB setting is on), **Previous**, **Play/Pause**, **Next**.
Initial focus: Play/Pause. D-pad left/right moves; select presses. Zones opens the zone list, Settings the settings page, Lyrics toggles the
lyrics view (selected look while on), the transport buttons send controls (section 5).

Visibility (setting "Controls"): *On input* (default): the bar is hidden until an opening key (see below) is pressed; it then shows for **5 s** (`barVisibleMs`) after the last key; it fades
in and out in **200 ms** (`barFadeMs`); the focus lands on Play/Pause. *Always*: the bar is always visible and does not take focus by itself.

Keys on Now Playing (decided 2026-10-19 after the manual acceptance; applies to every platform):

- **Bar hidden** (mode *On input*): `left` = previous, `right` = next (the bar stays hidden), `select`/`enter` = playpause **and** show the bar (so the user can navigate afterwards),
  `up`/`down` = show the bar only (no command). The key press acts once (key repeat ignored); down and up of a handled key are consumed. A command the zone does not allow is not sent (section 5).
- **Bar visible**, or mode *Always*: the keys go to the focus system (left/right move between the buttons, select presses the focused button) and extend the 5 s.
- **Other screens** (zone list, settings, connecting, manual entry): keys are never consumed; navigation only.
- Media keys (section 5) act directly in every case and do not show the bar. Back is unchanged.
- Log line per direct action: `dpad direct key=<KEYCODE name> control=<wire>`.

### 6.1 Volume

Added 2026-10-19. A TV remote's volume keys never reach the app (section 5), so the volume of the shown zone is changed in a panel that opens from the control bar. Source of the group semantics: Sonos (docs.sonos.com/docs/volume). Pure logic: `GroupVolume` / `ClickCounter` in `roon-client`, test data `shared/testvectors/volume.json` (the web core must pass the same cases).

- **Data:** each output of a zone may carry a `volume` object (`type` `number`, `db` or `incremental`, `min`, `max`, `value`, `step`, `is_muted`, `hard_limit_min`, `hard_limit_max`, `soft_limit`). An output without it is *fixed* (shown grey "fixed", skipped by the cursor, never changed). An unknown `type` is treated as `number`.
- **Button:** a speaker button right of Next in the control bar; shown only if at least one output of the zone has a `volume` object. Select opens the panel; the control bar then hides and stays hidden while the panel is open.
- **Panel:** at the top right, directly below the transport buttons of the bar (same right edge, top = bar bottom edge plus the button spacing), dark opaque background; cover and title stay visible (also in the lyrics view). Without a group one row; with several outputs a group row (name "Group") on top and one row per output below. Row: name, bar from min to max (soft limit mark), value (`41`, `-20,5 dB`; `incremental`: no number). A muted output shows a struck-through speaker. Values come from the zone events; while a request is pending the target value is shown (up to `pendingMs`, 3 s).
- **Keys while the panel is open (priority over everything except media keys):** `left` = lower, `right` = raise the selected row; `up`/`down` move between the group row and the outputs (fixed outputs skipped); `back` closes the panel (consumed, the app does not exit); `select` does nothing. Every key restarts the auto-close timer **8 s** (`volumePanelVisibleMs`). Media keys keep acting on playback.
- **Click rule:** click 1 to 3 in one direction change by 1 step unit, click 4 to 9 by 3, from click 10 by 5 (`stepFor`). A change of direction restarts the count at click 1. Key repeat counts as clicks (the panel is the one place where repeat is not ignored). The step unit of a single row is the output's own `step`; for the group row it is the smallest `step` among the controllable outputs.
- **Single row:** new value = value + direction * stepFor * step, rounded to the output's step grid (anchored at `min`), clamped to min..max. Afterwards the snapshot is retaken.
- **Group row (Sonos semantics):** the group value is the average of the controllable outputs. A click moves the *target average* by direction * stepFor * unit (kept within the outputs' overall range). Snapshot = values of the controllable outputs when the panel opened and after every single-row change. Each output gets `snapshot value * (target average / snapshot average)`, rounded to its step, clamped to min..max. An output at 0 stays 0; a target average of 0 sets all to 0 (and the snapshot is retaken with all 0, so the differences are gone); a snapshot average of 0 sets all outputs to the target. Outputs with a negative `min` (dB scales) have no meaningful ratio and are shifted by (target average - snapshot average) instead. Example: 40 and 10, average 25, target 27.5: 44 and 11.
- **Requests:** `com.roonlabs.transport:2/change_volume` with `{output_id, how, value}`. `number` and `db`: `how` = `absolute`, `value` = the new volume. `incremental`: `how` = `relative`, `value` = +1 or -1 per click, no number is shown. At most one request per output is in flight; clicks meanwhile only update the target, which is sent when the previous request completes (coalescing). Log lines: `volume send=<how> output="<name>" value=<v>`, `volume ok output="<name>"`, `volume failed output="<name>" error=<text>`.
- **Mute** is not controllable; only shown.

## 7. Settings

Page "Settings" (hint: select changes a value, Back returns). Select cycles the value (wrapping) or toggles; values are saved at once.
Defaults (`AppSettings`), row order:

| Row | Values, default first | Shown when |
|---|---|---|
| Background | Album cover, Artist image, Colour | always |
| Background sharpness | Soft, Sharp | background is not Colour |
| Background colour | Anthracite, Black, Midnight blue, Dark green, Wine red, Warm grey (palette tokens) | always |
| Controls | On input, Always visible | always |
| Screen on | Always, Only while playing | always |
| Key sounds | off, on (default off) | always |
| Show "via roon" | on, off | always |
| Lyrics (LRCLIB) | on, off | always |
| Lyrics timing | 0 s, then -3.0 s to +3.0 s in 0.5 s steps (select adds 0.5 s, from +3.0 s wraps to -3.0 s); hint "minus = later, plus = earlier" | LRCLIB on |
| (info line) Core: host:port or "not connected" | | above the next row |
| Search for Core again | action | always |
| Switch zone | action (closes Settings, opens the zone list) | always |

Persisted keys (any key-value store): host, port, manual flag, install id, zone id, output ids, one key per setting, lyrics button state
(default off), one token per Core id. The token store is keyed by Core id.
Language follows the system (English, German); the strings are in `shared/strings`.

Background image rules (Now Playing): Colour: no image, no gradient, the whole area is the chosen colour. Cover: the zone's cover as backdrop;
Artist: first artist image key, falling back to the cover when the track has none. Soft: small blurred backdrop (32 px, alpha 0.75), Sharp:
artist image 1280x720 or cover 720x720 centre-cropped to 16:9, full opacity, dark gradient kept. The base under any image is the chosen colour.

## 8. Lyrics

### 8.1 Switches

Two switches: the **LRCLIB setting** (opt-out; off means **no request to lrclib.net at all** and no Lyrics button) and the **Lyrics button**
(persisted, default off). A lookup runs only while both are on and the zone has a track with a non-blank title. A new track or turning
either switch off **cancels** a running lookup (including its HTTP call).

### 8.2 Query

From `three_line`: title = line1, artist = line2, album = line3, duration = `length` in seconds (0 = unknown). Cache key
`title|artist|album|duration`. Variants, in order, each building on the previous one, duplicates dropped
(`shared/testvectors/query-variants.json`):

1. `exact`;
2. `title`: trailing additions removed (`(Album Version)`, `[Remastered 2011]`, `- Remastered 2011`, `- 2011 Remaster`, `- Single Version`,
   `[Mono Version]`, repeated), words: remaster(ed), version, edit, mono, stereo, mix, deluxe, bonus, explicit, expanded, anniversary, reissue, single;
   a group that contains "live" is kept; a title is never reduced to empty;
3. `artist`: first artist of "A / B / C";
4. `no-album`: album empty.

The variants are tried with `GET /api/get`; the first hit wins. If none hits, `GET /api/search` with the last variant (title and artist only;
results filtered to |duration - wanted| <= 2 s when a duration is known; synced beats plain beats instrumental).

### 8.3 LRCLIB client

`GET https://lrclib.net/api/get?track_name&artist_name[&album_name][&duration]` (album and duration only when known). Headers:
`User-Agent: Roomscreen/<version> (https://github.com/knoellp/Roomscreen)`, `Accept: application/json`. Only these data leave the device.
Timeouts 5 s (connect, read, write), 10 s per call. Cases: `shared/testvectors/lrclib-responses.json`.

| Answer | Result |
|---|---|
| 200 with `syncedLyrics` (some non-empty line) | Found, synced (and plain) |
| 200 with only `plainLyrics` | Found, plain |
| 200 instrumental, no text | Found, instrumental |
| 200 with neither | NotFound |
| 404 | NotFound |
| 5xx, 429, I/O error, timeout | retried after 1 s and then 3 s (3 requests in total); still failing: **Unavailable** |
| other 4xx, body that is not valid JSON | Unavailable |

### 8.4 Cache

Looked up in this order: memory "unavailable" marker, disk entry. Found: kept without limit. Not found (every variant and search returned NotFound):
disk, valid for **7 days**. Unavailable: only in memory, **10 minutes**, never on disk. Disk: one file per key (SHA-1 of the key), at most **300**
files, oldest removed first; an unreadable entry is dropped. Instrumental counts as found and shows as "no lyrics".

### 8.5 LRC parsing

`shared/testvectors/lrc.json`. Time stamps `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]`, `[mm:ss.xxx]` (also `:` before the fraction); fraction digits are tenths,
hundredths or milliseconds by length. Several stamps per line give several lines. `<mm:ss.xx>` word tags are removed. Lines with a stamp and no text
are kept as empty lines. Metadata tags and lines without a stamp are skipped. `[offset:+-ms]` anywhere in the text: positive moves all lines
**earlier** (stamp minus offset), negative later; a time never goes below 0. The result is sorted by time, stable. CR LF is accepted.

### 8.6 Active line

`shared/testvectors/lyrics-sync.json`. Active index = last line with `timeMs <= position + userOffset + 250` (`LEAD_MS`), -1 before the first line.
The user offset is the setting "Lyrics timing" (positive shows lines earlier).

### 8.7 Display

- **Synchronized:** replaces the info strip: small cover (160 dp), title, artist, thin progress bar on the left; the text list on the right with the
  active line vertically centred, white, 30 sp drawn 1.13 times larger; other lines dimmed by distance from the active line (alpha 0.62, 0.42, 0.28, 0.16).
  The list moves when the active line changes. Before the first line none is active. On a sharp background a dark scrim sits under the text.
- **Plain only:** the text, static; D-pad up/down scrolls.
- **Nothing found / instrumental:** the normal Now Playing view with a small dim line "No lyrics found" under the album.
- **Loading, unavailable, off:** the normal view without a message; the next track asks again.

## 9. Screen on

Setting "Screen on": *Always* (default): the display is kept awake whenever the app is in the foreground. *Only while playing*: kept awake only
while the Now Playing screen shows a zone whose state is `playing`. Fire TV: `FLAG_KEEP_SCREEN_ON`.

## 10. Platform interface

What every platform provides to the core (`docs/PLAN.md` 4.4) and what the core does when a capability is missing:

| Area | Platform provides | If missing |
|---|---|---|
| Discovery | run SOOD (UDP multicast 239.255.90.90:9003 and broadcast, query for the Roon service, see ARCHITECTURE 2), return host and port; multicast permission held only while it runs | Report "not available". The connection logic skips discovery: with a cached endpoint it still connects; without one the Connecting screen stays and the user enters host and port by hand (1.2). "Search for Core again" shows the same behaviour |
| Storage | persistent key-value store (keys in section 7), and a cache area for lyrics (section 8.4) | Without persistence: the app works for the session but asks for the Enable and the zone again after a restart. Without a lyrics cache: memory only, same TTLs |
| Keys | map platform key codes to: up, down, left, right, select, back, play, pause, playpause, next, previous (also rewind -> previous, fast-forward -> next); register media keys where the platform needs it. Rule on Now Playing with the bar hidden: left/right/select = previous/next/playpause, up/down = show the bar; with the bar visible the keys navigate (section 6) | Without media keys: left/right/select still control playback while the bar is hidden, and the control bar works with the D-pad. No interaction sounds (focus move, click) unless the setting "Key sounds" is on: they can switch HDMI audio and stop a Roon zone on the TV |
| Screen | keep the display awake or release it | Without it: the setting "Screen on" has no effect (the platform's own screensaver rules apply) |
| Lifecycle | foreground and background events; a way to leave the app | Without background events: the connection stays open; without a way to leave: Back on the root screens does nothing |
| Network status | link confirmed or lost | Without it: the watchdog (2.3) detects loss after 30 s |
| Device info | device name for the extension display name, platform name, app version (also the lyrics User-Agent) | Fall back to the platform name and "unknown" |
| Log | line output with the prefix `Roomscreen` into the platform's debug chain | Without it: no logs, no behaviour change |

Log lines that tests and scripts rely on (keep the exact keys): `conn state=...`, `conn=connected host= port=`, `conn=reconnect_in ms= attempt=`,
`conn watchdog=silence seconds=30`, `endpoint=cached|cached_unreachable|rediscover|cache_skipped`, `discovery result=found|none`,
`registry=...`, `zone resolved id= via=id|output|none`, `control send=|ignored=|ok=|failed=`, `lyrics fetch=net|cache|memory variant= result= lines= ms=`,
`lifecycle=disconnect_after_delay|disconnect_cancelled`, `key code= name= action= repeat=`.
