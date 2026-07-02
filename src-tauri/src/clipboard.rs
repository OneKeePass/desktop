// Linux-only GTK/GDK clipboard backend.
//
// On Linux the arboard-backed tauri-plugin-clipboard-manager is unreliable:
// arboard's Wayland backend needs the wlr-data-control protocol, which
// GNOME/Mutter and other non-wlroots compositors do not implement, so it falls
// back to X11 and times out ("X11 server connection timed out ... unreachable").
// The webview (WebKitGTK) itself uses GTK/GDK's own clipboard, which works on
// Wayland. So for reading and clearing we use that same GTK clipboard.
//
// GTK/GDK clipboard calls must run on the GTK main (UI) thread. Tauri runs the
// GTK main loop on the process main thread, so we marshal onto it with
// AppHandle::run_on_main_thread and hand the result back over a channel. The
// commands calling into here are async (they run on a worker thread), so the
// blocking recv never runs on the main thread and cannot deadlock.

use gtk::prelude::*;
use std::sync::mpsc;
use tauri::Runtime;

use crate::Result;

// Runs 'f' with the default GTK clipboard on the GTK main thread and returns
// its result.
fn with_clipboard<R, F, T>(app: &tauri::AppHandle<R>, f: F) -> Result<T>
where
    R: Runtime,
    F: FnOnce(&gtk::Clipboard) -> T + Send + 'static,
    T: Send + 'static,
{
    let (tx, rx) = mpsc::channel();
    app.run_on_main_thread(move || {
        let res: Result<T> = match gtk::gdk::Display::default() {
            Some(display) => match gtk::Clipboard::default(&display) {
                Some(clipboard) => Ok(f(&clipboard)),
                None => Err("No default GTK clipboard for the display".to_string()),
            },
            None => Err("No default GTK display available".to_string()),
        };
        let _ = tx.send(res);
    })
    .map_err(|e| format!("run_on_main_thread failed: {e}"))?;

    rx.recv()
        .map_err(|e| format!("clipboard main-thread channel recv failed: {e}"))?
}

// Reads the current clipboard text, if any.
pub(crate) fn get_text<R: Runtime>(app: &tauri::AppHandle<R>) -> Result<Option<String>> {
    with_clipboard(app, |clipboard| {
        clipboard.wait_for_text().map(|t| t.to_string())
    })
}

// Clears the clipboard by overwriting it with empty text. Used by the
// sensitive-data auto-clear to remove a copied password from the clipboard.
pub(crate) fn clear<R: Runtime>(app: &tauri::AppHandle<R>) -> Result<()> {
    with_clipboard(app, |clipboard| {
        clipboard.set_text("");
    })
}
