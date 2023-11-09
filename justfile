alias r := run-repl
alias acs := advanced-compile-start-server
alias td := run-tauri-dev

run-repl:
    clojure  -M:frontend:fw:nrepl

# Run tauri dev always with onekeepass-dev enabled
run-tauri-dev:
    cargo tauri dev -f onekeepass-dev

# Compiles the UI code in advanced mode and start the http server
advanced-compile-start-server:
    clojure -M:frontend:fw  -m figwheel.main -O advanced  -bo dev -s

build-cljs-bundle:
    clojure -M:frontend:fw  -m figwheel.main -O advanced  -bo dev
    mkdir  -p ./resources/public/cljs-out/dev
    cp  ./target/public/cljs-out/dev/main_bundle.js  ./resources/public/cljs-out/dev/main_bundle.js

mac-aarch64-bundle-build-only:
    #!/usr/bin/env bash
    set -euxo pipefail
    export BOTAN_CONFIGURE_OS='macos'
    export BOTAN_CONFIGURE_CC_ABI_FLAGS='-arch arm64'
    export BOTAN_CONFIGURE_CC='clang'
    export BOTAN_CONFIGURE_CPU='arm64'
    export BOTAN_CONFIGURE_DISABLE_MODULES='tls,pkcs11,sodium,filters'
    cargo tauri build --target aarch64-apple-darwin

mac-x86_64-bundle-build-only:
    #!/usr/bin/env bash
    set -euxo pipefail
    export BOTAN_CONFIGURE_OS='macos'
    export BOTAN_CONFIGURE_CC_ABI_FLAGS='-arch x86_64'
    export BOTAN_CONFIGURE_CC='clang'
    export BOTAN_CONFIGURE_CPU='x86_64'
    export BOTAN_CONFIGURE_DISABLE_MODULES='"tls,pkcs11,sodium,filters"'
    cargo tauri build --target x86_64-apple-darwin


build-mac-x86_64-bundle:build-cljs-bundle
    just mac-x86_64-bundle-build-only

build-mac-aarch64-bundle:build-cljs-bundle
    mac-aarch64-bundle-build-only 

