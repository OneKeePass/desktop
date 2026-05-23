// Desktop orchestration over the shared remote-storage layer in
// onekeepass-core. Thin compared to mobile: no on-disk backup, no offline
// read-from-backup fallback, no history pruning, no iOS Autofill copy.
//
// Conflict detection: when opening a remote db, the remote file's
// modified-time is recorded in AppState keyed by db_key. On save, the
// recorded value is compared to the current remote mtime; a mismatch
// (when overwrite==false) returns DbFileContentChangeDetected and the UI
// prompts the user to merge or force-overwrite.

pub(crate) mod callback_service_provider;

// Cheap check for routing: true when db_key was minted by the remote-storage
// open/create flow (prefixed "Sftp-" or "Webdav-"). The actual parse only
// happens when we need to operate on the remote server. Callers use this to
// keep local file paths off the SFTP/WebDAV code path and vice versa.
pub(crate) fn is_remote_db_key(db_key: &str) -> bool {
    db_key.starts_with("Sftp-") || db_key.starts_with("Webdav-")
}

use std::io::Cursor;
use std::sync::Arc;

use log::{debug, info};
use uuid::Uuid;

use onekeepass_core::db_service::{
    self, KdbxLoaded, KdbxSaved, MergeResult, NewDatabase, RemoteConnectionEntrySummary,
};
use onekeepass_core::error::{self, Result};
use onekeepass_core::remote_storage::storage_service::{
    rs_type_from_db_key, ConnectionConfigs, RemoteFileMetadata, RemoteStorageOperation,
    RemoteStorageOperationType, RemoteStorageType, RemoteStorageTypeConfig,
};

// All functions below are synchronous and internally call the
// onekeepass-core macros that do `oneshot::Receiver::blocking_recv()`. That
// primitive panics if called from a Tokio worker thread, so the Tauri
// commands that invoke these functions MUST schedule them via
// `tokio::task::spawn_blocking`. To keep the blocking closures simple
// (`'static`-bound, no borrow of the Tauri `State<AppState>`), these
// functions take or return raw mtime values; the AppState mtime cache is
// updated by the Tauri command on either side of the spawn_blocking call.

pub(crate) fn rs_read_kdbx(
    db_file_name: &str,
    password: Option<&str>,
    key_file_name: Option<&str>,
) -> Result<(KdbxLoaded, Option<i64>)> {
    let rs_operation_type = rs_type_from_db_key(db_file_name)?;

    rs_operation_type.connect_by_id().map_err(|e| {
        info!("Remote storage connection error: {}", e);
        error::Error::NoRemoteStorageConnection
    })?;

    debug!("Remote server connected; reading file");

    let r = rs_operation_type.read()?;
    let remote_mtime = r.meta.modified.map(|t| t as i64);

    let file_name = rs_operation_type.file_name().ok_or(error::Error::DataError(
        "File name is not found in the rs operation type formed from the db key parsing",
    ))?;

    let mut reader = Cursor::new(&r.data);
    let kdbx_loaded = db_service::read_kdbx(
        &mut reader,
        db_file_name,
        password,
        key_file_name,
        Some(file_name),
    )?;

    Ok((kdbx_loaded, remote_mtime))
}

pub(crate) fn rs_save_kdbx(
    db_key: &str,
    overwrite: bool,
    recorded_mtime: Option<i64>,
    backup_file_name: Option<&str>,
) -> Result<(KdbxSaved, Option<i64>)> {
    let rs_operation_type = rs_type_from_db_key(db_key)?;

    rs_operation_type.connect_by_id().map_err(|e| {
        info!("Remote storage connection error: {}", e);
        error::Error::NoRemoteStorageConnection
    })?;

    if !overwrite && is_remote_file_modified(&rs_operation_type, recorded_mtime)? {
        return Err(error::Error::DbFileContentChangeDetected);
    }

    let mut mem = Cursor::new(Vec::<u8>::new());
    let kdbx_saved = db_service::save_kdbx_to_writer(&mut mem, db_key)?;

    let bytes = mem.into_inner();
    write_local_backup(backup_file_name, &bytes);

    let data = Arc::new(bytes);
    let meta = rs_operation_type.write_file(data)?;
    let remote_mtime = meta.modified.map(|t| t as i64);

    Ok((kdbx_saved, remote_mtime))
}

pub(crate) fn rs_create_kdbx(
    new_db: NewDatabase,
    backup_file_name: Option<&str>,
) -> Result<(KdbxLoaded, String, Option<i64>)> {
    let db_key = new_db.database_file_name.clone();
    let rs_operation_type = rs_type_from_db_key(&db_key)?;

    if rs_operation_type.file_metadata().is_ok() {
        return Err(error::Error::DataError(
            "A file with this name already exists at this location. Choose a different name.",
        ));
    }

    let mut mem = Cursor::new(Vec::<u8>::new());
    let kdbx_loaded = db_service::create_and_write_to_writer(&mut mem, new_db)?;

    let bytes = mem.into_inner();
    write_local_backup(backup_file_name, &bytes);

    let data = Arc::new(bytes);
    let meta = rs_operation_type.create_file(data)?;
    let remote_mtime = meta.modified.map(|t| t as i64);

    Ok((kdbx_loaded, db_key, remote_mtime))
}

