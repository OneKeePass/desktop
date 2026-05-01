#!/usr/bin/env bash
#
# Builds the MAS .app in dev-signing mode (Apple Development cert + Mac
# Development profile) and launches it for local sandbox testing.
# Prints the command to tail sandbox violations in another terminal.
#
# Run from desktop/ root or via desktop/scripts/test-mas-locally.sh
# Override the mode if needed: BUILD_FOR=mas ./scripts/test-mas-locally.sh
# (but a mas-signed .app cannot be launched directly outside TestFlight.)

set -euo pipefail

cd "$(dirname "$0")/.."

BUILD_FOR="${BUILD_FOR:-dev}" ./scripts/build-mas.sh

HOST_ARCH=$(uname -m)
case "$HOST_ARCH" in
    arm64)  TARGET="${TARGET:-aarch64-apple-darwin}" ;;
    x86_64) TARGET="${TARGET:-x86_64-apple-darwin}" ;;
esac
# Mirror build-mas.sh's fallback — Tauri 2 sometimes drops the .app at the
# host-default bundle path even when --target was passed.
APP="src-tauri/target/$TARGET/release/bundle/macos/OneKeePass.app"
if [ ! -d "$APP" ] && [ -d "src-tauri/target/release/bundle/macos/OneKeePass.app" ]; then
    APP="src-tauri/target/release/bundle/macos/OneKeePass.app"
fi

cat <<EOF

============================================================
To watch sandbox violations live, run this in another terminal:

    log stream --predicate 'sender == "sandboxd" OR (subsystem == "com.apple.sandbox" AND messageType >= 16)' --info

To see the app's own log output:

    log stream --predicate 'process == "OneKeePass"' --info

App container will be created at:
    ~/Library/Containers/com.onekeepass/

App Group container (shared with proxy) will be at:
    ~/Library/Group Containers/group.com.onekeepass.desktop/

============================================================

Opening $APP ...
EOF

open "$APP"
