use std::path::PathBuf;

// Must match APP_GROUP_ID in the parent app's src-tauri/src/sandbox.rs.
// Both processes must hold the application-groups entitlement naming this
// group for the shared container path to be writable under sandbox.
pub const APP_GROUP_ID: &str = "group.com.onekeepass.desktop";

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

#[cfg(target_os = "macos")]
fn real_home_dir() -> Option<PathBuf> {
    let home = std::env::var("HOME").ok()?;
    if let Some(idx) = home.find("/Library/Containers/") {
        Some(PathBuf::from(&home[..idx]))
    } else {
        Some(PathBuf::from(home))
    }
}

#[cfg(target_os = "macos")]
pub fn group_container_path() -> Option<PathBuf> {
    if !is_sandboxed() {
        return None;
    }
    let home = real_home_dir()?;
    Some(home.join("Library/Group Containers").join(APP_GROUP_ID))
}

#[cfg(not(target_os = "macos"))]
pub fn group_container_path() -> Option<PathBuf> {
    None
}
