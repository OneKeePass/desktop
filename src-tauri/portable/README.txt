OneKeePass - Windows Portable Version
=====================================

This is the portable version of OneKeePass. No installation is required and
the application keeps all of its data inside this folder.

Getting started
---------------
1. Copy or unzip this whole folder to any writable location - for example a
   USB drive or a folder under your Documents.
2. Run OneKeePass.exe

Requirement: Microsoft Edge WebView2 Runtime
--------------------------------------------
OneKeePass uses the WebView2 Evergreen Runtime, which is preinstalled on
current Windows 10 and Windows 11 systems. If OneKeePass does not start and
reports that WebView2 is missing, install it from:

  https://developer.microsoft.com/en-us/microsoft-edge/webview2/

Where your data is kept
-----------------------
All application data - preferences, logs, backups and word lists - is stored
in the 'onekeepass-data' folder next to OneKeePass.exe. It is created on the
first run. Deleting this folder removes all traces of your OneKeePass usage
(your password database .kdbx files are separate and stay wherever you saved
them).

Files in this folder
--------------------
  OneKeePass.exe        The application
  onekeepass-proxy.exe  Helper used only for the browser extension
  .portable             Marker file that enables portable mode - do NOT
                        delete it. Without it the app stores its data under
                        %USERPROFILE%\.onekeepass like the installed version
  _up_\                 Bundled application resources - do NOT delete
  onekeepass-data\      Your application data (created on first run)

Updating to a new version
-------------------------
Download the new portable zip and replace OneKeePass.exe,
onekeepass-proxy.exe and the '_up_' folder. Keep your 'onekeepass-data'
folder as it is.

Notes and limitations
---------------------
- If this folder is in a read-only location (for example 'C:\Program Files'
  or a write-protected USB drive), OneKeePass shows an error and exits.
  Move the folder to a writable location and start it again.
- The 'Recent files' list stores full file paths. If the drive letter of
  your USB drive changes on another computer, entries in that list may not
  open until you open the database file again manually.
- Enabling the browser extension integration writes a small configuration
  file and registry entries on the computer you are using (browsers require
  this). This is the only OneKeePass setting that leaves data on the host
  machine, and it also records the current location of onekeepass-proxy.exe.
  If your drive letter changes later, enable the browser integration again.
- While viewing an entry attachment, a temporary copy is placed in the
  Windows temp folder. It is removed when the app starts and when it quits.
