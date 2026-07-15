use std::{
    fs,
    path::{Path, PathBuf},
};

use crate::file_util;
use tauri::{path::BaseDirectory, Manager, Runtime};

// Should be called on app startup so that all app dirs are created
pub(crate) fn init_app_paths() {
    let app_dir = app_home_dir();
    let log_dir = app_logs_dir();
    let backups_dir = app_backup_dir();
    let word_list_dir = wordlists_dir();

    if !app_dir.exists() {
        let _ = fs::create_dir_all(&app_dir);
    }

    if !backups_dir.exists() {
        let _ = fs::create_dir_all(&backups_dir);
    } else {
        // Internal app-home backups are transient and should not survive app restarts.
        let _r = file_util::remove_dir_files(&backups_dir);
    }

    if !log_dir.exists() {
        let _ = fs::create_dir_all(&log_dir);
    } else {
        // Sweep legacy per-launch timestamped log files (format
        // "YYYY-MM-DD-HHMMSS.log") written by versions prior to the
        // size-rotated logging scheme. Active rotation files
        // (onekeepass[-dev].log, onekeepass[-dev].N.log) are preserved
        // so rotation history survives across launches.
        remove_legacy_timestamped_logs(&log_dir);
    }

    if !word_list_dir.exists() {
        if let Err(e) = fs::create_dir_all(&word_list_dir) {
            log::error!(
                "Creating dir {:?} failed with error: {} ",
                &word_list_dir,
                e
            );
        }
    }
}

fn remove_legacy_timestamped_logs(log_dir: &Path) {
    let Ok(entries) = fs::read_dir(log_dir) else {
        return;
    };
    for entry in entries.flatten() {
        if !entry
            .file_type()
            .map(|t| t.is_file())
            .unwrap_or(false)
        {
            continue;
        }
        let name = entry.file_name();
        let Some(name_str) = name.to_str() else {
            continue;
        };
        if is_legacy_timestamped_log_name(name_str) {
            let _ = fs::remove_file(entry.path());
        }
    }
}

fn is_legacy_timestamped_log_name(name: &str) -> bool {
    let Some(stem) = name.strip_suffix(".log") else {
        return false;
    };
    if stem.len() != 17 {
        return false;
    }
    let dash_positions = [4usize, 7, 10];
    for (i, b) in stem.bytes().enumerate() {
        if dash_positions.contains(&i) {
            if b != b'-' {
                return false;
            }
        } else if !b.is_ascii_digit() {
            return false;
        }
    }
    true
}

// Portable mode (Windows only). When a marker file '.portable' is found next to
// the exe, all app data (preference.toml, logs, backups, wordlists) is kept in
// 'onekeepass-data' beside the exe instead of ~/.onekeepass. The portable zip
// distribution ships with this marker; the MSI install does not.
#[cfg(target_os = "windows")]
const PORTABLE_MARKER_FILE: &str = ".portable";
#[cfg(target_os = "windows")]
const PORTABLE_DATA_DIR: &str = "onekeepass-data";

static PORTABLE_DATA_HOME: std::sync::OnceLock<Option<PathBuf>> = std::sync::OnceLock::new();

// Not enabled on macOS (exe lives inside the read-only .app bundle) or
// Linux (AppImage mounts are read-only)
#[cfg(target_os = "windows")]
fn detect_portable_data_home() -> Option<PathBuf> {
    let exe = std::env::current_exe().ok()?;
    let exe_dir = exe.parent()?;
    if exe_dir.join(PORTABLE_MARKER_FILE).exists() {
        Some(exe_dir.join(PORTABLE_DATA_DIR))
    } else {
        None
    }
}

#[cfg(not(target_os = "windows"))]
fn detect_portable_data_home() -> Option<PathBuf> {
    None
}

fn portable_data_home() -> Option<&'static PathBuf> {
    PORTABLE_DATA_HOME.get_or_init(detect_portable_data_home).as_ref()
}

pub(crate) fn is_portable() -> bool {
    portable_data_home().is_some()
}

