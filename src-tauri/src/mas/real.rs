// Real (active) implementation of the "mas-build" feature.
//
// Under the macOS App Store sandbox the app can only touch files the user has
// explicitly picked. To keep access across launches we persist a
// security-scoped bookmark per file/dir and re-open ("start") the scoped
// access before each read/write, releasing it afterwards. This module wraps
// that lifecycle; the non-sandboxed build uses the no-op variant in `noop.rs`.

use std::path::PathBuf;

use crate::app_state::AppState;

use super::bookmarks;
pub(crate) use super::bookmarks::BookmarkHandle;

// Identifies a stored scoped-access handle in AppState, keyed by the kind of
// resource (and its path, where there can be more than one).
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub(crate) enum ScopedAccessKey {
    Db(String),
    // TODO: Need to verify its use is required (AutoOpen specifc)
    DbDir(String),
    KeyFile(String),
    BackupDir,
}

// Holds the scoped-access handles needed to open a kdbx database (the db file,
// its parent dir and an optional key file) for the duration of a load. Handles
// are released on Drop unless handed off to AppState via store_success_handles.
#[derive(Default)]
pub(crate) struct LoadKdbxAccess {
    db_file: Option<BookmarkHandle>,
    // TODO: Need to verify its use is required (AutoOpen specifc).
    // May be useful when UI we ask user to open a db folder for future use.
    db_dir: Option<BookmarkHandle>,
    key_file: Option<BookmarkHandle>,
}

impl LoadKdbxAccess {
    // Starts scoped access from any bookmarks we already hold for these paths.
    // Missing bookmarks leave the matching handle as None (direct read is tried).
    pub(crate) fn prepare(db_file_name: &str, key_file_name: Option<&str>) -> Self {
        let db_parent_dir = parent_dir_string(db_file_name);
        Self {
            db_file: start_db_file_access(db_file_name),
            db_dir: db_parent_dir.as_deref().and_then(start_db_dir_access),
            key_file: key_file_name.and_then(start_key_file_access),
        }
    }

    // Stops scoped access for every handle still held here.
    pub(crate) fn release_all(&mut self) {
        release_if_some(self.db_file.take());
        release_if_some(self.db_dir.take());
        release_if_some(self.key_file.take());
    }

    // Called after a successful open. For any path we did not already have a
    // bookmark for, create one now (the user just granted access), then move
    // all live handles into AppState so access persists for later operations.
    pub(crate) fn store_success_handles(
        &mut self,
        db_file_name: &str,
        key_file_name: Option<&str>,
        app_state: &AppState,
    ) {
        if self.db_file.is_none() {
            create_db_file_bookmark(db_file_name);
            self.db_file = start_db_file_access(db_file_name);
        }
        if self.key_file.is_none() {
            if let Some(key_file_name) = key_file_name {
                create_key_file_bookmark(key_file_name);
                self.key_file = start_key_file_access(key_file_name);
            }
        }

        let db_parent_dir = parent_dir_string(db_file_name);
        if self.db_dir.is_none() {
            if let Some(dir) = db_parent_dir.as_deref() {
                create_db_dir_bookmark(dir);
                self.db_dir = start_db_dir_access(dir);
            }
        }

        if let Some(handle) = self.db_file.take() {
            app_state.store_scoped_access(ScopedAccessKey::Db(db_file_name.to_string()), handle);
        }
        if let (Some(key_file_name), Some(handle)) = (key_file_name, self.key_file.take()) {
            app_state
                .store_scoped_access(ScopedAccessKey::KeyFile(key_file_name.to_string()), handle);
        }
        if let (Some(dir), Some(handle)) = (db_parent_dir, self.db_dir.take()) {
            app_state.store_scoped_access(ScopedAccessKey::DbDir(dir), handle);
        }
    }
}

// Any handle not handed off to AppState is released when this value is dropped.
impl Drop for LoadKdbxAccess {
    fn drop(&mut self) {
        self.release_all();
    }
}

