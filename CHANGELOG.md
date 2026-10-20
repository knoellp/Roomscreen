# Changelog

All notable changes to Roomscreen. Version numbers follow `versionName` in `app/build.gradle.kts`.

## 1.1.0 (2026-10-21)

- Touch: a tap shows the control bar; every button and row can be tapped; "Close" rows in the zone list and the settings; settings rows 48 dp high
- Diagnostics overlay (build, device, connection, last inputs, log): five taps on the bottom-right corner, the settings row "Show diagnostics"
- Install without a computer: signed release APKs on the Releases page and at a stable address (see the README)
- Single-task activity: a second launch brings the running app to the front instead of stacking a new instance

## 1.0.0 (2026-10-20)

First release: Fire TV version 1.

- Roon Core discovery (SOOD) and direct connection over MOO/WebSocket, including manual address entry
- Zone list with a saved zone
- Now Playing with cover art and progress
- Synchronized lyrics (LRCLIB)
- Settings: background, screen on, key sounds, lyrics timing
- Remote control with direct D-pad control
- Volume panel with group support
- Reconnect handling after Core restart or network loss
- Release signing with a fixed key (see `docs/RELEASING.md`)
