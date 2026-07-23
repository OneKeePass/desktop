// Lock-on-suspend: when the OS is about to sleep/suspend, encrypt every open
// database's decrypted content in RAM BEFORE the memory image can be written to
// a hibernation/sleep file (the primary payoff of the Phase 2 memory-security
// lock). The actual locking happens synchronously in the platform suspend
// callback so it completes before the machine sleeps; the UI is notified so the
// lock screen shows on resume.
//
// Per platform:
//   macOS   - NSWorkspace.willSleepNotification (swift-lib register_system_sleep_observer)
//   Windows - PowerRegisterSuspendResumeNotification (PBT_APMSUSPEND callback)
//   Linux   - logind PrepareForSleep signal, guarded by a "delay" inhibitor lock

use std::sync::OnceLock;

use log::{error, info};
use tauri::{AppHandle, Emitter, Manager};

use crate::constants::event_action_names::DATABASES_LOCKED;
use crate::constants::event_names::MAIN_WINDOW_EVENT;
use crate::constants::window_labels::MAIN_WINDOW_LABEL;
use crate::ssh_agent;
use onekeepass_core::db_service as kp_service;

// Set once during setup so the platform suspend callbacks (which run on
// OS-owned threads with no captured state) can reach the UI to emit the event.
static APP_HANDLE: OnceLock<AppHandle> = OnceLock::new();

#[derive(Clone, serde::Serialize)]
struct LockedEventPayload {
    action: String,
}

// Registers the platform-specific suspend listener. Safe to call once, on the
// main thread, during app setup.
pub(crate) fn start(app_handle: &AppHandle) {
    if APP_HANDLE.set(app_handle.clone()).is_err() {
        return;
    }

    #[cfg(target_os = "macos")]
    macos::start();

    #[cfg(target_os = "windows")]
    windows::start();

    #[cfg(target_os = "linux")]
    linux::start();
}

// Locks all currently-unlocked open databases in the core: encrypts the
// decrypted content in place and drops each db's SSH keys from the agent. This
// mirrors what the `lock_kdbx` command does for a single db, applied to every
// unlocked db. Returns the db keys that were locked.
fn lock_all_unlocked_dbs() -> Vec<String> {
    let keys = match kp_service::unlocked_kdbx_cache_keys() {
        Ok(keys) => keys,
        Err(e) => {
            error!("lock-on-suspend: could not list unlocked databases: {}", e);
            return Vec::new();
        }
    };

    for db_key in &keys {
        if let Err(e) = kp_service::lock_kdbx(db_key) {
            error!("lock-on-suspend: failed to lock a database: {}", e);
        }
        ssh_agent::clear_keys_for_db(db_key);
    }
    keys
}

// Called by each platform's suspend callback. Locks every open database, then
// tells the UI to show the lock screen. Runs on whatever thread the platform
// callback uses; both the core lock and the Tauri emit are safe off the main
// thread.
fn on_system_suspend() {
    let locked = lock_all_unlocked_dbs();
    if locked.is_empty() {
        return;
    }
    info!("lock-on-suspend: locked {} open database(s)", locked.len());

    if let Some(app) = APP_HANDLE.get() {
        if let Some(window) = app.get_webview_window(MAIN_WINDOW_LABEL) {
            let _ = window.emit(
                MAIN_WINDOW_EVENT,
                LockedEventPayload {
                    action: DATABASES_LOCKED.to_string(),
                },
            );
        }
    }
}

#[cfg(target_os = "macos")]
mod macos {
    // Bridges to swift-lib's register_system_sleep_observer, which adds an
    // NSWorkspace.willSleepNotification observer that invokes this callback on
    // the main run loop just before the system sleeps.
    extern "C" {
        fn register_system_sleep_observer(callback: extern "C" fn());
    }

    extern "C" fn on_sleep_callback() {
        super::on_system_suspend();
    }

    pub(super) fn start() {
        unsafe { register_system_sleep_observer(on_sleep_callback) };
    }
}

#[cfg(target_os = "windows")]
mod windows {
    use std::ffi::c_void;
    use std::ptr;

    use log::error;
    use windows_sys::Win32::System::Power::{
        PowerRegisterSuspendResumeNotification, DEVICE_NOTIFY_SUBSCRIBE_PARAMETERS,
    };

    // Deliver notifications via a callback function (vs. a window handle).
    const DEVICE_NOTIFY_CALLBACK: u32 = 2;
    // The system is suspending operation (about to sleep/hibernate).
    const PBT_APMSUSPEND: u32 = 0x0004;

