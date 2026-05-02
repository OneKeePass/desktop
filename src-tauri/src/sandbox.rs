use std::path::PathBuf;

// App Group identifier shared between OneKeePass.app and onekeepass-proxy
// in the Mac App Store build. Must also be registered for both bundle IDs
// in the Apple Developer Portal and listed in their entitlements.
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
// onekeepass-proxy. Always returns Some on macOS (sandboxed or not) so the
// IPC socket location is consistent between the main app and the proxy across
// both MAS and DMG builds. Under App Sandbox the application-groups entitlement
// authorises writes here; non-sandboxed DMG builds can write here freely.
#[cfg(target_os = "macos")]
pub fn group_container_path() -> Option<PathBuf> {
    let home = real_home_dir()?;
    Some(home.join("Library/Group Containers").join(APP_GROUP_ID))
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
        "chrome" => Some(home.join("Library/Application Support/Google/Chrome/NativeMessagingHosts")),
        _ => None,
    }
}

#[cfg(not(target_os = "macos"))]
pub(crate) fn browser_manifest_dir(_browser_id: &str) -> Option<PathBuf> {
    None
}
