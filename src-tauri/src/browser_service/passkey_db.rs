//! High-level orchestration layer between the browser_service message handler
//! and the onekeepass-core KDBX operations for passkeys.
//!
//! Each public function in this module corresponds to one message round-trip
//! between the browser extension and the desktop app.

use serde::Serialize;
use tauri::{Emitter, Manager};
use uuid::Uuid;

use onekeepass_core::db_service as kp_service;
use onekeepass_core::error::Result;
use onekeepass_core::db_service::browser_extension::{
    PasskeyEntry, PasskeySummary, PasskeyStorageInfo,
};

use crate::app_state;
use crate::browser_service::passkey_crypto::{self, PasskeyCreationResult};
use crate::constants::event_names::PASSKEY_DATA_CHANGED_EVENT;
use crate::constants::window_labels::MAIN_WINDOW_LABEL;

#[derive(Clone, Serialize)]
struct PasskeyChangedPayload {
    db_key: String,
}

// ── Shared data structures ────────────────────────────────────────────────────

/// Minimal database descriptor returned to the extension so the user can choose
/// which open database to store the new passkey in.
#[derive(Debug, Serialize)]
pub struct OpenedDbInfo {
    pub db_key: String,
    pub db_name: String,
}

// ── Passkey creation helpers ──────────────────────────────────────────────────

/// Returns a list of all currently open, browser-enabled databases.
///
/// The extension presents this list to the user as the first step of the
/// passkey creation popup.
pub(crate) fn get_opened_databases_for_passkey() -> Result<Vec<OpenedDbInfo>> {
    let db_keys = kp_service::all_kdbx_cache_keys()?;
    let mut result = Vec::with_capacity(db_keys.len());

    for db_key in &db_keys {
        // Retrieve the database name from the meta information.
        // On any error (e.g. db was closed between the two calls) skip the entry.
        let name_result = kp_service::browser_extension::get_db_name(db_key);
        if let Ok(db_name) = name_result {
            result.push(OpenedDbInfo {
                db_key: db_key.clone(),
                db_name,
            });
        }
    }

    Ok(result)
}

/// Generates a new P-256 key pair, builds all WebAuthn registration structures,
/// stores the passkey in KDBX, persists the database to disk, and returns the
/// credential JSON that the extension passes back to the website.
///
/// `target` determines whether the passkey is added to an existing KDBX entry
/// or stored as a brand-new entry.
pub(crate) fn create_and_store_passkey(
    db_key: &str,
    options_json: &str,
    origin: &str,
    existing_entry_uuid: Option<String>,
    new_entry_name: Option<String>,
    group_uuid: Option<String>,
    new_group_name: Option<String>,
) -> Result<String> {
    // 1. Crypto: generate key, build WebAuthn structures
    let creation_result: PasskeyCreationResult =
        passkey_crypto::create_passkey(options_json, origin)?;

    // 2. Convert the optional UUIDs from strings
    let existing_entry_uuid_parsed = existing_entry_uuid
        .as_deref()
        .map(|s| {
            Uuid::parse_str(s).map_err(|e| {
                onekeepass_core::error::Error::UnexpectedError(format!(
                    "Invalid existing_entry_uuid: {}",
                    e
                ))
            })
        })
        .transpose()?;

    let group_uuid_parsed = group_uuid
        .as_deref()
        .map(|s| {
            Uuid::parse_str(s).map_err(|e| {
                onekeepass_core::error::Error::UnexpectedError(format!(
                    "Invalid group_uuid: {}",
                    e
                ))
            })
        })
        .transpose()?;

    // 3. Persist in KDBX (in-memory mutation)
    let storage_info = PasskeyStorageInfo {
        credential_id_b64url: creation_result.credential_id_b64url.clone(),
        private_key_pem: creation_result.private_key_pem.clone(),
        rp_id: creation_result.rp_id.clone(),
        rp_name: creation_result.rp_name.clone(),
        username: creation_result.username.clone(),
        user_handle_b64url: creation_result.user_handle_b64url.clone(),
        origin: creation_result.origin.clone(),
        entry_uuid: existing_entry_uuid_parsed,
        new_entry_name,
        group_uuid: group_uuid_parsed,
        new_group_name,
    };
    // 4. Delegate entry create/update + save to core (with correct backup path)
    let backup_file_name = app_state::AppState::state_instance().get_backup_file(db_key);

    log::debug!("Passkey creation kdbx backup_file_name {:?}",&backup_file_name);

    kp_service::browser_extension::create_and_store_passkey(
        db_key,
        storage_info,
        backup_file_name.as_deref(),
    )?;

    // 5. Notify the main window so the UI reloads the entry list
    if let Some(win) = app_state::AppState::global_app_handle()
        .get_webview_window(MAIN_WINDOW_LABEL)
    {
        log::debug!("Emiting event PASSKEY_DATA_CHANGED_EVENT in db message to the UI layer");
        let _ = win.emit(
            PASSKEY_DATA_CHANGED_EVENT,
            PasskeyChangedPayload { db_key: db_key.to_string() },
        );
    }

    Ok(creation_result.credential_json)
}

// ── Passkey authentication helpers ───────────────────────────────────────────

/// Searches all open databases for passkeys matching `rp_id`.
///
/// If `allow_credential_ids` is non-empty (site sent `allowCredentials`), only
/// passkeys whose credential ID appears in that list are returned.
pub(crate) fn find_matching_passkeys(
    rp_id: &str,
    allow_credential_ids: Vec<String>,
) -> Result<Vec<PasskeySummary>> {
    let db_keys = kp_service::all_kdbx_cache_keys()?;
    kp_service::browser_extension::find_matching_passkeys(&db_keys, rp_id, &allow_credential_ids)
}

/// Loads the passkey stored in `entry_uuid` (within `db_key`) and produces the
/// signed WebAuthn assertion JSON.
pub(crate) fn sign_passkey_assertion(
    db_key: &str,
    entry_uuid: &Uuid,
    options_json: &str,
    origin: &str,
) -> Result<String> {
    // 1. Retrieve the passkey data from KDBX
    let passkey: PasskeyEntry =
        kp_service::browser_extension::get_passkey_for_assertion(db_key, entry_uuid)?;

    // 2. Sign the assertion
    let assertion = passkey_crypto::sign_assertion(
        &passkey.credential_id_b64url,
        &passkey.rp_id,
        &passkey.user_handle_b64url,
        &passkey.private_key_pem,
        options_json,
        origin,
    )?;

    Ok(assertion.credential_json)
}
