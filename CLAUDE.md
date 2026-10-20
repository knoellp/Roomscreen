# Roomscreen

"Now Playing" display for Roon zones on TV platforms: Fire OS (native Android TV app in Kotlin, Compose for TV; version 1), Samsung Tizen and Fire TV Vega (shared web core, later). Talks directly to the Roon Core via SOOD (discovery, where the platform can) and MOO over WebSocket. No helper service. Full brief: `docs/PLAN.md`. User README: `README.md`; developer notes: `docs/DEVELOPMENT.md`; test and device status: `docs/STATUS.md`. Next phase: `docs/PLAN-F3b.md`. Architecture decisions: `docs/ARCHITECTURE.md`.

Phase names (see `docs/PLAN.md` section 10): F3 (Fire TV acceptance, release signing, hand-over), F3b (touch, diagnostics overlay, release pipeline), T0... (Tizen), V0... (Vega).

## Hard constraints
- Direct app <-> Roon Server communication; no Node.js, Docker service, bridge or other helper at runtime.
- Fire OS: native UI only (Kotlin + Compose for TV). Deliverable: sideloadable APK.
- No hosted web page and no code loaded from the network at runtime. A UI made of bundled HTML/CSS/JS files inside an embedded WebView is allowed (Tizen, Vega, a later Fire OS shell); the Fire OS version 1 app has no WebView.
- Do not present placeholders as finished features; document untested/unimplemented items in the README (Known limitations) and in `docs/STATUS.md`.
- Code, comments, commits, README in English; UI strings in English plus German resources.

## Build and test
All tooling runs in Docker (no local JDK/SDK; no sudo):
- `docker build -t roomscreen-android docker/android-build`
- `scripts/dock <cmd>` runs a command in the image with `--network host`, project mounted at /work.
  - `scripts/dock ./gradlew test`
  - `scripts/dock adb connect $FIRE_TV_IP:5555`
  - `scripts/dock maestro --version`
- Toolchain exceptions: the Tizen SDK (from phase T1) and the Vega SDK/CLI (from phase V0) are installed in the user's home directory, not in Docker. Web core: Node.js 20+, project-local dependencies (`npm ci`), no global packages.
- No sudo anywhere. If a step needs sudo or a system-wide install: stop and ask.
- Local config: `config/local.env` (gitignored; see `config/local.env.example`). Release signing: `config/signing.env` (gitignored; see `config/signing.env.example`), loaded by `scripts/dock`.

## Test rules
- "Compiles" is not evidence. A feature is done only with a green test run, screenshot or log excerpt.
- Always sign with the same key; use `adb install -r`. Never uninstall or clear app data without asking.
- Use the dedicated Roon test zone only; never disturb other zones.
- Never hardcode a Core IP; use discovery or config.

## Git
- Commit and push after each meaningful step. Never commit secrets (keystores, tokens, local.env).
- Commit messages: plain English, no tool/assistant attribution, no Co-Authored-By.

## Releases
- `docs/RELEASING.md` is the procedure. `versionCode` rises with every release, the tag is `v<versionName>`, `CHANGELOG.md` gets a heading per version first.
- Only release builds signed with the release key are published; never a debug build. The workflow refuses a wrong keystore, certificate, tag or version code.
- The user creates the GitHub release (`gh release create`); the workflow uploads the assets and commits `releases/`.