// True when a read failed with PermissionDenied for a recent file while
// sandboxed - i.e. our bookmark is stale/missing and the UI should ask the
// user to re-pick the file so we can mint a fresh bookmark.
pub(crate) fn should_request_db_repick(
    io_error_kind: std::io::ErrorKind,
    db_file_name: &str,
    app_state: &AppState,
) -> bool {
    io_error_kind == std::io::ErrorKind::PermissionDenied
        && crate::sandbox::is_sandboxed()
        && app_state
            .preference
            .lock()
            .unwrap()
            .is_in_recent_files(db_file_name)
}

// RAII guard that keeps a single scoped access open for the lifetime of the
// guard and releases it on Drop. Used for short-lived, single-resource access.
pub(crate) struct ScopedAccessGuard(Option<BookmarkHandle>);

impl ScopedAccessGuard {
    // A guard that holds nothing (no bookmark / nothing to release).
    pub(crate) fn none() -> Self {
        Self(None)
    }
}

impl Drop for ScopedAccessGuard {
    fn drop(&mut self) {
        release_if_some(self.0.take());
    }
}

// Holds scoped access to the database's parent directory (used by auto-open).
pub(crate) fn auto_open_root_dir_access(db_file_name: &str) -> ScopedAccessGuard {
    let Some(dir) = parent_dir_string(db_file_name) else {
        return ScopedAccessGuard::none();
    };
    ScopedAccessGuard(start_db_dir_access(&dir))
}

// Holds scoped access to the database file itself for one operation.
pub(crate) fn db_file_access(db_file_name: &str) -> ScopedAccessGuard {
    ScopedAccessGuard(start_db_file_access(db_file_name))
}

// Auto-open variant of LoadKdbxAccess::prepare: the parent dir is intentionally
// not opened here (auto-open relies on the file/key-file bookmarks only).
pub(crate) fn prepare_auto_open_db(
    db_file_name: &str,
    key_file_name: Option<&str>,
) -> LoadKdbxAccess {
    LoadKdbxAccess {
        db_file: start_db_file_access(db_file_name),
        db_dir: None,
        key_file: key_file_name.and_then(start_key_file_access),
    }
}

// Persists the handles gathered during a successful auto-open into AppState.
pub(crate) fn store_auto_open_success_handles(
    access: &mut LoadKdbxAccess,
    db_file_name: &str,
    key_file_name: Option<&str>,
    app_state: &AppState,
) {
    access.store_success_handles(db_file_name, key_file_name, app_state);
}

// Creates and stores a bookmark for a user-picked backup directory.
pub(crate) fn create_backup_dir_bookmark(dir: &str) {
    if let Some(b64) = bookmarks::create(dir) {
        bookmarks::store_backup_dir(dir, &b64);
    }
}

// Whether we may write backups to `dir`: always when not sandboxed, otherwise
// only if we hold a bookmark granting scoped access to it.
pub(crate) fn can_use_backup_dir(dir: &str) -> bool {
    !crate::sandbox::is_sandboxed() || bookmarks::load_backup_dir(dir).is_some()
}

// Records access just granted by the user picking the db file: create and store
// its bookmark, then start scoped access and keep the handle in AppState.
pub(crate) fn record_user_granted_db_file(db_file_name: &str, app_state: &AppState) {
    let new_bookmark_b64 = bookmarks::create(db_file_name);
    if let Some(b64) = &new_bookmark_b64 {
        bookmarks::store_db_file(db_file_name, b64);
    }
    if let Some(b64) = &new_bookmark_b64 {
        if let Ok((handle, _)) = bookmarks::resolve_and_start(b64) {
            app_state.store_scoped_access(ScopedAccessKey::Db(db_file_name.to_string()), handle);
        }
    }
}

// On startup (when sandboxed and backups are enabled), re-establishes scoped
// access to the saved backup directory from its stored bookmark, refreshing the
// bookmark if macOS reports it as stale.
pub(crate) fn init_backup_dir_scoped_access(app_state: &AppState) {
    if !crate::sandbox::is_sandboxed() {
        return;
    }
    let backup_dir = {
        let pref = app_state.preference.lock().unwrap();
        if !pref.backup.enabled {
            return;
        }
        pref.backup.dir.clone()
    };
    let Some(dir) = backup_dir else {
        return;
    };
    let Some(b64) = bookmarks::load_backup_dir(&dir) else {
        return;
    };

    match bookmarks::resolve_and_start(&b64) {
        Ok((handle, refreshed)) => {
            if let Some(refreshed_b64) = refreshed {
                bookmarks::store_backup_dir(&dir, &refreshed_b64);
                log::debug!("Refreshed stale backup-dir bookmark and persisted");
            }
            app_state.store_scoped_access(ScopedAccessKey::BackupDir, handle);
        }
        Err(e) => {
            log::warn!(
                "Failed to resolve backup-dir bookmark; backup writes to a non-default \
                 dir will be skipped until the user re-picks. Cause: {}",
                e
            );
        }
    }
}

