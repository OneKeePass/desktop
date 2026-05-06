use std::path::PathBuf;

use crate::app_state::AppState;

pub(crate) use super::bookmarks::BookmarkHandle;
use super::bookmarks;

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub(crate) enum ScopedAccessKey {
    Db(String),
    DbDir(String),
    KeyFile(String),
    BackupDir,
}

#[derive(Default)]
pub(crate) struct LoadKdbxAccess {
    db_file: Option<BookmarkHandle>,
    db_dir: Option<BookmarkHandle>,
    key_file: Option<BookmarkHandle>,
}

impl LoadKdbxAccess {
    pub(crate) fn prepare(db_file_name: &str, key_file_name: Option<&str>) -> Self {
        let db_parent_dir = parent_dir_string(db_file_name);
        Self {
            db_file: start_db_file_access(db_file_name),
            db_dir: db_parent_dir.as_deref().and_then(start_db_dir_access),
            key_file: key_file_name.and_then(start_key_file_access),
        }
    }

    pub(crate) fn release_all(&mut self) {
        release_if_some(self.db_file.take());
        release_if_some(self.db_dir.take());
        release_if_some(self.key_file.take());
    }

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
            app_state.store_scoped_access(
                ScopedAccessKey::KeyFile(key_file_name.to_string()),
                handle,
            );
        }
        if let (Some(dir), Some(handle)) = (db_parent_dir, self.db_dir.take()) {
            app_state.store_scoped_access(ScopedAccessKey::DbDir(dir), handle);
        }
    }
}

impl Drop for LoadKdbxAccess {
    fn drop(&mut self) {
        self.release_all();
    }
}

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

pub(crate) struct ScopedAccessGuard(Option<BookmarkHandle>);

impl ScopedAccessGuard {
    pub(crate) fn none() -> Self {
        Self(None)
    }
}

impl Drop for ScopedAccessGuard {
    fn drop(&mut self) {
        release_if_some(self.0.take());
    }
}

pub(crate) fn auto_open_root_dir_access(db_file_name: &str) -> ScopedAccessGuard {
    let Some(dir) = parent_dir_string(db_file_name) else {
        return ScopedAccessGuard::none();
    };
    ScopedAccessGuard(start_db_dir_access(&dir))
}

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

pub(crate) fn store_auto_open_success_handles(
    access: &mut LoadKdbxAccess,
    db_file_name: &str,
    key_file_name: Option<&str>,
    app_state: &AppState,
) {
    access.store_success_handles(db_file_name, key_file_name, app_state);
}

pub(crate) fn create_backup_dir_bookmark(dir: &str) {
    if let Some(b64) = bookmarks::create(dir) {
        bookmarks::store_backup_dir(dir, &b64);
    }
}

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

pub(crate) fn release_scoped_handle(handle: BookmarkHandle) {
    bookmarks::release(handle);
}

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

fn create_db_file_bookmark(db_file_name: &str) {
    if let Some(b64) = bookmarks::create(db_file_name) {
        bookmarks::store_db_file(db_file_name, &b64);
    }
}

fn create_db_dir_bookmark(dir: &str) {
    if let Some(b64) = bookmarks::create(dir) {
        bookmarks::store_db_dir(dir, &b64);
    }
}

fn create_key_file_bookmark(key_file_name: &str) {
    if let Some(b64) = bookmarks::create(key_file_name) {
        bookmarks::store_key_file(key_file_name, &b64);
    }
}

fn release_if_some(handle: Option<BookmarkHandle>) {
    if let Some(handle) = handle {
        bookmarks::release(handle);
    }
}

fn parent_dir_string(file_name: &str) -> Option<String> {
    PathBuf::from(file_name)
        .parent()
        .map(|p| p.to_string_lossy().to_string())
}
