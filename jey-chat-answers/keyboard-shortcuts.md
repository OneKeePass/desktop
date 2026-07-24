# Keyboard shortcuts in OneKeePass

## Why native menu shortcuts stopped working on Windows

The shortcut strings in `src-tauri/src/menu.rs` are valid. In particular,
`CmdOrControl` is accepted by the pinned `muda` parser and becomes Command on
macOS and Control on Windows and Linux.

Native menu accelerators follow this path on macOS and Linux:

```text
Key press
  -> platform-native menu accelerator
  -> Tauri/muda MenuEvent
  -> main.rs on_menu_event callback
  -> menu::handle_menu_events
  -> TauriMenuEvent sent to ClojureScript
  -> frontend action
```

Windows has an additional platform-specific dependency: its event loop must
pass the keyboard message and the menu accelerator table to the Win32
`TranslateAcceleratorW` function. In the currently resolved Tauri/WebView2
combination, shortcuts pressed while WebView2 has focus are not reaching that
native translation path. Menu clicks still work because they directly produce
the native menu command.

The entry shortcuts such as Ctrl+B, Ctrl+C, and Ctrl+T continued to work because
they use a ClojureScript `document` keydown handler. They do not depend on the
Windows native menu accelerator path.

The fact that native shortcuts previously worked on Windows suggests a
regression in the Tauri, `muda`, `tao`, or WebView2 dependency chain. Finding
the exact regression would require testing older lockfiles or releases on
Windows and bisecting those dependency versions.

## Current Windows fallback

OneKeePass now handles system-menu combinations through the webview keydown
listener on Windows only. The frontend maps the combination to a menu ID and
asks Rust to activate it. Rust checks that the corresponding native menu item
is currently enabled before emitting the same `TauriMenuEvent` as a menu click.

This preserves native menu availability rules and leaves macOS and Linux on
their existing native accelerator paths. The contextual entry shortcuts remain
webview-handled on every platform because they must avoid interfering with text
editing and selections.

## Recommended design for user-configurable shortcuts

The webview keydown approach is the better foundation for shortcuts users can
define or change. It supports:

- loading mappings dynamically from preferences;
- changing mappings without rebuilding the application;
- context rules for text fields, dialogs, and selections;
- conflict detection for combinations such as Ctrl+C;
- consistent behavior across Windows, macOS, and Linux;
- possible multi-key sequences in the future.

The suggested architecture is:

```text
User shortcut preferences
  -> shared shortcut registry
  -> webview keydown handler
  -> menu/action ID
  -> Rust enabled-state check
  -> existing TauriMenuEvent dispatcher
  -> application action
```

Native menu accelerators should still be synchronized with the preferences so
menus display the user's current bindings. The webview registry should be the
authoritative runtime dispatcher, while the native accelerator is primarily a
menu hint and platform integration.

The global-shortcut plugin is not appropriate for ordinary menu actions. A
global shortcut may fire while OneKeePass is not focused, which is undesirable
for Save, New Entry, or Lock Database. Reserve global shortcuts for actions the
user explicitly expects to work system-wide, such as showing OneKeePass or
opening a future quick-access window.
