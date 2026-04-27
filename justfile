
alias td := run-tauri-dev

# We need to use 'tauri dev' with feature 'onekeepass-dev' enabled
# Expecting the front end http server is running or will wait for it to start 
run-tauri-dev:
    cargo tauri dev -f onekeepass-dev

# Compiles the cljs UI code during devtime
compile-cljs:
    just -f ./src-cljs/justfile shadow-cljs-compile

# Compiles the cljs UI code for the production release build (avanced mode)
build-cljs-bundle:
    just -f ./src-cljs/justfile shadow-cljs-clean-release

mac-aarch64-bundle-build-only:
    #!/usr/bin/env bash
    set -euxo pipefail
    export BOTAN_CONFIGURE_OS='macos'
    export BOTAN_CONFIGURE_CC_ABI_FLAGS='-arch arm64'
    export BOTAN_CONFIGURE_CC='clang'
    export BOTAN_CONFIGURE_CPU='arm64'
    export BOTAN_CONFIGURE_DISABLE_MODULES='tls,pkcs11,sodium,filters'
    
    #cargo tauri build --verbose --target aarch64-apple-darwin

    # Need to do releae build of sidecar onekeepass-proxy executable
    just -f ./onekeepass-proxy/justfile build-cp-mac-aarch64 true

    # This will build "release"
    cargo tauri build --target aarch64-apple-darwin

mac-x86_64-bundle-build-only:
    #!/usr/bin/env bash
    set -euxo pipefail
    export BOTAN_CONFIGURE_OS='macos'
    export BOTAN_CONFIGURE_CC_ABI_FLAGS='-arch x86_64'
    export BOTAN_CONFIGURE_CC='clang'
    export BOTAN_CONFIGURE_CPU='x86_64'
    export BOTAN_CONFIGURE_DISABLE_MODULES='"tls,pkcs11,sodium,filters"'

    # Need to do releae build of sidecar onekeepass-proxy executable
    just -f ./onekeepass-proxy/justfile build-cp-mac-x86_64 true
    
    # This will build "release"
    cargo tauri build --target x86_64-apple-darwin

    ## This is for debug build and it happens only during 'dev' time
    ## This requires cljs repl running. Last time the loading resource file did not work
    # cargo tauri dev --target x86_64-apple-darwin


build-mac-x86_64-bundle:build-cljs-bundle
    just mac-x86_64-bundle-build-only

build-mac-aarch64-bundle:build-cljs-bundle
    just mac-aarch64-bundle-build-only 


################

# This will build linux "release" bundle when called in a Linux terminal
build-linux-x86_64:
    just -f ./onekeepass-proxy/justfile build-cp-linux-x86_64 true
    cargo tauri build

