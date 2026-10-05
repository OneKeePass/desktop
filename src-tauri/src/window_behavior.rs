// "Send window to background" after copying to the clipboard (issue #94).
//
// No platform lets an app simply lower itself and pass focus on in one call.
// What works is handing focus to the window directly behind OneKeePass in the
// stacking order, which is the window the user was in before switching to
// OneKeePass. The OS raises that window above us and OneKeePass stays visible
// behind it instead of being minimized.
//
// - macOS: CGWindowList (front-to-back) + NSRunningApplication.activate, in
//   swift-lib/src/WindowBehavior.swift
// - Windows: walk GW_HWNDNEXT below our window + SetForegroundWindow, allowed
//   because our process is the foreground one at that moment
// - Linux X11: _NET_CLIENT_LIST_STACKING + a _NET_ACTIVE_WINDOW client message
// - Linux Wayland: not possible. No protocol lets one client activate another
//   client's window, so is_supported() returns false and the settings UI shows
//   the option disabled.
//
// When OneKeePass is not the foreground app, or no suitable window is found,
// nothing is changed.

use tauri::Runtime;

use crate::Result;

// Whether this session can send the window to the background. Called off the
// main thread (on Linux it waits for the GTK main thread to answer).
pub(crate) fn is_supported<R: Runtime>(app: &tauri::AppHandle<R>) -> bool {
    platform::is_supported(app)
}

pub(crate) fn send_to_background<R: Runtime>(app: &tauri::AppHandle<R>) -> Result<()> {
    if !platform::is_supported(app) {
        log::debug!("Send window to background is not supported in this session");
        return Ok(());
    }

    if !platform::send_to_background()? {
        log::debug!("No window found to hand focus to; window left as is");
    }
    Ok(())
}

#[cfg(target_os = "macos")]
mod platform {
    use swift_rs::{swift, Bool};
    use tauri::Runtime;

    use crate::Result;

    swift!(fn window_send_to_background() -> Bool);

    pub(super) fn is_supported<R: Runtime>(_app: &tauri::AppHandle<R>) -> bool {
        true
    }

    pub(super) fn send_to_background() -> Result<bool> {
        Ok(unsafe { window_send_to_background() })
    }
}

#[cfg(target_os = "windows")]
mod platform {
    use std::ffi::c_void;

    use tauri::Runtime;
    use windows_sys::Win32::Foundation::HWND;
    use windows_sys::Win32::Graphics::Dwm::{DwmGetWindowAttribute, DWMWA_CLOAKED};
    use windows_sys::Win32::System::Threading::GetCurrentProcessId;
    use windows_sys::Win32::UI::WindowsAndMessaging::{
        GetAncestor, GetClassNameW, GetForegroundWindow, GetWindow, GetWindowLongW,
        GetWindowTextLengthW, GetWindowThreadProcessId, IsIconic, IsWindowVisible,
        SetForegroundWindow, GA_ROOTOWNER, GWL_EXSTYLE, GW_HWNDNEXT, GW_OWNER, WS_EX_APPWINDOW,
        WS_EX_NOACTIVATE, WS_EX_TOOLWINDOW,
    };

    use crate::Result;

    pub(super) fn is_supported<R: Runtime>(_app: &tauri::AppHandle<R>) -> bool {
        true
    }

    pub(super) fn send_to_background() -> Result<bool> {
        unsafe {
            let foreground = GetForegroundWindow();
            if foreground == 0 {
                return Ok(false);
            }

            // A dialog of ours may be the foreground window; start from its
            // top-level owner so the search begins below the main window
            let own = match GetAncestor(foreground, GA_ROOTOWNER) {
                0 => foreground,
                root => root,
            };

            let own_pid = GetCurrentProcessId();
            if window_pid(own) != own_pid {
                // The user has already moved to another app
                return Ok(false);
            }

            let mut hwnd = GetWindow(own, GW_HWNDNEXT);
            while hwnd != 0 {
                // Everything below the desktop window is not a candidate
                if is_desktop(hwnd) {
                    break;
                }
                if window_pid(hwnd) != own_pid && is_switchable(hwnd) {
                    return Ok(SetForegroundWindow(hwnd) != 0);
                }
                hwnd = GetWindow(hwnd, GW_HWNDNEXT);
            }
            Ok(false)
        }
    }

    unsafe fn window_pid(hwnd: HWND) -> u32 {
        let mut pid: u32 = 0;
        GetWindowThreadProcessId(hwnd, &mut pid);
        pid
    }