// In portable mode the data dir must be created next to the exe. The user may
// have unzipped into a read-only location (e.g 'C:\Program Files'); dir creation
// alone can appear to succeed there, so a probe file write confirms writability.
// The caller shows the returned message and exits instead of silently falling
// back to ~/.onekeepass, which would leave traces on the host machine
pub(crate) fn verify_portable_writable() -> Result<(), String> {
    let Some(data_dir) = portable_data_home() else {
        return Ok(());
    };

    let not_writable_msg = || {
        format!(
            "OneKeePass is running in portable mode but cannot write to {}. \
             Move the OneKeePass folder to a writable location (for example a USB drive \
             or your Documents folder) and start it again.",
            data_dir.display()
        )
    };

    fs::create_dir_all(data_dir).map_err(|_| not_writable_msg())?;

    let probe_file = data_dir.join(".write-test");
    fs::write(&probe_file, b"").map_err(|_| not_writable_msg())?;
    let _ = fs::remove_file(&probe_file);

    Ok(())
}

// IMPORTANT: unwrap() is used. What is the alternative ?
pub(crate) fn app_home_dir() -> PathBuf {
    // The runtime portable marker takes precedence over the onekeepass-dev feature
    if let Some(p) = portable_data_home() {
        return p.clone();
    }

    #[cfg(not(feature = "onekeepass-dev"))]
    let p = std::env::home_dir()
        .unwrap()
        .join(Path::new(".onekeepass"))
        .join(Path::new("prod"));

    // To activate this feature during development, we need to use 'cargo tauri dev -f onekeepass-dev'
    #[cfg(feature = "onekeepass-dev")]
    let p = std::env::home_dir()
        .unwrap()
        .join(Path::new(".onekeepass"))
        .join(Path::new("dev"));
    p
}

// All fns getting app dir should be called only after 'init_app_paths' is called

pub(crate) fn app_logs_dir() -> PathBuf {
    app_home_dir().join("logs")
}

pub(crate) fn app_backup_dir() -> PathBuf {
    app_home_dir().join("backups")
}

pub(crate) fn wordlists_dir() -> PathBuf {
    app_home_dir().join("wordlists")
}

#[allow(dead_code)]
pub fn create_sub_dir_path<P: AsRef<Path>>(root_dir: P, sub: &str) -> PathBuf {
    // Initialize with the root_dir itself
    let mut final_full_path_dir = Path::new(root_dir.as_ref()).to_path_buf();

    let full_path_dir = Path::new(root_dir.as_ref()).join(sub);

    if !full_path_dir.exists() {
        if let Err(e) = std::fs::create_dir_all(&full_path_dir) {
            // This should not happen!
            log::error!(
                "Directory at {} creation failed {:?}",
                &full_path_dir.display(),
                e
            );
        } else {
            // As fallback use the full_path_dir of root_dir
            final_full_path_dir = full_path_dir;
        }
    } else {
        final_full_path_dir = full_path_dir;
    }
    final_full_path_dir
}

pub(crate) fn app_resources_dir<R: Runtime>(app: &tauri::AppHandle<R>) -> Result<String, String> {
    if let Ok(path) = app
        .path()
        .resolve("../resources/public/", BaseDirectory::Resource)
    {
        path.as_path()
            .to_str()
            .map(|s| s.into())
            .ok_or("Could not resolve resource public dir".into())
    } else {
        Err("Could not resolve resource public dir".into())
    }
}

#[cfg(test)]
mod tests {
    use super::is_legacy_timestamped_log_name;

    #[test]
    fn legacy_name_matches_timestamp_pattern() {
        assert!(is_legacy_timestamped_log_name("2026-05-26-143022.log"));
        assert!(is_legacy_timestamped_log_name("2024-01-01-000000.log"));
    }

    #[test]
    fn legacy_name_rejects_rotation_files() {
        assert!(!is_legacy_timestamped_log_name("onekeepass.log"));
        assert!(!is_legacy_timestamped_log_name("onekeepass.1.log"));
        assert!(!is_legacy_timestamped_log_name("onekeepass.2.log"));
        assert!(!is_legacy_timestamped_log_name("onekeepass-dev.log"));
        assert!(!is_legacy_timestamped_log_name("onekeepass-dev.1.log"));
    }

    #[test]
    fn legacy_name_rejects_non_matching_shapes() {
        assert!(!is_legacy_timestamped_log_name("2026-05-26.log")); // too short
        assert!(!is_legacy_timestamped_log_name("2026-05-26-143022.txt")); // wrong ext
        assert!(!is_legacy_timestamped_log_name("2026/05/26-143022.log")); // wrong sep
        assert!(!is_legacy_timestamped_log_name("abcd-ef-gh-ijklmn.log")); // non-digits
        assert!(!is_legacy_timestamped_log_name("random.log"));
    }
}
