#!/usr/bin/env bash
#
# Builds OneKeePass desktop for the Mac App Store, in one of two modes.
#
# BUILD_FOR=mas (default)
#   Produces an Apple-Distribution-signed .app + a productbuild-signed .pkg
#   ready to upload to App Store Connect via altool/Transporter.
#
# BUILD_FOR=dev
#   Produces an Apple-Development-signed .app launchable on this Mac (which
#   must be registered in the team's macOS device list and listed in the dev
#   provisioning profile). Skips productbuild — dev builds are launched
#   directly via `open OneKeePass.app`. Used for local sandbox-violation
#   hunting and feature smoke tests.
#
# Steps performed (both modes):
#   1. envsubst the three .in templates (entitlements + Tauri config overlay)
#   2. Compile the cljs UI in advanced/release mode via src-cljs/justfile.
#      The dev-mode cljs output uses eval() and is blocked by the production
#      CSP (script-src 'self' — no 'unsafe-eval'), producing a blank window.
#      Mirrors the DMG flow's `build-cljs-bundle` step.
#   3. Rebuild the sidecar onekeepass-proxy via onekeepass-proxy/justfile so
#      Tauri's externalBin picks up current proxy code (mirrors the DMG flow).
#      Botan crypto-crate config is exported here as well.
#   4. tauri build with --features mas-build (auto_type compiled out)
#   5. Embed the provisioning profile as Contents/embedded.provisionprofile
#   6. Re-sign the embedded onekeepass-proxy with sandbox + app-group entitlements
#   7. Re-sign the parent .app (mandatory after modifying nested signatures)
#   8. (mas only) productbuild a signed .pkg ready for App Store Connect upload
#
# Requires: cargo, cargo-tauri, just, envsubst (gettext), codesign, productbuild.
#
# Common required env (sourced from desktop/.env.local — see .env.local.example):
#   APPLE_TEAM_ID                10-character developer team identifier
#
# Required when BUILD_FOR=mas:
#   APPLE_SIGNING_IDENTITY       e.g. "3rd Party Mac Developer Application: Name (TEAMID)"
#   APPLE_INSTALLER_IDENTITY     e.g. "3rd Party Mac Developer Installer: Name (TEAMID)"
#   MAS_PROVISION_PROFILE        absolute path to the MAS .provisionprofile for com.onekeepass
#
# Required when BUILD_FOR=dev:
#   APPLE_DEV_SIGNING_IDENTITY   e.g. "Apple Development: Name (TEAMID)"
#   MAS_DEV_PROVISION_PROFILE    absolute path to the Mac Development .provisionprofile
#
# Optional env:
#   BUILD_FOR                    "mas" (default) or "dev"
#   TARGET                       cargo target triple (default: native host arch)
#   PKG_OUT                      output .pkg path (default: OneKeePass.pkg in cwd)
#                                (ignored when BUILD_FOR=dev)
#   SKIP_CLJS_BUILD              true/1/yes to skip src-cljs release build
#   SKIP_PROXY_BUILD             true/1/yes to skip onekeepass-proxy rebuild
#
# Optional args:
#   --skip-cljs                  skip src-cljs release build
#   --skip-proxy                 skip onekeepass-proxy rebuild
#   --skip-prebuilds             skip both src-cljs and proxy rebuilds

set -euo pipefail

cd "$(dirname "$0")/.."
DESKTOP_DIR="$(pwd)"

usage() {
    cat <<'EOF'
Usage: scripts/build-mas.sh [--skip-cljs] [--skip-proxy] [--skip-prebuilds]

Environment:
  BUILD_FOR=mas|dev            Build distribution package or local dev app.
  SKIP_CLJS_BUILD=true         Skip src-cljs release build.
  SKIP_PROXY_BUILD=true        Skip onekeepass-proxy rebuild.

Options:
  --skip-cljs                  Skip src-cljs release build.
  --skip-proxy                 Skip onekeepass-proxy rebuild.
  --skip-prebuilds             Skip both src-cljs and proxy rebuilds.
  -h, --help                   Show this help.
EOF
}

truthy() {
    case "${1:-}" in
        1|true|TRUE|yes|YES|y|Y) return 0 ;;
        *) return 1 ;;
    esac
}

