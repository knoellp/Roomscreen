#!/usr/bin/env bash
# Build the debug APK, install it on the Fire TV (adb install -r, never uninstall), start it,
# optionally run a Maestro flow, and save a screenshot plus logcat to build/device-artifacts/.
# Usage: scripts/deploy-test.sh [--flow maestro/phase1.yaml] [--wait SECONDS] [--via-roon on|off]
# Everything runs in ONE container because the adb server dies with it.
set -euo pipefail
cd "$(dirname "$0")/.."
exec scripts/dock bash scripts/device-steps.sh "$@"
