#!/usr/bin/env bash
# Cargo runner used by MAS builds so botan-src gets the right clang -arch flag
# for each target slice, including Tauri's universal-apple-darwin build where
# cargo is invoked once for aarch64 and once for x86_64.

set -euo pipefail

target=""
prev=""
for arg in "$@"; do
    if [ "$prev" = "--target" ] || [ "$prev" = "-t" ]; then
        target="$arg"
        break
    fi

    case "$arg" in
        --target=*)
            target="${arg#--target=}"
            break
            ;;
    esac

    prev="$arg"
done

if [ -z "$target" ]; then
    case "$(uname -m)" in
        arm64)  target="aarch64-apple-darwin" ;;
        x86_64) target="x86_64-apple-darwin" ;;
    esac
fi

case "$target" in
    aarch64-apple-darwin)
        export BOTAN_CONFIGURE_CC_ABI_FLAGS="-arch arm64"
        ;;
    x86_64-apple-darwin)
        export BOTAN_CONFIGURE_CC_ABI_FLAGS="-arch x86_64"
        ;;
    *)
        unset BOTAN_CONFIGURE_CC_ABI_FLAGS
        ;;
esac

exec cargo "$@"