// Mirror of the local save path: write the serialized kdbx bytes to the
// preferences-resolved backup file before pushing them to the remote.
// Best-effort — a backup failure is logged but does not abort the save (the
// user's primary target is the remote file). The parent directory is
// created if it does not already exist.
fn write_local_backup(backup_file_name: Option<&str>, bytes: &[u8]) {
    let Some(backup_path) = backup_file_name else {
        return;
    };
    if let Some(parent) = std::path::Path::new(backup_path).parent() {
        if !parent.as_os_str().is_empty() && !parent.exists() {
            if let Err(e) = std::fs::create_dir_all(parent) {
                log::warn!(
                    "Remote-storage backup: could not create dir {:?}: {}",
                    parent,
                    e
                );
                return;
            }
        }
    }
    if let Err(e) = std::fs::write(backup_path, bytes) {
        log::warn!(
            "Remote-storage backup: write to {} failed: {}",
            backup_path,
            e
        );
    } else {
        debug!("Remote-storage backup written: {}", backup_path);
    }
}

// True when the remote mtime no longer matches what was recorded when the
// db was last opened or saved. Defensive: if either value is missing
// (some servers don't report mtime, or this is the first save after open
// with no prior record), treats it as unmodified to avoid spurious
// conflicts blocking the user.
fn is_remote_file_modified(
    rs_operation_type: &RemoteStorageOperationType,
    recorded: Option<i64>,
) -> Result<bool> {
    let Some(recorded) = recorded else {
        return Ok(false);
    };

    let current: Option<i64> = rs_operation_type
        .file_metadata()
        .ok()
        .and_then(|m: RemoteFileMetadata| m.modified.map(|t| t as i64));

    let Some(current) = current else {
        return Ok(false);
    };

    Ok(current != recorded)
}

// Returns the current remote mtime (None if the server doesn't report
// one or the lookup failed). Used by the focus-poll detector to decide
// whether the remote file diverged from what we last saw. The recorded
// value lives in AppState; the Tauri command does the compare.
pub(crate) fn rs_current_remote_mtime(db_key: &str) -> Result<Option<i64>> {
    let rs_operation_type = rs_type_from_db_key(db_key)?;

    rs_operation_type.connect_by_id().map_err(|e| {
        info!("Remote storage connection error: {}", e);
        error::Error::NoRemoteStorageConnection
    })?;

    let current = rs_operation_type
        .file_metadata()
        .ok()
        .and_then(|m: RemoteFileMetadata| m.modified.map(|t| t as i64));

    Ok(current)
}

// Downloads the current remote bytes and merges them into the in-memory
// db using the stored composite key. Returns the merge result and the
// remote file's mtime at fetch time (so the caller can refresh
// AppState.remote_mtime and avoid an immediate re-prompt).
pub(crate) fn rs_merge_with_remote(db_key: &str) -> Result<(MergeResult, Option<i64>)> {
    let rs_operation_type = rs_type_from_db_key(db_key)?;

    rs_operation_type.connect_by_id().map_err(|e| {
        info!("Remote storage connection error: {}", e);
        error::Error::NoRemoteStorageConnection
    })?;

    let r = rs_operation_type.read()?;
    let remote_mtime = r.meta.modified.map(|t| t as i64);

    let mut reader = Cursor::new(&r.data);
    let merge_result = db_service::merge_kdbx_with_reader(db_key, &mut reader)?;

    Ok((merge_result, remote_mtime))
}

// ---- read-only resolvers used by the connection picker ----

pub(crate) fn list_kdbx_source_connections(
    rs_storage_type: RemoteStorageType,
) -> Result<Vec<RemoteConnectionEntrySummary>> {
    let entry_type_uuid_bytes = match rs_storage_type {
        RemoteStorageType::Sftp => db_service::entry_type_uuid::REMOTE_CONNECTION_SFTP,
        RemoteStorageType::Webdav => db_service::entry_type_uuid::REMOTE_CONNECTION_WEBDAV,
        _ => {
            return Err(error::Error::DataError("Unsupported remote storage type"));
        }
    };

    let entry_type_uuid = uuid::Builder::from_slice(entry_type_uuid_bytes)
        .map_err(|_| error::Error::DataError("Failed to build entry-type uuid"))?
        .into_uuid();

    Ok(db_service::list_remote_connection_entries(&entry_type_uuid))
}

pub(crate) fn get_remote_storage_config(
    rs_storage_type: RemoteStorageType,
    connection_id: &str,
) -> Result<RemoteStorageTypeConfig> {
    let u_id = Uuid::parse_str(connection_id)
        .map_err(|_| error::Error::DataError("Invalid connection_id (uuid)"))?;

    ConnectionConfigs::find_remote_storage_config(&u_id, rs_storage_type).ok_or(
        error::Error::DataError("Connection config not found"),
    )
}