SKIP_CLJS_BUILD="${SKIP_CLJS_BUILD:-false}"
SKIP_PROXY_BUILD="${SKIP_PROXY_BUILD:-false}"

while [ "$#" -gt 0 ]; do
    case "$1" in
        --skip-cljs|--skip-cljs-build)
            SKIP_CLJS_BUILD=true
            ;;
        --skip-proxy|--skip-proxy-build)
            SKIP_PROXY_BUILD=true
            ;;
        --skip-prebuilds)
            SKIP_CLJS_BUILD=true
            SKIP_PROXY_BUILD=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "ERROR: unknown argument: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
    shift
done

# Source local env if present
if [ -f .env.local ]; then
    set -a
    # shellcheck disable=SC1091
    . .env.local
    set +a
fi

BUILD_FOR="${BUILD_FOR:-mas}"
case "$BUILD_FOR" in
    mas|dev) ;;
    *) echo "ERROR: BUILD_FOR must be 'mas' or 'dev' (got: $BUILD_FOR)" >&2; exit 1 ;;
esac

: "${APPLE_TEAM_ID:?APPLE_TEAM_ID is required}"

if [ "$BUILD_FOR" = "dev" ]; then
    : "${APPLE_DEV_SIGNING_IDENTITY:?APPLE_DEV_SIGNING_IDENTITY is required for BUILD_FOR=dev}"
    : "${MAS_DEV_PROVISION_PROFILE:?MAS_DEV_PROVISION_PROFILE is required for BUILD_FOR=dev}"
    SIGNING_IDENTITY="$APPLE_DEV_SIGNING_IDENTITY"
    PROVISION_PROFILE="$MAS_DEV_PROVISION_PROFILE"
else
    : "${APPLE_SIGNING_IDENTITY:?APPLE_SIGNING_IDENTITY is required for BUILD_FOR=mas}"
    : "${APPLE_INSTALLER_IDENTITY:?APPLE_INSTALLER_IDENTITY is required for BUILD_FOR=mas}"
    : "${MAS_PROVISION_PROFILE:?MAS_PROVISION_PROFILE is required for BUILD_FOR=mas}"
    SIGNING_IDENTITY="$APPLE_SIGNING_IDENTITY"
    PROVISION_PROFILE="$MAS_PROVISION_PROFILE"
fi

if [ ! -f "$PROVISION_PROFILE" ]; then
    echo "ERROR: provisioning profile does not exist: $PROVISION_PROFILE" >&2
    exit 1
fi

HOST_ARCH=$(uname -m)
case "$HOST_ARCH" in
    arm64)  DEFAULT_TARGET="aarch64-apple-darwin" ;;
    x86_64) DEFAULT_TARGET="x86_64-apple-darwin" ;;
    *) echo "ERROR: unsupported host arch: $HOST_ARCH" >&2; exit 1 ;;
esac
TARGET="${TARGET:-$DEFAULT_TARGET}"

case "$TARGET" in
    aarch64-apple-darwin)
        PROXY_JUST_RECIPE="build-cp-mac-aarch64"
        BOTAN_CPU="arm64"
        BOTAN_ABI="-arch arm64"
        ;;
    x86_64-apple-darwin)
        PROXY_JUST_RECIPE="build-cp-mac-x86_64"
        BOTAN_CPU="x86_64"
        BOTAN_ABI="-arch x86_64"
        ;;
    *)
        echo "ERROR: unsupported macOS target: $TARGET" >&2; exit 1
        ;;
esac
PKG_OUT="${PKG_OUT:-$DESKTOP_DIR/OneKeePass.pkg}"

export BOTAN_CONFIGURE_OS='macos'
export BOTAN_CONFIGURE_CC='clang'
export BOTAN_CONFIGURE_CPU="$BOTAN_CPU"
export BOTAN_CONFIGURE_CC_ABI_FLAGS="$BOTAN_ABI"
export BOTAN_CONFIGURE_DISABLE_MODULES='tls,pkcs11,sodium,filters'

echo "==> Building for: $BUILD_FOR (target: $TARGET)"

