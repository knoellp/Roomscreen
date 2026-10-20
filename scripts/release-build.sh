#!/usr/bin/env bash
# Builds, verifies and packages the signed release APK. Runs inside the build container:
#   scripts/dock bash scripts/release-build.sh
set -euo pipefail
cd "$(dirname "$0")/.."

APKSIGNER=/opt/android-sdk/build-tools/36.0.0/apksigner
GRADLE_FILE=app/build/outputs/apk/release
OUT=build/release

if [ -z "${RELEASE_KEYSTORE:-}" ]; then
  echo "ERROR: RELEASE_KEYSTORE is not set; refusing to build an unsigned release." >&2
  exit 1
fi
if [ ! -f "$RELEASE_KEYSTORE" ]; then
  echo "ERROR: keystore file not found: $RELEASE_KEYSTORE" >&2
  exit 1
fi

./gradlew test
./gradlew :app:assembleRelease

APK="$GRADLE_FILE/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "ERROR: $APK was not produced (unsigned build?)." >&2
  exit 1
fi

DIGEST=$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr 'A-F' 'a-f')
EXPECTED=$(grep -v '^#' config/release-fingerprint.txt | tr -d '[:space:]' | tr 'A-F' 'a-f')
echo "Signer certificate SHA-256: $DIGEST"
echo "Expected fingerprint:       $EXPECTED"
if [ -z "$DIGEST" ] || [ "$DIGEST" != "$EXPECTED" ]; then
  echo "ERROR: APK signing certificate does not match config/release-fingerprint.txt." >&2
  exit 1
fi

VERSION_NAME=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*"([^"]+)".*/\1/')
VERSION_CODE=$(grep -E '^\s*versionCode\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*=\s*([0-9]+).*/\1/')
if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "ERROR: could not read versionName/versionCode from app/build.gradle.kts." >&2
  exit 1
fi

VERSIONED="Roomscreen-${VERSION_NAME}-${VERSION_CODE}.apk"
rm -rf "$OUT"
mkdir -p "$OUT"
cp "$APK" "$OUT/$VERSIONED"
cp "$APK" "$OUT/Roomscreen.apk"
(
  cd "$OUT"
  {
    echo "# signing certificate SHA-256: $DIGEST"
    sha256sum "$VERSIONED" Roomscreen.apk
  } > SHA256SUMS
)

echo
echo "RESULT version:     $VERSION_NAME ($VERSION_CODE)"
echo "RESULT certificate: $DIGEST (matches config/release-fingerprint.txt)"
echo "RESULT files:       $OUT/$VERSIONED $OUT/Roomscreen.apk $OUT/SHA256SUMS"
sed 's/^/RESULT sums:        /' "$OUT/SHA256SUMS"
