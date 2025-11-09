alias r := run-repl
alias acs := advanced-compile-start-server
alias td := run-tauri-dev

# Using 'just run-repl' will start nrepl server and then we need to click jack-in or connect to the REPL server
# in MS Code to start the cljs compiling and connect to the REPL 
run-repl:
    clojure  -M:frontend:fw:nrepl

# Before calling target 'run-tauri-dev', we need to do the above 'just run-repl' in a terminal and
# then do the following in another terminal

# We need to use 'tauri dev' with feature 'onekeepass-dev' enabled
# Expecting the front end http server is running or will wait for it to start 
run-tauri-dev:
    cargo tauri dev -f onekeepass-dev

# Compiles the UI code in advanced mode 
# Bundles cljs compiled code and all dependent packages 
# using webpack (--mode=production) to /desktop/target/public/cljs-out/dev/main_bundle.js
# And then starts the http server 
# Once the http server starts, we can then use 'just td' to connect to this build
advanced-compile-start-server:
    clojure -M:frontend:fw  -m figwheel.main -O advanced  -bo dev -s

advanced-compile:
    clojure -M:frontend:fw  -m figwheel.main -O advanced  -bo dev

build-cljs-bundle type="advanced":
    rm -rf target
    clojure -M:frontend:fw  -m figwheel.main -O {{type}} -bo dev
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