    // Roughly the windows that Alt+Tab would offer
    unsafe fn is_switchable(hwnd: HWND) -> bool {
        if IsWindowVisible(hwnd) == 0 || IsIconic(hwnd) != 0 {
            return false;
        }

        let ex_style = GetWindowLongW(hwnd, GWL_EXSTYLE) as u32;
        if ex_style & (WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE) != 0 {
            return false;
        }
        if GetWindow(hwnd, GW_OWNER) != 0 && ex_style & WS_EX_APPWINDOW == 0 {
            return false;
        }
        if GetWindowTextLengthW(hwnd) == 0 {
            return false;
        }

        // Cloaked windows are on another virtual desktop or are suspended UWP
        // app windows; they report visible but are not shown
        let mut cloaked: u32 = 0;
        let hr = DwmGetWindowAttribute(
            hwnd,
            DWMWA_CLOAKED as _,
            &mut cloaked as *mut u32 as *mut c_void,
            std::mem::size_of::<u32>() as u32,
        );
        !(hr == 0 && cloaked != 0)
    }

    unsafe fn is_desktop(hwnd: HWND) -> bool {
        let mut buf = [0u16; 32];
        let len = GetClassNameW(hwnd, buf.as_mut_ptr(), buf.len() as i32);
        if len <= 0 {
            return false;
        }
        let class = String::from_utf16_lossy(&buf[..len as usize]);
        class == "Progman" || class == "WorkerW"
    }
}

#[cfg(target_os = "linux")]
mod platform {
    use std::sync::{mpsc, OnceLock};

    use gtk::prelude::*;
    use tauri::Runtime;
    use x11rb::connection::Connection;
    use x11rb::protocol::xproto::{
        Atom, AtomEnum, ClientMessageEvent, ConnectionExt, EventMask, Window,
    };

    use crate::Result;

    x11rb::atom_manager! {
        Atoms: AtomsCookie {
            _NET_ACTIVE_WINDOW,
            _NET_CLIENT_LIST_STACKING,
            _NET_CURRENT_DESKTOP,
            _NET_WM_DESKTOP,
            _NET_WM_PID,
            _NET_WM_STATE,
            _NET_WM_STATE_HIDDEN,
            _NET_WM_WINDOW_TYPE,
            _NET_WM_WINDOW_TYPE_NORMAL,
            _NET_WM_WINDOW_TYPE_DIALOG,
        }
    }

    // _NET_WM_DESKTOP value for windows shown on all desktops
    const ALL_DESKTOPS: u32 = 0xFFFF_FFFF;

    // _NET_ACTIVE_WINDOW source indication for pagers/taskbars. Window managers
    // apply focus-stealing prevention to source 1 (application) requests but
    // honour source 2, which is what a taskbar click sends.
    const SOURCE_PAGER: u32 = 2;

    // GDK chooses the backend at startup (Wayland when available unless
    // GDK_BACKEND says otherwise), so ask GDK which display it opened rather
    // than guessing from environment variables. Cached as it cannot change
    // while the app runs.
    pub(super) fn is_supported<R: Runtime>(app: &tauri::AppHandle<R>) -> bool {
        static X11: OnceLock<bool> = OnceLock::new();
        *X11.get_or_init(|| gdk_display_is_x11(app))
    }

    // GDK calls must run on the GTK main thread; see crate::clipboard
    fn gdk_display_is_x11<R: Runtime>(app: &tauri::AppHandle<R>) -> bool {
        let (tx, rx) = mpsc::channel();
        let sent = app.run_on_main_thread(move || {
            let x11 = gtk::gdk::Display::default()
                .map(|d| d.type_().name().contains("X11"))
                .unwrap_or(false);
            let _ = tx.send(x11);
        });
        if let Err(e) = sent {
            log::error!("run_on_main_thread failed while checking the GDK backend: {e}");
            return false;
        }
        rx.recv().unwrap_or(false)
    }

    pub(super) fn send_to_background() -> Result<bool> {
        send_to_background_x11().map_err(|e| format!("Send window to background failed: {e}"))
    }

