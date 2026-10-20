# Releases

This directory holds the current signed APK and the five previous versions (`Roomscreen-<version>-<versionCode>.apk`) plus
`SHA256SUMS`. It is maintained by the release workflow (`.github/workflows/release.yml`); do not edit it by hand.

- All versions: https://github.com/knoellp/Roomscreen/releases
- Stable URL of the latest version: https://github.com/knoellp/Roomscreen/releases/latest/download/Roomscreen.apk
- Install or update: `adb install -r Roomscreen-<version>-<versionCode>.apk`. Never uninstall to update; that deletes the Roon
  token and needs a new "Enable" in Roon.
- Signing certificate SHA-256: `76bd1d41f70843b362806bbb81cc1a7dce77c51d87ccbd27059edafb18b3c6fb`
