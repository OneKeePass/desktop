use std::path::PathBuf;

// App Group identifier shared between OneKeePass.app and onekeepass-proxy
// in the Mac App Store build. Must also be registered for both bundle IDs
// in the Apple Developer Portal and listed in their entitlements.
#[cfg(all(target_os = "macos", feature = "mas-build"))]
pub const APP_GROUP_ID: &str = "group.com.onekeepass.desktop";

// True when running inside macOS App Sandbox. Detection is via launchd's
// $HOME redirection: sandboxed apps see HOME under /Library/Containers/<bundle>/Data.
// Returns false on non-macOS, on DMG/Developer-ID builds, and during `cargo run`.
#[cfg(target_os = "macos")]
pub fn is_sandboxed() -> bool {
    std::env::var("HOME")
        .map(|h| h.contains("/Library/Containers/"))
        .unwrap_or(false)
}

#[cfg(not(target_os = "macos"))]
pub fn is_sandboxed() -> bool {
    false
}

// True when running inside a Flatpak. The runtime writes /.flatpak-info into
// every instance before the app starts. $FLATPAK_ID is also set, but it is a
// plain environment variable and survives into processes launched on the host,
// so the file is the reliable signal.
#[cfg(target_os = "linux")]
pub fn is_flatpak() -> bool {
    std::path::Path::new("/.flatpak-info").exists()
}

#[cfg(not(target_os = "linux"))]
pub fn is_flatpak() -> bool {
    false
}

// User's real home directory, even when called from inside an App Sandbox
// where $HOME is redirected to the per-app container. Strips the
// /Library/Containers/<bundle>/Data suffix when present.
#[cfg(target_os = "macos")]
pub(crate) fn real_home_dir() -> Option<PathBuf> {
    let home = std::env::var("HOME").ok()?;
    if let Some(idx) = home.find("/Library/Containers/") {
        Some(PathBuf::from(&home[..idx]))
    } else {
        Some(PathBuf::from(home))
    }
}

// Path to the macOS App Group container shared by OneKeePass.app and
// onekeepass-proxy in the Mac App Store build. Direct-download/dev builds use
// tipsy's default socket location instead, avoiding macOS "access data from
// other apps" privacy prompts caused by touching Group Containers.
#[cfg(target_os = "macos")]
pub fn group_container_path() -> Option<PathBuf> {
    #[cfg(not(feature = "mas-build"))]
    {
        return None;
    }

    #[cfg(feature = "mas-build")]
    {
        let home = real_home_dir()?;
        Some(home.join("Library/Group Containers").join(APP_GROUP_ID))
    }
}

#[cfg(not(target_os = "macos"))]
pub fn group_container_path() -> Option<PathBuf> {
    None
}

// Standard directory where the native-messaging manifest for `browser_id`
// must be placed. Uses the real home dir even under sandbox so the path
// always points to where the browser looks for manifests. Returns None for
// unknown browser ids or on non-macOS.
#[cfg(target_os = "macos")]
pub(crate) fn browser_manifest_dir(browser_id: &str) -> Option<PathBuf> {
    let home = real_home_dir()?;
    match browser_id.to_ascii_lowercase().as_str() {
        "firefox" => Some(home.join("Library/Application Support/Mozilla/NativeMessagingHosts")),
        "chrome" => {
            Some(home.join("Library/Application Support/Google/Chrome/NativeMessagingHosts"))
        }
        "brave" => Some(
            home.join("Library/Application Support/BraveSoftware/Brave-Browser/NativeMessagingHosts"),
        ),
        _ => None,
    }
}

#[cfg(not(target_os = "macos"))]
pub(crate) fn browser_manifest_dir(_browser_id: &str) -> Option<PathBuf> {
    None
}

// Reads one key out of /.flatpak-info, which the runtime writes into every
// instance. It is INI: section headers in brackets, then key=value lines.
#[cfg(target_os = "linux")]
fn flatpak_info_value(section: &str, key: &str) -> Option<String> {
    let text = std::fs::read_to_string("/.flatpak-info").ok()?;
    let mut in_section = false;
    for line in text.lines() {
        let line = line.trim();
        if line.starts_with('[') && line.ends_with(']') {
            in_section = &line[1..line.len() - 1] == section;
        } else if in_section {
            if let Some((k, v)) = line.split_once('=') {
                if k.trim() == key {
                    return Some(v.trim().to_string());
                }
            }
        }
    }
    None
}

// Path of the launcher script Flatpak exports onto the host for this app, e.g.
// /var/lib/flatpak/exports/bin/com.onekeepass.OneKeePass. It runs `flatpak run <app-id>`
// and forwards its arguments, so it is the only path to the proxy that means anything
// to a browser running outside this sandbox.
//
// app-path points at <install-root>/app/<app-id>/<arch>/<branch>/<commit>/files, so the
// install root is whatever precedes /app/<app-id>/. Splitting on the app id rather than
// on "/app/" keeps a home directory that happens to contain an "app" component from
// truncating the root.
#[cfg(target_os = "linux")]
pub(crate) fn flatpak_host_launcher_path() -> Option<PathBuf> {
    let app_id = flatpak_info_value("Application", "name")?;

    let root = flatpak_info_value("Instance", "app-path")
        .and_then(|app_path| {
            let marker = format!("/app/{}/", app_id);
            app_path.find(&marker).map(|i| app_path[..i].to_string())
        })
        // Both default install roots export into the same layout; the system-wide one
        // is the better guess when app-path is missing or shaped unexpectedly.
        .unwrap_or_else(|| "/var/lib/flatpak".to_string());

    Some(PathBuf::from(root).join("exports/bin").join(app_id))
}

// True when this Flatpak demonstrably has no filesystem grant covering `path`.
//
// Without a grant the app still has a writable home, so creating a directory under it
// and writing a manifest there both succeed -- into a private directory no browser
// reads. That failure is invisible at the call site, which is why it is checked here
// instead.
//
// Answers false whenever the grant cannot be read, so a change in how permissions are
// reported degrades to attempting the write rather than disabling the feature.
#[cfg(target_os = "linux")]
pub(crate) fn flatpak_filesystem_grant_missing(path: &std::path::Path) -> bool {
    let Some(filesystems) = flatpak_info_value("Context", "filesystems") else {
        return false;
    };

    let home = std::env::var("HOME").unwrap_or_default();
    let wanted = path.to_string_lossy().to_string();

    for entry in filesystems.split(';').filter(|e| !e.is_empty()) {
        // Entries carry an access mode: ~/.mozilla/native-messaging-hosts:create
        let granted = entry.split(':').next().unwrap_or(entry);

        if granted == "host" || granted == "home" {
            return false;
        }

        // The tilde form is what the manifest asks for; whether it survives into
        // /.flatpak-info is not something to depend on, so both are compared.
        let expanded = if let Some(rest) = granted.strip_prefix("~/") {
            format!("{}/{}", home, rest)
        } else {
            granted.to_string()
        };

        if wanted == expanded || wanted.starts_with(&format!("{}/", expanded)) {
            return false;
        }
    }

    true
}
