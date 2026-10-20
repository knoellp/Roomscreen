# Roomscreen

*A Now Playing display for every room - for Roon on Fire TV and Android TV.*

Roomscreen shows what is playing in one Roon zone: cover, title, artist, album, progress and, if you like, synchronized lyrics,
on a big blurred backdrop. It is a native Android TV app (Kotlin, Compose for TV) that talks directly to your Roon Core.
There is no helper service, no bridge and no web page to host.

> Roomscreen is not affiliated with or endorsed by Roon Labs. Roon is a trademark of Roon Labs LLC.

## Requirements

- A Roon Core (Roon Server 2.x) in the same network as the TV device.
- A device with Fire OS or Android TV, for example the Fire TV Stick HD (2020). It was tested on that stick (Fire OS 7, API 28).
- It does **not** run on devices with Vega OS (Fire TV Stick 4K Select, Fire TV Stick HD 2026).

## Install

There is no download page yet (see Known limitations). Install the APK with ADB, either one you were given or one you build.

1. On the Fire TV: Settings, My Fire TV, Developer options: turn on ADB debugging (and "Apps from unknown sources").
2. `adb connect <fire-tv-ip>:5555` and confirm the dialog on the TV.
3. `adb install -r app-debug.apk`
4. Start Roomscreen from the apps list.

Build it yourself (needs Docker, no local JDK or Android SDK):

```
docker build -t roomscreen-android docker/android-build
```

then run the Gradle task `:app:assembleDebug` in that image as described in the developer notes (`docs/DEVELOPMENT.md`).
The APK is `app/build/outputs/apk/debug/app-debug.apk`.
Always update with `adb install -r` and the same signing key, so the app keeps its Roon authorization. Never uninstall to update.

## Set up Roon

On the first start the app finds the Core by itself (or you enter its address). It registers as an extension named
"Roomscreen (<device name>)". In Roon open **Settings → Extensions**, find it and press **Enable**. The app continues by itself.
Then pick the zone to show once; it is remembered. When you group zones in Roon, Roomscreen follows the group and switches
back when the group dissolves.

## Remote control

| Key | Control bar hidden (default) | Control bar visible |
|---|---|---|
| Left | previous track | moves to the next button on the left |
| Right | next track | moves to the next button on the right |
| Center / OK | play or pause, and shows the bar | presses the focused button |
| Up / Down | shows the control bar | keeps the bar open |
| Play/Pause, Rewind, Fast forward (media keys) | play or pause, previous, next - always, the bar stays hidden | same |
| Back | closes Settings and the zone list, otherwise leaves the app | same |

The control bar has a speaker button right of "next" (only if the zone has at least one output that Roon can control). OK on it opens the volume panel:

| Key | Volume panel open |
|---|---|
| Left / Right | lower / raise the selected row |
| Up / Down | move between the group row and the outputs (fixed outputs are skipped) |
| Center / OK | does nothing |
| Back | closes the panel (the app stays) |
| Media keys | still play, pause, previous, next |

The control bar (Zones, Settings, Lyrics, previous, play/pause, next) shows for 5 seconds after Up, Down or OK and again after
each key while it is open. With the setting **Controls: always visible** the ring always navigates the bar and never
controls playback directly. Commands are only sent if the zone allows them; otherwise they are ignored.
Zones opens the zone list to switch zones; Settings opens the settings page.

## Volume

The panel at the top right, directly below the transport buttons of the control bar, shows one row per output of the shown zone; for a group of outputs a group row comes first. Holding or repeating a key speeds up: clicks 1 to 3 change the volume by one step, clicks 4 to 9 by three steps, from click 10 by five; a change of direction starts again at one. It closes with Back or after 8 seconds without a key; the control bar stays hidden while it is open.
For a group, the group value is the average of its outputs and a click moves that average; every output follows in proportion (40 and 10 become 44 and 11 when the average rises by 10 percent), as Sonos does it. An output at 0 stays at 0, outputs without volume control are shown as "fixed" and never changed, a muted output is marked with a struck-through speaker. Verified on the Fire TV Stick with a single zone and with a group of two zones (see `docs/STATUS.md`).

