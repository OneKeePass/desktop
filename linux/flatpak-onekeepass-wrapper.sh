#!/bin/sh
# Entry point for the Flatpak, installed as /app/bin/onekeepass-wrapper and named as the
# manifest's `command:`.
#
# A Flatpak has exactly one command, but two programs have to be reachable through it: the
# app, and onekeepass-proxy. The proxy is launched by a browser running outside this
# sandbox, which can only reach us through the launcher Flatpak exports onto the host
# (/var/lib/flatpak/exports/bin/com.onekeepass.OneKeePass). That launcher runs
# `flatpak run <app-id>` and forwards the browser's arguments here, so the browser's own
# argv is what tells the two cases apart.
#
# Chromium-based browsers pass the caller's origin as the first argument:
#   chrome-extension://<id>/
# Firefox passes the manifest path first and the extension id second. Neither shape can be
# produced by launching the app normally, including with a database path to open.
#
# Written as a native messaging manifest path by native_messaging_config.rs.

case "$1" in
  chrome-extension://*)
    exec onekeepass-proxy "$@"
    ;;
esac

if [ "$2" = "onekeepass@gmail.com" ]; then
  exec onekeepass-proxy "$@"
fi

exec OneKeePass "$@"
