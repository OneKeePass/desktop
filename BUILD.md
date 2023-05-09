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
```
- Build bundle
```
yarn install  or npm install --legacy-peer-deps

just mac-unv-bundle-build
```
You can find the final release build dmg file in ./src-tauri/target/universal-apple-darwin/release/bundle/dmg

## Development

### Quick setup

```
just acs (This will build clojurescript and start a local web server)

In another terminal, do

just td (This will build the tauri and start the UI window)
```

### Using REPL
Comming soon