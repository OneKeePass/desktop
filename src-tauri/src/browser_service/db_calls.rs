use onekeepass_core::db_service as kp_service;

use onekeepass_core::error::Result;
use serde::Serialize;
use uuid::Uuid;

#[derive(Serialize)]
pub(crate) struct AllMatchedEntries {
    browser_enabled_db_available: bool,
    url: String,
    matched_entries: Vec<kp_service::browser_extension::MatchedDbEntries>,
}

pub(crate) fn find_matching_in_enabled_db_entries(input_url: &str) -> Result<AllMatchedEntries> {
    // TODO: Need to get only the browser enabaled databases
    let enabled_db_keys = kp_service::all_kdbx_cache_keys()?;

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

// Following will get the complete EntryFormData which is not required for browser extension for now
// Instead we use some basic entry detail only as done above

// pub(crate) fn entry_details_by_id(db_key: &str, entry_uuid: &Uuid) -> Result<kp_service::EntryFormData>  {
//     kp_service::get_entry_form_data_by_id(db_key, entry_uuid)
// }
