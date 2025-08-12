
use onekeepass_core::db_service as kp_service;

use onekeepass_core::error::Result;
use serde::Serialize;

#[derive(Serialize)]
pub(crate) struct AllMatchedEntries {
    browser_enabled_db_available: bool,
    matched_entries: Vec<kp_service::browser_extension::MatchedDbEntries>,
}


pub(crate) fn find_matching_in_enabled_db_entries(input_url: &str) -> Result<AllMatchedEntries> {
    // TODO: Need to get only the browser enabaled databases
    let enabled_db_keys = kp_service::all_kdbx_cache_keys()?;

    let browser_enabled_db_available = !enabled_db_keys.is_empty();

    let entries = kp_service::browser_extension::find_matching_in_enabled_db_entries(
        &enabled_db_keys,
        input_url,
    )?;

    Ok(AllMatchedEntries {
        browser_enabled_db_available,
        matched_entries:entries,
    })
}

// #[derive(Serialize)]
// struct BrowserEnabledDb {
//     db_key: String,
//     file_name: String,
// }

// fn all_enabled_browser_databases() -> Result<Vec<BrowserEnabledDb>> {
//     let keys = kp_service::all_kdbx_cache_keys()?;

//     let dbs = keys
//         .iter()
//         .map(|v| {
//             let path = Path::new(v);
//             let file_name = path
//                 .file_name()
//                 .and_then(|name| name.to_str())
//                 .unwrap_or("");
//             BrowserEnabledDb {
//                 db_key: v.to_string(),
//                 file_name: file_name.to_string(),
//             }
//         })
//         .collect::<Vec<BrowserEnabledDb>>();
//     Ok(dbs)
// }

// fn test1() {
//     kp_service::browser_extension::find_matching_in_enabled_db_entries(enabled_db_keys, input_url)
// }
