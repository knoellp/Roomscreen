# Roomscreen

Native Android TV (Kotlin, Compose for TV) "Now Playing" display for Roon zones. Talks directly to the Roon Core via SOOD (discovery) and MOO over WebSocket. No helper service, no WebView. Full brief: `docs/PLAN.md`. Architecture decisions: `docs/ARCHITECTURE.md`.

## Hard constraints
- Direct app <-> Roon Server communication; no Node.js, Docker service, bridge or other helper at runtime.
- Native UI only (Kotlin + Compose for TV). Deliverable: sideloadable APK.
- Do not present placeholders as finished features; document untested/unimplemented items in the README.
- Code, comments, commits, README in English; UI strings in English plus German resources.

## Build and test
All tooling runs in Docker (no local JDK/SDK; no sudo):
- `docker build -t roomscreen-android docker/android-build`
- `scripts/dock <cmd>` runs a command in the image with `--network host`, project mounted at /work.
  - `scripts/dock ./gradlew test`
  - `scripts/dock adb connect $FIRE_TV_IP:5555`
  - `scripts/dock maestro --version`
- Local config: `config/local.env` (gitignored; see `config/local.env.example`).

## Test rules
- "Compiles" is not evidence. A feature is done only with a green test run, screenshot or log excerpt.
- Always sign with the same key; use `adb install -r`. Never uninstall or clear app data without asking.
- Use the dedicated Roon test zone only; never disturb other zones.
- Never hardcode a Core IP; use discovery or config.

## Git
- Commit and push after each meaningful step. Never commit secrets (keystores, tokens, local.env).
- Commit messages: plain English, no tool/assistant attribution, no Co-Authored-By.
