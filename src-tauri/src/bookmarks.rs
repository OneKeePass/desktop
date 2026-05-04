// Security-scoped bookmark wrapper around the Swift FFI in
// swift-lib/src/Bookmarks.swift. Used to persist file/folder access across
// app launches under the macOS App Sandbox. Calls are no-ops / Err on
// non-macOS so call sites do not need additional cfg gating.

use std::{fs, path::PathBuf};

use onekeepass_core::db_service::service_util::string_to_simple_hash;

// Transparent handle around the Int64 returned by Swift. Each successful
// `resolve_and_start` must be paired with exactly one `release` call.
// Releasing twice or releasing an unknown handle is harmless but logged on
// the Swift side.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct BookmarkHandle(i64);

#[derive(Debug, Clone, Copy)]
enum StoreKind {
    DbFile,
    BackupDir,
    BrowserDir,
}

impl StoreKind {
    fn dir_name(self) -> &'static str {
        match self {
            Self::DbFile => "db-files",
            Self::BackupDir => "backup-dirs",
            Self::BrowserDir => "browser-dirs",
        }
    }
}

// Creates a base64-encoded security-scoped bookmark for `path`. Caller must
// already have access to the path (typically just after a Tauri file/folder
// dialog returned it). Returns None if the OS refuses to create the bookmark.
pub(crate) fn create(path: &str) -> Option<String> {
    imp::create(path)
}

// Resolves a previously-created bookmark and starts security-scoped access on
// the resulting URL, returning a handle the caller must later pass to `release`.
// If the OS flagged the bookmark as stale, a refreshed base64 blob is returned
// in the second tuple element so the caller can persist it back into storage.
pub(crate) fn resolve_and_start(
    b64: &str,
) -> Result<(BookmarkHandle, Option<String>), &'static str> {
    imp::resolve_and_start(b64)
}

// Releases the scoped access associated with `handle`. After this call the
// handle value is no longer valid.
pub(crate) fn release(handle: BookmarkHandle) {
    imp::release(handle);
}

pub(crate) fn load_db_file(path: &str) -> Option<String> {
    load(StoreKind::DbFile, path)
}

pub(crate) fn store_db_file(path: &str, b64: &str) {
    store(StoreKind::DbFile, path, b64);
}

pub(crate) fn remove_db_file(path: &str) {
    remove(StoreKind::DbFile, path);
}

pub(crate) fn clear_db_files() {
    clear(StoreKind::DbFile);
}

pub(crate) fn load_backup_dir(path: &str) -> Option<String> {
    load(StoreKind::BackupDir, path)
}

pub(crate) fn store_backup_dir(path: &str, b64: &str) {
    store(StoreKind::BackupDir, path, b64);
}

pub(crate) fn remove_backup_dir(path: &str) {
    remove(StoreKind::BackupDir, path);
}

pub(crate) fn load_browser_dir(browser_id: &str) -> Option<String> {
    let dir = store_dir(StoreKind::BrowserDir)?;
    let prefix = format!("{}-", browser_id);
    for entry in fs::read_dir(dir).ok()?.flatten() {
        let file_name = entry.file_name().to_string_lossy().to_string();
        if file_name.starts_with(&prefix) && file_name.ends_with(".bm") {
            if let Ok(b64) = fs::read_to_string(entry.path()) {
                let b64 = b64.trim().to_string();
                if !b64.is_empty() {
                    return Some(b64);
                }
            }
        }
    }
    None
}

pub(crate) fn store_browser_dir(browser_id: &str, path: &str, b64: &str) {
    remove_browser_dir(browser_id);
    let file_name = format!("{}-{}.bm", browser_id, string_to_simple_hash(path));
    if let Some(path) = store_dir(StoreKind::BrowserDir).map(|dir| dir.join(file_name)) {
        write_bookmark(path, b64);
    }
}

