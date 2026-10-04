# Flatpak packaging

Files here build OneKeePass as a Flatpak for Flathub. The app ID is
`com.onekeepass.OneKeePass`.

The manifest itself is one level up, at `desktop/com.onekeepass.OneKeePass.yml`.
`--sandbox` — the mode Flathub's buildbot builds in — rejects any source outside the
manifest's own directory, and the manifest has to reach `src-tauri`, `src-cljs`,
`onekeepass-proxy` and `resources`. The Flathub manifest satisfies that rule by
pulling the whole repo as a single git source instead; `make-flathub-manifest.py`
derives it.

When editing the manifest, note that the two comment markers mean different things.
`#` comments are carried into the Flathub copy; `##` comments are stripped from it by
`make-flathub-manifest.py`. 

So `#` holds the minimum a reviewer or future maintainer
needs — why a permission exists, why a build step is not what it looks like — and `##`
holds everything else: rationale, dates, local toolchain and VM details, `file.rs:123`
references. Split a block that is both, rather than losing half of it either way.

| File | What it is |
| --- | --- |
| `com.onekeepass.OneKeePass.metainfo.xml` | AppStream metadata; mandatory for Flathub |
| `com.onekeepass.OneKeePass.desktop` | desktop entry |
| `icons/hicolor/**` | icons, named by app ID |
| `flatpak-install-data.sh` | installs resources, desktop entry, metainfo, icons, the wrapper and licence into /app |
| `flatpak-onekeepass-wrapper.sh` | the Flatpak's `command:`; sends a browser's native messaging launch to the proxy, everything else to the app |
| `flatpak-maven-generator.py` | generates `maven-sources.json` |
| `make-flathub-manifest.py` | derives the Flathub manifest from the one above |
| `cargo-sources.json` | generated — vendored crates for the app |
| `proxy-cargo-sources.json` | generated — vendored crates for the proxy sidecar |
| `node-sources.json` | generated — npm tarballs as a yarn offline mirror |
| `maven-sources.json` | generated — Maven artifacts as a populated local repository |
