#!/bin/sh
# Installs the data files OneKeePass needs at runtime: the Tauri resources, the desktop
# entry, the AppStream metainfo, the icons and the licence.
#
# Called from the onekeepass-data module of com.onekeepass.OneKeePass.yml, and run by
# flatpak-builder with the repo as the working directory. The layout below is Tauri's
# rather than ours, which is why it is a script kept with the app instead of a list of
# install lines in the packaging manifest.
#
# FLATPAK_DEST is set to /app by flatpak-builder; the default is there so the script can
# be run against a scratch directory to check what it produces.

set -eu

DEST="${FLATPAK_DEST:-/app}"
APP_ID=com.onekeepass.OneKeePass

# tauri.conf.json gives bundle.resources as "../resources/public/<x>", relative to
# src-tauri. Tauri resolves the resource root at <exe_dir>/../lib/<productName> and
# encodes each leading ".." as "_up_", so at runtime the app reads
#   $DEST/lib/OneKeePass/_up_/resources/public/{icons,translations,wordlists}
# Flattening these into $DEST/lib/OneKeePass/ builds fine and then fails silently at
# runtime as untranslated UI. Taken from the app's own startup log:
#   Translation files root dir for i18n is
#     "/app/lib/OneKeePass/_up_/resources/public/translations"
mkdir -p "$DEST/lib/OneKeePass/_up_/resources/public"
cp -r resources/public/icons \
      resources/public/translations \
      resources/public/wordlists \
      "$DEST/lib/OneKeePass/_up_/resources/public/"

# Both are already named by the app ID, as Flathub requires.
install -Dm644 "linux/$APP_ID.desktop" "$DEST/share/applications/$APP_ID.desktop"
install -Dm644 "linux/$APP_ID.metainfo.xml" "$DEST/share/metainfo/$APP_ID.metainfo.xml"

# Each icon is installed by name rather than with `cp -r linux/icons/hicolor
# $DEST/share/icons/`: where share/icons does not exist yet, cp reads it as the
# destination NAME and renames hicolor to icons. The icons then sit outside any icon
# theme, and flatpak warns "Icon referenced in desktop file but not exported".
# install -D creates the parent directories and cannot be misread.
for size in 256x256 512x512; do
  install -Dm644 "linux/icons/hicolor/$size/apps/$APP_ID.png" \
                 "$DEST/share/icons/hicolor/$size/apps/$APP_ID.png"
done

# Flathub requires each module's licence under share/licenses.
install -Dm644 LICENSE "$DEST/share/licenses/$APP_ID/LICENSE"
