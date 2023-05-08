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

mac-build-common:
    clojure -M:frontend:fw  -m figwheel.main -O advanced  -bo dev
    mkdir  -p ./resources/public/cljs-out/dev
    cp  ./target/public/cljs-out/dev/main_bundle.js  ./resources/public/cljs-out/dev/main_bundle.js

# tauri build --target 
mac-unv-bundle-build:mac-build-common
    cargo tauri build --target universal-apple-darwin

mac-x86_64-bundle-build:mac-build-common
    cargo tauri build --target x86_64-apple-darwin


mac-aarch64-bundle-build:mac-build-common
    cargo tauri build --target aarch64-apple-darwin
