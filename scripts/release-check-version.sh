#!/usr/bin/env bash
# Checks that a release tag is consistent with the source tree. Needs gh (GH_TOKEN) and runs on the runner.
#   scripts/release-check-version.sh v1.0.0
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${1:-}"
if [ -z "$TAG" ]; then
  echo "usage: $0 <tag>" >&2
  exit 2
fi

VERSION_NAME=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*"([^"]+)".*/\1/')
VERSION_CODE=$(grep -E '^\s*versionCode\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*=\s*([0-9]+).*/\1/')
if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "ERROR: could not read versionName/versionCode from app/build.gradle.kts." >&2
  exit 1
fi

if [ "$TAG" != "v$VERSION_NAME" ]; then
  echo "ERROR: tag $TAG does not match versionName $VERSION_NAME (expected v$VERSION_NAME)." >&2
  exit 1
fi

MAX=0
for t in $(gh release list --limit 1000 --json tagName --jq '.[].tagName'); do
  # The release being published is itself in the list; its assets (if any) must not block a re-run.
  [ "$t" = "$TAG" ] && continue
  for name in $(gh release view "$t" --json assets --jq '.assets[].name'); do
    if [[ "$name" =~ ^Roomscreen-.+-([0-9]+)\.apk$ ]]; then
      code="${BASH_REMATCH[1]}"
      if [ "$code" -gt "$MAX" ]; then MAX="$code"; fi
    fi
  done
done

if [ "$VERSION_CODE" -le "$MAX" ]; then
  echo "ERROR: versionCode $VERSION_CODE must be greater than the highest released code $MAX." >&2
  exit 1
fi

if ! grep -Eq "^## ${VERSION_NAME//./\\.}( |\$)" CHANGELOG.md; then
  echo "ERROR: CHANGELOG.md has no heading '## $VERSION_NAME'." >&2
  exit 1
fi

echo "OK: tag $TAG, versionName $VERSION_NAME, versionCode $VERSION_CODE (highest released: $MAX), CHANGELOG entry present."