pub(crate) fn remove_browser_dir(browser_id: &str) {
    let Some(dir) = store_dir(StoreKind::BrowserDir) else {
        return;
    };
    let prefix = format!("{}-", browser_id);
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let file_name = entry.file_name().to_string_lossy().to_string();
            if file_name.starts_with(&prefix) && file_name.ends_with(".bm") {
                let _ = fs::remove_file(entry.path());
            }
        }
    }
}

pub(crate) fn clear_browser_dirs() {
    clear(StoreKind::BrowserDir);
}

fn store_dir(kind: StoreKind) -> Option<PathBuf> {
    crate::sandbox::group_container_path()
        .map(|dir| dir.join("OKP-SHARED").join("bookmarks").join(kind.dir_name()))
}

fn store_file_path(kind: StoreKind, key: &str) -> Option<PathBuf> {
    let file_name = format!("{}.bm", string_to_simple_hash(key));
    store_dir(kind).map(|dir| dir.join(file_name))
}

fn load(kind: StoreKind, key: &str) -> Option<String> {
    let b64 = fs::read_to_string(store_file_path(kind, key)?)
        .ok()?
        .trim()
        .to_string();
    if b64.is_empty() {
        None
    } else {
        Some(b64)
    }
}

fn store(kind: StoreKind, key: &str, b64: &str) {
    if let Some(path) = store_file_path(kind, key) {
        write_bookmark(path, b64);
    }
}

fn write_bookmark(path: PathBuf, b64: &str) {
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    if let Err(err) = fs::write(&path, b64.as_bytes()) {
        log::error!("Failed to write bookmark file {:?}: {}", path, err);
    }
}

fn remove(kind: StoreKind, key: &str) {
    if let Some(path) = store_file_path(kind, key) {
        let _ = fs::remove_file(path);
    }
}

fn clear(kind: StoreKind) {
    if let Some(dir) = store_dir(kind) {
        let _ = fs::remove_dir_all(&dir);
        let _ = fs::create_dir_all(dir);
    }
}

#[cfg(target_os = "macos")]
mod imp {
    use super::BookmarkHandle;
    use serde::Deserialize;
    use swift_rs::{swift, SRString};

    swift!(fn bookmark_create(path: &SRString) -> SRString);
    swift!(fn bookmark_resolve_and_start(b64: &SRString) -> SRString);
    swift!(fn bookmark_release(handle: i64));

    #[derive(Deserialize)]
    struct ResolveResult {
        handle: i64,
        refreshed_b64: Option<String>,
    }

    pub(super) fn create(path: &str) -> Option<String> {
        let s: SRString = path.into();
        let result = unsafe { bookmark_create(&s) };
        let b64 = result.to_string();
        if b64.is_empty() {
            None
        } else {
            Some(b64)
        }
    }

    pub(super) fn resolve_and_start(
        b64: &str,
    ) -> Result<(BookmarkHandle, Option<String>), &'static str> {
        let s: SRString = b64.into();
        let result = unsafe { bookmark_resolve_and_start(&s) };
        let json = result.to_string();
        if json.is_empty() {
            return Err("bookmark resolve failed (see Console.app for the underlying NSError)");
        }
        let parsed: ResolveResult = serde_json::from_str(&json)
            .map_err(|_| "bookmark resolve returned malformed JSON")?;
        Ok((BookmarkHandle(parsed.handle), parsed.refreshed_b64))
    }

    pub(super) fn release(handle: BookmarkHandle) {
        unsafe { bookmark_release(handle.0) };
    }
}

#[cfg(not(target_os = "macos"))]
mod imp {
    use super::BookmarkHandle;

    pub(super) fn create(_path: &str) -> Option<String> {
        None
    }

    pub(super) fn resolve_and_start(
        _b64: &str,
    ) -> Result<(BookmarkHandle, Option<String>), &'static str> {
        Err("security-scoped bookmarks are macOS-only")
    }

    pub(super) fn release(_handle: BookmarkHandle) {}
}