    // Matches windows-sys DEVICE_NOTIFY_CALLBACK_ROUTINE: context/setting are
    // `*const c_void`. The subscribe params must stay alive for the whole
    // registration, so they are boxed-and-leaked in `start`.
    unsafe extern "system" fn power_callback(
        _context: *const c_void,
        event_type: u32,
        _setting: *const c_void,
    ) -> u32 {
        if event_type == PBT_APMSUSPEND {
            super::on_system_suspend();
        }
        // ERROR_SUCCESS
        0
    }

    pub(super) fn start() {
        // The params struct is referenced by the OS for the lifetime of the
        // registration, so it must not live only on this stack frame.
        let params = Box::new(DEVICE_NOTIFY_SUBSCRIBE_PARAMETERS {
            Callback: Some(power_callback),
            Context: ptr::null_mut(),
        });
        // Recipient is typed as HANDLE (isize in windows-sys 0.52).
        let params_ptr = Box::into_raw(params) as isize;

        // The registration handle must outlive the process (the callback fires
        // for the whole session), so it is intentionally left registered.
        // Windows grants the callback a short synchronous window to finish
        // before sleeping, which is enough to encrypt the open databases.
        // windows-sys 0.52 types the out-param as `*mut *mut c_void`.
        let mut handle: *mut c_void = ptr::null_mut();
        let status = unsafe {
            PowerRegisterSuspendResumeNotification(DEVICE_NOTIFY_CALLBACK, params_ptr, &mut handle)
        };
        if status != 0 {
            error!(
                "lock-on-suspend: PowerRegisterSuspendResumeNotification failed with {}",
                status
            );
        }
    }
}

#[cfg(target_os = "linux")]
mod linux {
    use log::{error, warn};
    use zbus::blocking::Connection;
    use zbus::zvariant::OwnedFd;

    // logind's PrepareForSleep(true) fires just before sleeping. To make sure we
    // finish encrypting before that happens, we hold a "delay" inhibitor lock:
    // logind waits (up to InhibitDelayMaxSec) for the lock to be released before
    // actually sleeping. We lock the databases, then release the fd so sleep
    // proceeds, and re-acquire the inhibitor on resume for the next cycle.
    pub(super) fn start() {
        std::thread::Builder::new()
            .name("okp-sleep-monitor".into())
            .spawn(run)
            .ok();
    }

    fn run() {
        let connection = match Connection::system() {
            Ok(c) => c,
            Err(e) => {
                warn!("lock-on-suspend: no system D-Bus, sleep locking disabled: {}", e);
                return;
            }
        };

        let proxy = match zbus::blocking::Proxy::new(
            &connection,
            "org.freedesktop.login1",
            "/org/freedesktop/login1",
            "org.freedesktop.login1.Manager",
        ) {
            Ok(p) => p,
            Err(e) => {
                warn!("lock-on-suspend: could not reach logind, sleep locking disabled: {}", e);
                return;
            }
        };

        let mut inhibitor: Option<OwnedFd> = take_inhibitor(&proxy);

        let signal = match proxy.receive_signal("PrepareForSleep") {
            Ok(s) => s,
            Err(e) => {
                error!("lock-on-suspend: could not subscribe to PrepareForSleep: {}", e);
                return;
            }
        };

        for message in signal {
            let about_to_sleep: bool = match message.body().deserialize() {
                Ok(v) => v,
                Err(e) => {
                    error!("lock-on-suspend: bad PrepareForSleep payload: {}", e);
                    continue;
                }
            };

            if about_to_sleep {
                super::on_system_suspend();
                // Releasing the inhibitor fd lets logind proceed with the sleep.
                inhibitor = None;
            } else {
                // Resumed: re-arm the inhibitor for the next sleep.
                inhibitor = take_inhibitor(&proxy);
            }
        }
    }

    fn take_inhibitor(proxy: &zbus::blocking::Proxy) -> Option<OwnedFd> {
        match proxy.call::<_, _, OwnedFd>(
            "Inhibit",
            &(
                "sleep",
                "OneKeePass",
                "Encrypting open databases before sleep",
                "delay",
            ),
        ) {
            Ok(fd) => Some(fd),
            Err(e) => {
                warn!("lock-on-suspend: could not take logind sleep inhibitor: {}", e);
                None
            }
        }
    }
}