echo "==> Resolving templates"
export APPLE_TEAM_ID
for base in entitlements.mas.plist entitlements.proxy.mas.plist tauri.mas.conf.json; do
    src="src-tauri/$base.in"
    dst="src-tauri/$base"
    envsubst '${APPLE_TEAM_ID}' < "$src" > "$dst"
done

if truthy "$SKIP_CLJS_BUILD"; then
    echo "==> Skipping cljs UI release/advanced build"
else
    echo "==> Compiling cljs UI in release/advanced mode"
    just -f src-cljs/justfile shadow-cljs-clean-release
fi

if truthy "$SKIP_PROXY_BUILD"; then
    echo "==> Skipping sidecar onekeepass-proxy rebuild"
else
    echo "==> Rebuilding sidecar onekeepass-proxy ($TARGET, release)"
    just -f onekeepass-proxy/justfile "$PROXY_JUST_RECIPE" true
fi

echo "==> Building MAS .app for $TARGET"
cargo tauri build --target "$TARGET" \
    --config src-tauri/tauri.mas.conf.json \
    --bundles app \
    -- --features mas-build

# Tauri 2 with --target sometimes places the bundled .app at the host-default
# path (target/release/bundle/...) instead of target/<triple>/release/bundle/.
# Accept either layout — the binary inside is identical (cargo did honor the
# target for compilation; only the bundling output path is inconsistent).
APP="src-tauri/target/$TARGET/release/bundle/macos/OneKeePass.app"
if [ ! -d "$APP" ]; then
    FALLBACK_APP="src-tauri/target/release/bundle/macos/OneKeePass.app"
    if [ -d "$FALLBACK_APP" ]; then
        echo "==> .app not at $APP, using $FALLBACK_APP (Tauri target-bundling quirk)"
        APP="$FALLBACK_APP"
    else
        echo "ERROR: expected app bundle not found at $APP or $FALLBACK_APP" >&2
        exit 1
    fi
fi

echo "==> Embedding provisioning profile"
cp "$PROVISION_PROFILE" "$APP/Contents/embedded.provisionprofile"

echo "==> Re-signing embedded onekeepass-proxy"
codesign --force --sign "$SIGNING_IDENTITY" \
    --entitlements src-tauri/entitlements.proxy.mas.plist \
    --options runtime --timestamp \
    "$APP/Contents/MacOS/onekeepass-proxy"

echo "==> Re-signing parent .app bundle"
codesign --force --sign "$SIGNING_IDENTITY" \
    --entitlements src-tauri/entitlements.mas.plist \
    --options runtime --timestamp \
    "$APP"

echo "==> Verifying signatures"
codesign --verify --deep --strict --verbose=2 "$APP"
codesign -dvvv "$APP" 2>&1 | grep -E '^(Authority|TeamIdentifier|Identifier|Sealed)='
echo
echo "--- entitlements (parent) ---"
codesign --display --entitlements :- "$APP" | plutil -p -
echo
echo "--- entitlements (proxy) ---"
codesign --display --entitlements :- "$APP/Contents/MacOS/onekeepass-proxy" | plutil -p -

if [ "$BUILD_FOR" = "mas" ]; then
    echo "==> Building installer .pkg at $PKG_OUT"
    productbuild --component "$APP" /Applications \
        --sign "$APPLE_INSTALLER_IDENTITY" \
        "$PKG_OUT"

    echo
    echo "==> Done (BUILD_FOR=mas)."
    echo "    App: $APP"
    echo "    Pkg: $PKG_OUT"
    echo
    echo "Next: validate before upload with"
    echo "    xcrun altool --validate-app -f \"$PKG_OUT\" -t macos \\"
    echo "        -u \"\$APPLE_ID\" -p \"\$APPLE_APP_SPECIFIC_PASSWORD\""
else
    echo
    echo "==> Done (BUILD_FOR=dev)."
    echo "    App: $APP"
    echo
    echo "Next: launch locally with"
    echo "    open \"$APP\""
    echo "And in another terminal, watch sandbox enforcement live:"
    echo "    log stream --predicate 'sender == \"sandboxd\" OR subsystem == \"com.apple.sandbox\"' \\"
    echo "        --info --debug"
fi
