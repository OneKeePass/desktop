# Flatpak packaging

Files here build OneKeePass as a Flatpak for Flathub. The app ID is
`com.onekeepass.OneKeePass`.

The manifest itself is one level up, at `desktop/com.onekeepass.OneKeePass.yml`.
`--sandbox` — the mode Flathub's buildbot builds in — rejects any source outside the
manifest's own directory, and the manifest has to reach `src-tauri`, `src-cljs`,
`onekeepass-proxy` and `resources`. The Flathub manifest satisfies that rule by
pulling the whole repo as a single git source instead — see *The Flathub manifest*
below.

| File | What it is |
| --- | --- |
| `com.onekeepass.OneKeePass.metainfo.xml` | AppStream metadata; mandatory for Flathub |
| `com.onekeepass.OneKeePass.desktop` | desktop entry |
| `icons/hicolor/**` | icons, named by app ID |
| `flatpak-maven-generator.py` | generates `maven-sources.json` |
| `make-flathub-manifest.py` | derives the Flathub manifest from the one above |
| `cargo-sources.json` | generated — vendored crates for the app |
| `proxy-cargo-sources.json` | generated — vendored crates for the proxy sidecar |
| `node-sources.json` | generated — npm tarballs as a yarn offline mirror |
| `maven-sources.json` | generated — Maven artifacts as a populated local repository |

## Why the four generated lists exist

Flathub builds with no network. Every dependency has to arrive as a source declared
in the manifest, with a checksum, so the four lists are what stands in for
`cargo build`, `yarn install` and shadow-cljs each reaching out to their registries.

They are generated, not written by hand, and they must be regenerated whenever the
lockfile they came from changes. A stale list does not fail loudly at generation
time — it fails as a missing crate or an unresolvable jar in the middle of a
25-minute build.

## Regenerating

Three of the four come from upstream tooling in
[flatpak-builder-tools](https://github.com/flatpak/flatpak-builder-tools). Its
scripts need `aiohttp`, `PyYAML` and `tomlkit`:

```sh
python3 -m venv ~/.venvs/flatpak-tools
~/.venvs/flatpak-tools/bin/pip install "aiohttp<4" "PyYAML<7" "tomlkit<1"
```

Run all four from the `desktop` directory (where the manifest also lives):

```sh
FBT=/path/to/flatpak-builder-tools

# after src-tauri/Cargo.lock changes
~/.venvs/flatpak-tools/bin/python $FBT/cargo/flatpak-cargo-generator.py \
  src-tauri/Cargo.lock -o linux/cargo-sources.json

# after onekeepass-proxy/Cargo.lock changes
~/.venvs/flatpak-tools/bin/python $FBT/cargo/flatpak-cargo-generator.py \
  onekeepass-proxy/Cargo.lock -o linux/proxy-cargo-sources.json

# after yarn.lock changes
PYTHONPATH=$FBT/node ~/.venvs/flatpak-tools/bin/python -m flatpak_node_generator \
  yarn yarn.lock -o linux/node-sources.json

# after src-cljs/shadow-cljs.edn :dependencies change
python3 linux/flatpak-maven-generator.py
```

## The Maven list

There is no upstream Maven generator, so `flatpak-maven-generator.py` is ours.

`src-cljs/.shadow-cljs/classpath.edn` looks like the answer and is not: it lists only
the 58 jars that reach the classpath, while offline resolution also needs the 113 poms
Aether reads to rebuild the dependency graph — parent and BOM poms such as
`jackson-bom` and `commons-parent`, whose jars are never used. Its paths are also
absolute into whichever machine last ran a build.

So the generator resolves for real, into an empty local repository, and lists whatever
that produced. It then cross-checks its jars against `classpath.edn` and reports
anything missing.

Two details worth knowing before changing it:

- **`HOME` does not decide where Maven artifacts land.** shadow-cljs resolves through
  pomegranate/Aether, which takes the default repository path from the JVM's
  `user.home` — and the JVM reads that from the OS, not the environment. Setting
  `HOME` and expecting an empty `~/.m2` silently resolves against the developer's real
  one instead, producing a list that looks complete and is not. Both the generator and
  the manifest set `:maven {:local-repo ...}` explicitly for this reason.
- **The `_remote.repositories` files are vendored too.** Aether's enhanced local
  repository manager uses them to confirm an artifact came from a configured
  repository, and re-fetches anything it cannot confirm — which offline means a failed
  build. They are emitted with their timestamp comments stripped so regeneration is
  stable.

## Building and testing

```sh
flatpak run org.flatpak.Builder --force-clean --user --install build-dir \
  com.onekeepass.OneKeePass.yml
flatpak run com.onekeepass.OneKeePass
```

To check it builds the way Flathub's buildbot will — no network, no download cache:

```sh
flatpak run org.flatpak.Builder --download-only build-dir \
  com.onekeepass.OneKeePass.yml
flatpak run org.flatpak.Builder --force-clean --sandbox --disable-download \
  --user --install build-dir com.onekeepass.OneKeePass.yml
```

Do not toggle `--ccache` between runs. flatpak-builder prunes cache stages it no
longer references after a successful build, so switching it on or off costs two full
rebuilds rather than one.

## The Flathub manifest

The manifest published on Flathub is not maintained by hand. It differs from
`desktop/com.onekeepass.OneKeePass.yml` in exactly one way — each module's
`type: dir` / `type: file` sources become one `type: git` source pinned to a tag — so
it is derived, and the two cannot drift:

```sh
python3 linux/make-flathub-manifest.py --tag v0.25.1 --out-dir /tmp/flathub-pr
```

That writes what the Flathub repo holds, and nothing else:

```
com.onekeepass.OneKeePass.yml
flathub.json                  # only-arches: [x86_64]
linux/cargo-sources.json
linux/proxy-cargo-sources.json
linux/node-sources.json
linux/maven-sources.json
```

The four lists are copied rather than fetched with the git source because a source
list is read relative to the **manifest's** repo. Regenerate them here, then re-run
the script and copy its output across. The script also appends the trailing newline
that the upstream cargo and node generators omit, so regenerating them does not show
up in git as a changed last line.

### Comments: `#` ships, `##` does not

Half this manifest is comments, and that is deliberate — it is where the reasoning
behind the packaging is written down. The Flathub copy is a packaging file, not a
document, and Flathub neither requires nor expects comments; most published manifests
have almost none. So the two are marked apart:

- `#` — kept in both. The minimum that stops a future maintainer breaking something:
  why a permission exists, and why a build step is not what it looks like.
- `##` — kept here only. Everything else: rationale, dates, the Fedora VM, the Mac
  toolchain, `just` recipes, `file.rs:123` references.

When a block is half and half, split it — the short version under `#`, the full
reasoning beneath it under `##`. The `#` line is what a reviewer sees, so it has to
stand on its own.

If a reviewer asks why something is the way it is, the answer is already written under
a `##` here; promote it to `#` and regenerate.

The script refuses to run unless the tag exists **and is already pushed**. That is
not pedantry: Flathub downloads the AppStream screenshot URLs at build time and they
are pinned to the same tag, so generating against a local-only tag produces a
manifest that fails to build for everyone but you.

Never move or delete a tag Flathub has built from. It rebuilds by itself when the
runtime updates, and re-reads both the tag and those URLs. If review asks for
changes, cut a new version.
