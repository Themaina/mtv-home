#!/usr/bin/env bash
# Build the debug APK, install it on the TV over Wi-Fi, and launch it.
# Usage: scripts/run-on-tv.sh [tv-ip:port]
set -euo pipefail

TV="${1:-${TV_ADDR:?Set TV_ADDR to your TV IP, e.g. TV_ADDR=192.168.1.20:5555}}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
cd "$(dirname "$0")/.."

"$ADB" connect "$TV" >/dev/null
./gradlew assembleDebug --quiet
"$ADB" -s "$TV" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$TV" shell am start -n com.mtv.hello/.MainActivity