// Stops scoped access for a handle previously stored in AppState.
pub(crate) fn release_scoped_handle(handle: BookmarkHandle) {
    bookmarks::release(handle);
}

// Starts scoped access to the db file from its stored bookmark (None if we have
// no bookmark or it fails to resolve - the caller then attempts a direct read).
fn start_db_file_access(db_file_name: &str) -> Option<BookmarkHandle> {
    let Some(b64) = bookmarks::load_db_file(db_file_name) else {
        return None;
    };
    resolve_and_refresh(&b64, |refreshed| {
        bookmarks::store_db_file(db_file_name, refreshed);
    })
    .map_err(|e| {
        log::warn!(
            "DB bookmark resolve failed for {}: {}. Attempting direct read.",
            db_file_name,
            e
        );
    })
    .ok()
}

// Starts scoped access to the db's parent directory from its stored bookmark.
fn start_db_dir_access(dir: &str) -> Option<BookmarkHandle> {
    let Some(b64) = bookmarks::load_db_dir(dir) else {
        return None;
    };
    resolve_and_refresh(&b64, |refreshed| {
        bookmarks::store_db_dir(dir, refreshed);
    })
    .map_err(|e| {
        log::warn!(
            "DB parent-dir bookmark resolve failed for {}: {}. Attempting direct read.",
            dir,
            e
        );
    })
    .ok()
}

// Starts scoped access to the key file from its stored bookmark.
fn start_key_file_access(key_file_name: &str) -> Option<BookmarkHandle> {
    let Some(b64) = bookmarks::load_key_file(key_file_name) else {
        return None;
    };
    resolve_and_refresh(&b64, |refreshed| {
        bookmarks::store_key_file(key_file_name, refreshed);
    })
    .map_err(|e| {
        log::warn!(
            "Key-file bookmark resolve failed for {}: {}. Attempting direct read.",
            key_file_name,
            e
        );
    })
    .ok()
}

// Resolves a base64 bookmark and starts scoped access; if macOS hands back a
// refreshed bookmark (the old one went stale), persists it via the callback.
fn resolve_and_refresh(
    b64: &str,
    store_refreshed: impl FnOnce(&str),
) -> Result<BookmarkHandle, &'static str> {
    let (handle, refreshed) = bookmarks::resolve_and_start(b64)?;
    if let Some(refreshed_b64) = refreshed {
        store_refreshed(&refreshed_b64);
    }
    Ok(handle)
}

// Creates and persists a bookmark for the db file (called once access is granted).
fn create_db_file_bookmark(db_file_name: &str) {
    if let Some(b64) = bookmarks::create(db_file_name) {
        bookmarks::store_db_file(db_file_name, &b64);
    }
}

// Creates and persists a bookmark for the db's parent directory.
fn create_db_dir_bookmark(dir: &str) {
    if let Some(b64) = bookmarks::create(dir) {
        bookmarks::store_db_dir(dir, &b64);
    }
}

// Creates and persists a bookmark for the key file.
fn create_key_file_bookmark(key_file_name: &str) {
    if let Some(b64) = bookmarks::create(key_file_name) {
        bookmarks::store_key_file(key_file_name, &b64);
    }
}

// Releases scoped access for a handle if one is present.
fn release_if_some(handle: Option<BookmarkHandle>) {
    if let Some(handle) = handle {
        bookmarks::release(handle);
    }
}

// Returns the parent directory of a path as a string, if it has one.
fn parent_dir_string(file_name: &str) -> Option<String> {
    PathBuf::from(file_name)
        .parent()
        .map(|p| p.to_string_lossy().to_string())
}
