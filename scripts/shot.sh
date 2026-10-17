#!/usr/bin/env bash
# Screenshot of the Fire TV to build/device-artifacts/<name>.png (own container; adb reconnects).
set -euo pipefail
cd "$(dirname "$0")/.."
exec scripts/dock bash -c 'adb connect $FIRE_TV_IP:5555 >/dev/null && mkdir -p build/device-artifacts && adb -s $FIRE_TV_IP:5555 exec-out screencap -p > build/device-artifacts/'"$1"'.png && echo build/device-artifacts/'"$1"'.png'
