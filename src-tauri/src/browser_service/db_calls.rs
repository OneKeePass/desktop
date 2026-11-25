use onekeepass_core::db_service as kp_service;

use onekeepass_core::db_service::browser_extension::{
    EntryBasicInfo, GroupInfo, LoginCredentialCheck, LoginCredentialInput,
    LoginCredentialSaveInfo, LoginCredentialSaveOutcome, PasskeySummary,
};
use onekeepass_core::error::Result;
use serde::Serialize;
use uuid::Uuid;

use crate::app_state;
use crate::browser_service::{passkey_db, passkey_db::OpenedDbInfo};

// Validates that `db_key` refers to a currently open AND unlocked database.
// Returns a generic error if the key is not recognised or is locked, to avoid
// revealing which databases exist (enumeration protection) and to keep a locked
// database unreachable from the extension. A locked db is treated exactly like an
// unavailable one, so the extension needs no change (see Extension-Locked-DB
// gating plan). Since every db-key-specific handler (entry details, custom icon,
// group/entry pickers, sign assertion, passkey create) routes through here, this
// single check gates them all.
pub(crate) fn validate_db_key(db_key: &str) -> Result<()> {
    let open_keys = kp_service::unlocked_kdbx_cache_keys()?;
    if open_keys.iter().any(|k| k == db_key) {
        Ok(())
    } else {
        log::warn!("db_key validation failed — key not in open, unlocked databases");
        Err(onekeepass_core::error::Error::UnexpectedError(
            "DATABASE_NOT_AVAILABLE".to_string(),
        ))
    }
}

#[derive(Serialize)]
pub(crate) struct AllMatchedEntries {
    browser_enabled_db_available: bool,
    url: String,
    matched_entries: Vec<kp_service::browser_extension::MatchedDbEntries>,
}

pub(crate) fn find_matching_in_enabled_db_entries(input_url: &str) -> Result<AllMatchedEntries> {
    // TODO: Need to get only the browser enabaled databases
    // Only unlocked databases are reachable from the extension; a locked (but open)
    // db is excluded so no matches leak from it.
    let enabled_db_keys = kp_service::unlocked_kdbx_cache_keys()?;

    // log::debug!("In find_matching_in_enabled_db_entries enabled_db_keys are {:?}", &enabled_db_keys);

    let browser_enabled_db_available = !enabled_db_keys.is_empty();

    let entries = kp_service::browser_extension::find_matching_in_enabled_db_entries(
        &enabled_db_keys,
        input_url,
    )?;

    Ok(AllMatchedEntries {
        browser_enabled_db_available,
        url: input_url.to_string(),
        matched_entries: entries,
    })
}

#[inline]
pub(crate) fn entry_details_by_id(
    db_key: &str,
    entry_uuid: &Uuid,
) -> Result<kp_service::browser_extension::BasicEntryCredentialInfo> {
    kp_service::browser_extension::basic_entry_credential_info(db_key, entry_uuid)
}

#[inline]
pub(crate) fn custom_icon_for_browser_extension(
    db_key: &str,
    custom_icon_uuid: &str,
) -> Result<kp_service::browser_extension::BrowserExtensionCustomIcon> {
    kp_service::browser_extension::custom_icon_for_browser_extension(db_key, custom_icon_uuid)
}

// ── Saving logins submitted in the browser ───────────────────────────────────

// Checks a login the user submitted on a site against the unlocked databases.
// The stored passwords are compared in core and never returned: the answer is a
// status with entry titles and the databases/groups a new entry can go to.
pub(crate) fn check_login_credential(
    input: &LoginCredentialInput,
) -> Result<LoginCredentialCheck> {
    // As for the fill list, only unlocked databases are reachable from the extension
    let enabled_db_keys = kp_service::unlocked_kdbx_cache_keys()?;
    kp_service::browser_extension::check_login_credential(&enabled_db_keys, input)
}

// Saves a login the user confirmed in the extension (adds an entry or updates the
// password of an existing one, keeping the old one in its history), writes the
// database file and tells the main window to show the entry.
pub(crate) fn save_login_credential(
    info: &LoginCredentialSaveInfo,
) -> Result<LoginCredentialSaveOutcome> {
    validate_db_key(&info.db_key)?;
    let enabled_db_keys = kp_service::unlocked_kdbx_cache_keys()?;
    let backup_file_name = app_state::AppState::state_instance().get_backup_file(&info.db_key);
    let outcome = kp_service::browser_extension::save_login_credential_to_db(
        &enabled_db_keys,
        info,
        backup_file_name.as_deref(),
    )?;
    passkey_db::notify_entry_saved_from_browser(
        &info.db_key,
        &outcome.entry_uuid.to_string(),
        &outcome.group_uuid.to_string(),
        &outcome.entry_type_uuid.to_string(),
        &outcome.entry_type_name,
        &outcome.tags,
        if outcome.created {
            passkey_db::BrowserEntryChange::LoginSaved
        } else {
            passkey_db::BrowserEntryChange::PasswordUpdated
        },
    );
    Ok(outcome)
}

// Following will get the complete EntryFormData which is not required for browser extension for now
// Instead we use some basic entry detail only as done above

// pub(crate) fn entry_details_by_id(db_key: &str, entry_uuid: &Uuid) -> Result<kp_service::EntryFormData>  {
//     kp_service::get_entry_form_data_by_id(db_key, entry_uuid)
// }

// ── Passkey wrappers ──────────────────────────────────────────────────────────

// Returns all currently open databases so the user can choose where to store
// the new passkey.
#[inline]
pub(crate) fn get_opened_databases_for_passkey() -> Result<Vec<OpenedDbInfo>> {
    passkey_db::get_opened_databases_for_passkey()
}

// Returns all user-visible groups in the database for the passkey group picker.
#[inline]
pub(crate) fn get_db_groups_for_passkey(db_key: &str) -> Result<Vec<GroupInfo>> {
    kp_service::browser_extension::get_db_groups(db_key)
}

// Returns all entries in the given group for the passkey entry picker.
#[inline]
pub(crate) fn get_group_entries_for_passkey(
    db_key: &str,
    group_uuid: &Uuid,
) -> Result<Vec<EntryBasicInfo>> {
    kp_service::browser_extension::get_group_entries(db_key, group_uuid)
}

// Wraps the passkey list with a `browser_enabled_db_available` flag so the
// browser extension can distinguish "no DB open" from "no matching passkeys".
#[derive(Serialize)]
pub(crate) struct PasskeyListResult {
    browser_enabled_db_available: bool,
    passkey_list: Vec<PasskeySummary>,
}

// Searches all open databases for passkeys matching the given RP ID.
pub(crate) fn find_matching_passkeys(
    rp_id: &str,
    allow_credential_ids: Vec<String>,
) -> Result<PasskeyListResult> {
    let db_keys = kp_service::unlocked_kdbx_cache_keys()?;
    let browser_enabled_db_available = !db_keys.is_empty();
    let passkey_list = passkey_db::find_matching_passkeys(rp_id, allow_credential_ids)?;
    Ok(PasskeyListResult {
        browser_enabled_db_available,
        passkey_list,
    })
}

// Signs a WebAuthn assertion using the passkey stored in the given entry.
#[inline]
pub(crate) fn sign_passkey_assertion(
    db_key: &str,
    entry_uuid: &Uuid,
    options_json: &str,
    origin: &str,
) -> Result<String> {
    passkey_db::sign_passkey_assertion(db_key, entry_uuid, options_json, origin)
}