## Settings

Open **Settings** from the control bar; OK changes a value, Back returns. Values are stored on the device.

| Setting | Values (default first) |
|---|---|
| Background | album cover, artist image (falls back to the cover), colour |
| Background sharpness | Soft (blurred) or Sharp (full-size image, no blur); hidden while the background is "Colour" |
| Background colour | Anthracite, Black, Midnight blue, Dark green, Wine red, Warm grey |
| Controls | show on input, always visible |
| Screen on | always, only while playing |
| Key sounds | off (default), on. The interaction sounds (focus move, click) go out over HDMI and can switch the TV audio, which stops a Roon zone on the TV; hence off by default |
| Show "via roon" | on, off |
| Lyrics (LRCLIB) | on, off (off: no requests to lrclib.net at all, no Lyrics button) |
| Lyrics timing | 0 s, then -3.0 s to +3.0 s in 0.5 s steps. Plus shows the lines earlier, minus later. |
| Core | shows the address; "Search for Core again" runs discovery; "Switch zone" |

The language follows the system (English, German). By default the screen stays on while Roomscreen is in the foreground.

## Lyrics and privacy

The control bar has a **Lyrics** button (German: **Liedtexte**). The text comes from [LRCLIB](https://lrclib.net) and is looked up per track:
synchronized text highlights the active line; plain text is static (Up/Down scrolls it); if nothing is found you see a small
"No lyrics found" line; if LRCLIB is not reachable you see the normal view and the next track asks again.

Only when lyrics are on, the app sends title, artist, album, duration and a User-Agent (`Roomscreen/<version> (https://github.com/knoellp/Roomscreen)`)
to lrclib.net. Nothing else is transmitted. The app stores only the Roon token, the Core address, the chosen zone and the display settings
on the device, plus a lyrics cache (at most 300 files; "not found" for 7 days). The setting **Lyrics (LRCLIB)** turns LRCLIB off completely.

## Troubleshooting

- **The Core is not found:** on the connecting screen choose "Enter address manually" and type the Core's IP address (port 9330 by default);
  or open Settings and use "Search for Core again". The TV device and the Core must be in the same network.
- **The extension disappeared in Roon or Now Playing never appears:** open Roon, Settings → Extensions, and press Enable again.
- **Playback on a zone connected to the TV stops when you press keys:** keep "Key sounds" off (the default); the interaction sounds can switch the HDMI audio.
- **A hint "Core not reachable" appears after 20 seconds:** check that the Core is running and reachable from the TV; the app keeps trying and searches again after a few failures.
- **`adb install -r` fails with a signature error:** the installed app was signed with another key. Compare the certificate SHA-256 digest of your APK with
  `76bd1d41f70843b362806bbb81cc1a7dce77c51d87ccbd27059edafb18b3c6fb` (also in `config/release-fingerprint.txt`). A different key needs a one-time uninstall (the stored authorization is lost) and a new Enable in Roon.

## Known limitations

- Only Fire OS / Android TV. Vega OS devices and the Echo Show 15 are not supported yet; Samsung Tizen is planned.
- There is no release download yet: the release pipeline is not built (planned). Install by ADB.
- LRCLIB often answers "503 overloaded"; on those tracks no lyrics appear until the next track.
- Plain-text lyrics and the "No lyrics found" line were only verified in tests and screenshots, not with a real track on the device.
- The direct ring-key control (Left, Right, OK) was verified on the stick with injected key events; the physical remote (a Samsung TV remote over HDMI-CEC) was last checked with the previous build (media keys and bar navigation). Volume keys of a TV remote stay in the TV; the app cannot route them to Roon.

## License

Apache-2.0. Roon is a trademark of Roon Labs LLC; Amazon, Fire TV and Samsung names belong to their owners and are used descriptively only.
Bundled font: Comfortaa from https://github.com/google/fonts (SIL Open Font License 1.1, text in `docs/fonts/comfortaa-OFL.txt`), used for the "roon" word of the "via roon" hint.

Developers: see `docs/DEVELOPMENT.md` (build, tests, scripts); test and device status: `docs/STATUS.md`.
