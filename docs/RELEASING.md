# Releasing

How the release key is created, used and (in phase F3b) handed to the release pipeline.

## The release key

One RSA 4096 key signs every build that is installed on a device, **debug and release alike**
(`buildTypes.debug.signingConfig = release` when the key is configured). That way `adb install -r` can replace a debug build by a
release build and back without losing the Roon token. A different key makes Android refuse the update; the only way out is an
uninstall, which loses the token and needs a new "Enable" in Roon.

Files (all gitignored, never commit them):

- `config/release.jks`: the PKCS12 keystore, alias `roomscreen`.
- `config/signing.env`: `RELEASE_KEYSTORE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
  `scripts/dock` hands it to the container, Gradle reads it from the environment. (PKCS12 has one password for store and key,
  so both values are the same.)

**Back up `config/release.jks` and the passwords in a second place outside this machine. If the keystore is lost, no update can be
installed over an existing installation any more.**

The public certificate fingerprint is in `config/release-fingerprint.txt` and in the README section Troubleshooting. Check an APK with
`scripts/dock /opt/android-sdk/build-tools/36.0.0/apksigner verify --print-certs <apk>`.

### Creating the keystore (done once)

```bash
openssl rand -base64 24   # run twice, put the values into config/signing.env (copy config/signing.env.example)
scripts/dock bash -c 'keytool -genkeypair -keystore "$RELEASE_KEYSTORE" -alias "$RELEASE_KEY_ALIAS" \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Roomscreen, O=knoellp" \
  -storepass "$RELEASE_KEYSTORE_PASSWORD" -keypass "$RELEASE_KEY_PASSWORD"'
```

## Building

```bash
scripts/dock ./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
scripts/dock ./gradlew :app:assembleDebug       # same key, plus the debug hooks
```

Without `config/signing.env` the debug build uses the default Android debug key and the release build is unsigned.

## GitHub secrets for the pipeline (set on 2026-10-18)

The workflow in F3b signs the release APK with the same key. The four secrets were stored on 2026-10-18 with the commands below; re-run them only to rotate the key.
Run them from the repository root with `gh` logged in; they read the files, nothing is printed.

```bash
set -a; . config/signing.env; set +a
base64 -w0 "$RELEASE_KEYSTORE" | gh secret set RELEASE_KEYSTORE_B64
printf %s "$RELEASE_KEYSTORE_PASSWORD" | gh secret set RELEASE_KEYSTORE_PASSWORD
printf %s "$RELEASE_KEY_ALIAS"         | gh secret set RELEASE_KEY_ALIAS
printf %s "$RELEASE_KEY_PASSWORD"      | gh secret set RELEASE_KEY_PASSWORD
```

In the workflow, decode `RELEASE_KEYSTORE_B64` to a file, export `RELEASE_KEYSTORE` (path), `RELEASE_KEYSTORE_PASSWORD`,
`RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`, and run `./gradlew :app:assembleRelease`. Verify that the digest of the built
APK equals `config/release-fingerprint.txt` before publishing.

The decoded keystore must have the SHA-256 `59ee46a3bd37775e89a312a0acd84ef735ddd18ade745526904769359cf1c588` (that of `config/release.jks`); the workflow checks this before building.

## Releasing a version

1. Bump `versionCode` (must be higher than every released code) and `versionName` in `app/build.gradle.kts`.
2. Add a `## <versionName> (<date>)` entry to `CHANGELOG.md`.
3. Commit and push to `main`.
4. Create the release; the tag must be `v<versionName>`:
   `gh release create vX.Y.Z --title "Roomscreen X.Y.Z" --notes-file <notes>` (or in the GitHub web UI).
5. Publishing the release starts `.github/workflows/release.yml`. It builds, verifies and uploads
   `Roomscreen-X.Y.Z-<code>.apk`, `Roomscreen.apk` and `SHA256SUMS` to the release, then commits the versioned APK to `releases/`
   on `main` (current plus five previous versions, with a regenerated `releases/SHA256SUMS`).

Never publish a debug build. The stable download URL is `https://github.com/knoellp/Roomscreen/releases/latest/download/Roomscreen.apk`.

## The release workflow

Trigger: `release: published` (full run) or `workflow_dispatch` (build only, result is the workflow artifact `release-apk`;
nothing is published and `main` is not touched). Job `build` (read-only token):

1. Builds the image from `docker/android-build` with `WITH_MAESTRO=0` (layer cache in the GitHub Actions cache).
2. Decodes `RELEASE_KEYSTORE_B64` and aborts unless the file SHA-256 is `59ee46a3bd37775e89a312a0acd84ef735ddd18ade745526904769359cf1c588`.
3. On a release: `scripts/release-check-version.sh <tag>` requires tag = `v<versionName>`, a `versionCode` greater than every code in
   existing release assets (`Roomscreen-<name>-<code>.apk`) and a `## <versionName>` heading in `CHANGELOG.md`.
4. `scripts/release-build.sh` in the container: `./gradlew test` (tests gate the upload), `:app:assembleRelease`,
   `apksigner verify --print-certs`, and the certificate digest must equal `config/release-fingerprint.txt`; it then writes
   `build/release/Roomscreen-<name>-<code>.apk`, `Roomscreen.apk` and `SHA256SUMS`.
5. Removes the keystore (always) and uploads the artifact `release-apk`.

Job `publish` (only on a release, `contents: write`) uploads the three files with `gh release upload --clobber` and commits `releases/`
as `github-actions[bot]`. If the commit step fails, the release assets stay in place and the job is red.

Dry run: `gh workflow run release.yml --ref main`, then `gh run watch`; download the artifact with `gh run download <id> -n release-apk`.

### Same build locally

```bash
scripts/dock bash scripts/release-build.sh   # needs config/signing.env; output in build/release/
```