    fn send_to_background_x11() -> std::result::Result<bool, Box<dyn std::error::Error>> {
        let (conn, screen_num) = x11rb::connect(None)?;
        let root = conn.setup().roots[screen_num].root;
        let atoms = Atoms::new(&conn)?.reply()?;
        let own_pid = std::process::id();

        let Some(active) = property_u32s(&conn, root, atoms._NET_ACTIVE_WINDOW, AtomEnum::WINDOW)?
            .first()
            .copied()
        else {
            log::debug!("X11: root window has no _NET_ACTIVE_WINDOW");
            return Ok(false);
        };

        let active_pid = window_pid(&conn, &atoms, active)?;
        if active_pid != Some(own_pid) {
            // The user has already moved to another app
            log::debug!(
                "X11: active window {active:#x} has pid {active_pid:?}, not ours ({own_pid})"
            );
            return Ok(false);
        }

        // Bottom-to-top order
        let stacking = property_u32s(
            &conn,
            root,
            atoms._NET_CLIENT_LIST_STACKING,
            AtomEnum::WINDOW,
        )?;
        let Some(active_index) = stacking.iter().position(|w| *w == active) else {
            log::debug!("X11: active window {active:#x} is not in _NET_CLIENT_LIST_STACKING");
            return Ok(false);
        };
        log::debug!(
            "X11: {} windows below ours in _NET_CLIENT_LIST_STACKING (bottom-to-top: {:x?})",
            active_index,
            &stacking[..active_index]
        );

        let current_desktop =
            property_u32s(&conn, root, atoms._NET_CURRENT_DESKTOP, AtomEnum::CARDINAL)?
                .first()
                .copied();

        for &candidate in stacking[..active_index].iter().rev() {
            // A window can close while we look at it; its property queries then
            // fail with BadWindow, so treat that as not a candidate
            let wanted = window_pid(&conn, &atoms, candidate)
                .and_then(|pid| {
                    if pid == Some(own_pid) {
                        Ok(false)
                    } else {
                        is_switchable(&conn, &atoms, candidate, current_desktop)
                    }
                })
                .unwrap_or(false);
            if !wanted {
                log::debug!("X11: skipping window {candidate:#x}");
                continue;
            }

            let event = ClientMessageEvent::new(
                32,
                candidate,
                atoms._NET_ACTIVE_WINDOW,
                [SOURCE_PAGER, x11rb::CURRENT_TIME, active, 0, 0],
            );
            conn.send_event(
                false,
                root,
                EventMask::SUBSTRUCTURE_REDIRECT | EventMask::SUBSTRUCTURE_NOTIFY,
                event,
            )?;
            conn.flush()?;
            log::debug!("X11: sent _NET_ACTIVE_WINDOW for window {candidate:#x}");
            return Ok(true);
        }

        Ok(false)
    }

    fn is_switchable(
        conn: &impl Connection,
        atoms: &Atoms,
        window: Window,
        current_desktop: Option<u32>,
    ) -> std::result::Result<bool, Box<dyn std::error::Error>> {
        // Minimized windows are left alone
        let states = property_u32s(conn, window, atoms._NET_WM_STATE, AtomEnum::ATOM)?;
        if states.contains(&atoms._NET_WM_STATE_HIDDEN) {
            return Ok(false);
        }

        // Windows on another workspace are not visible behind us
        let desktop = property_u32s(conn, window, atoms._NET_WM_DESKTOP, AtomEnum::CARDINAL)?
            .first()
            .copied();
        if let (Some(d), Some(current)) = (desktop, current_desktop) {
            if d != ALL_DESKTOPS && d != current {
                return Ok(false);
            }
        }

        // Skip docks, panels, desktop and similar. No type set means normal.
        let types = property_u32s(conn, window, atoms._NET_WM_WINDOW_TYPE, AtomEnum::ATOM)?;
        Ok(types.is_empty()
            || types.contains(&atoms._NET_WM_WINDOW_TYPE_NORMAL)
            || types.contains(&atoms._NET_WM_WINDOW_TYPE_DIALOG))
    }

    fn window_pid(
        conn: &impl Connection,
        atoms: &Atoms,
        window: Window,
    ) -> std::result::Result<Option<u32>, Box<dyn std::error::Error>> {
        Ok(
            property_u32s(conn, window, atoms._NET_WM_PID, AtomEnum::CARDINAL)?
                .first()
                .copied(),
        )
    }

    fn property_u32s(
        conn: &impl Connection,
        window: Window,
        property: Atom,
        type_: AtomEnum,
    ) -> std::result::Result<Vec<u32>, Box<dyn std::error::Error>> {
        let reply = conn
            .get_property(false, window, property, type_, 0, u32::MAX)?
            .reply()?;
        Ok(reply.value32().map(|v| v.collect()).unwrap_or_default())
    }
}
