#!/usr/bin/env bash
# Developer helper: build the launcher from source and install it with the real installer.
#   TV_ADDR=192.168.1.20 scripts/launcher.sh            build, install, switch the TV to it
#   TV_ADDR=192.168.1.20 scripts/launcher.sh --no-launch build and install without touching the screen
#   scripts/launcher.sh --uninstall                     restore the original home screen
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
if [ "${1:-}" != "--uninstall" ]; then
  ./gradlew :launcher:assembleRelease --quiet
  exec ./install.sh --apk launcher/build/outputs/apk/release/launcher-release.apk --yes "$@"
fi
exec ./install.sh "$@"
