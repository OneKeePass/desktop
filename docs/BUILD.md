## Prerequisites

Please install the following for your platform of choice - macOS or Linux or Windows

1. The first step is to install Rust and system dependencies. 
   
    Follow the [Tauri setup guide](https://tauri.app/v1/guides/getting-started/prerequisites) 
2. Install [Node](https://nodejs.org/)
3. Install JDK from [Adoptium](https://adoptium.net/) or from [azul](https://www.azul.com/downloads/?package=jdk#zulu) or from [Oracle](https://www.oracle.com/java/technologies/downloads/)
4. Install [Clojure](https://clojure.org/guides/install_clojure). See more details on ClojureScript [here](https://clojurescript.org/guides/quick-start)


## Build

- Install rust tool [just](https://github.com/casey/just). This is onetime install only
``` 
cargo install just

cargo install tauri-cli
```
- Build bundle (Mac OS)
```
yarn install  or npm install --legacy-peer-deps

just build-mac-x86_64-bundle

or 

just build-mac-aarch64-bundle

```
You can find the final release build dmg file in ./src-tauri/target/x86_64-apple-darwin/release/bundle/dmg and in
./src-tauri/target/aarch64-apple-darwin/release/bundle/dmg

- Build bundle (Linux)
```
sudo apt-get install -y libwebkit2gtk-4.0-dev build-essential wget libssl-dev libgtk-3-dev libayatana-appindicator3-dev librsvg2-dev patchelf  (one time install)

yarn install

just build-cljs-bundle

cargo tauri build --target x86_64-unknown-linux-gnu

```

## Development

### Quick setup

```
just acs (This will build clojurescript and start a local web server)

In another terminal, do

just td (This will build the tauri and start the UI window)
```

### Using REPL
Comming soon