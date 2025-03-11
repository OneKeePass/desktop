mod resolver;
use std::collections::HashMap;

use log::debug;
pub(crate) use resolver::{AutoOpenProperties, AutoOpenPropertiesResolved};

use onekeepass_core::db_service::{
  self as kp_service, AllTags, EntryCategories, EntryCategoryGrouping, EntryTypeHeaders, GroupTree,
  KdbxLoaded,
};

use onekeepass_core::error::Result;
use serde::Serialize;
use tauri::State;

use crate::app_state;

#[derive(Default, Serialize)]
pub(crate) struct AutoOpenDbsInfo {
  // All dbs that are opened successfully
  opened_dbs: Vec<AutoOpenedDbInitData>,

  // failed db keys with error message
  opening_failed_dbs: HashMap<String, String>,

  error_messages: Vec<String>,
}

// All relevant intial data that are used in UI on opening a database first time
#[derive(Serialize)]
pub(crate) struct AutoOpenedDbInitData {
  kdbx_loaded: KdbxLoaded,
  all_tags: AllTags,
  groups_tree: GroupTree,
  entry_categories: EntryCategories,
  entry_type_headers: EntryTypeHeaders,
}

pub(crate) fn open_all_auto_open_dbs(
  auto_open_db_key: &str,
  app_state: State<'_, app_state::AppState>,
) -> Result<AutoOpenDbsInfo> {
  let mut auto_open_dbs_info = AutoOpenDbsInfo::default();

  let grouping_kind: EntryCategoryGrouping =
    app_state.default_entry_category_groupings().as_str().into();

  inner_open_all_auto_open_dbs(auto_open_db_key, &mut auto_open_dbs_info, &grouping_kind);

  Ok(auto_open_dbs_info)
}

fn inner_open_all_auto_open_dbs(
  auto_open_db_key: &str,
  auto_open_dbs_info: &mut AutoOpenDbsInfo,
  grouping_kind: &EntryCategoryGrouping,
) {
  // Get all entries of "AutoOpen" group for this db_key
  // Here we are assuming all entries under auto open group are of auto open type
  // If there is any entry is found here, then the url parsing will fail as it will not be kdbx:// url
  // TODO: exclude non auto open entry type
  let entry_uuids =
    kp_service::auto_open_group_entry_uuids(auto_open_db_key).unwrap_or_else(|_| vec![]);

  // entry_uuids is empty when this db with db_key does not have AutoOpen group or when it does not have any entries
  if entry_uuids.is_empty() {
    return;
  }

  debug!(
    "Entry uuids found {:?} and  grouping_kind used is {:?} for db_key {}",
    &entry_uuids, grouping_kind, &auto_open_db_key
  );

  for entry_uuid in entry_uuids {
    // All kvs with required place holder parsing for an entry with 'entry_uuid' are collected
    let kvs_result = kp_service::entry_key_value_fields(auto_open_db_key, &entry_uuid);
    if let Err(e) = kvs_result {
      auto_open_dbs_info.error_messages.push(e.to_string());
      continue;
    }
    // unwrap is fine as we have checked for error
    let kvs = kvs_result.unwrap();

    // Prepare props to resolve
    let auto_open_properties = AutoOpenProperties {
      source_db_key: auto_open_db_key.to_string(),
      url_field_value: kvs.get(kp_service::entry_keyvalue_key::URL).cloned(),
      key_file_path: kvs.get(kp_service::entry_keyvalue_key::USER_NAME).cloned(),
      device_if_val: kvs.get(kp_service::entry_keyvalue_key::IF_DEVICE).cloned(),
    };

    // Here we resolve any {DB_DIR} place holder found in url and username fields
    // and make sure that the key file and db file are located

    let ao_val = auto_open_properties.resolve();
    if let Err(e) = ao_val {
      // As there are errors in resolving, we skip this entry uuid
      auto_open_dbs_info.error_messages.push(e.to_string());
      continue;
    }
    // unwrap is fine as we have checked for error
    let ao_resolved = ao_val.unwrap();

    // Proceed only if the db in 'url_field_value' can be opened in this device
    if !ao_resolved.can_open {
      debug!(
        "Can not open db from entry uuid {} on this device as it excluded",
        &entry_uuid
      );
      continue;
    }

    let password = kvs
      .get(kp_service::entry_keyvalue_key::PASSWORD)
      .map(|s| s.as_str());

    let kf_path = ao_resolved.key_file_path.as_deref();

    if let Some(db_key_to_open) = ao_resolved.url_field_value {
      // Condider only the dbs that are not yet opened
      if !kp_service::is_db_opened(&db_key_to_open) {
        // Note: load_kdbx will add this db_key to 'all_kdbx_cache'
        match kp_service::load_kdbx(&db_key_to_open, password, kf_path) {
          // On opening, load all init data
          Ok(kdbx_loaded) => match load_init_data(&grouping_kind, &kdbx_loaded) {
            Ok(auto_open_db) => {
              auto_open_dbs_info.opened_dbs.push(auto_open_db);
              // recursive call to check that the newly opened db has 'AutoOpen' group or not
              inner_open_all_auto_open_dbs(&kdbx_loaded.db_key, auto_open_dbs_info, grouping_kind);
            }
            // Init data loading of opened child db failed
            Err(e) => {
              auto_open_dbs_info
                .opening_failed_dbs
                .insert(db_key_to_open, e.to_string());
            }
          },
          // load_kdbx call of child db failed
          Err(e) => {
            auto_open_dbs_info
              .opening_failed_dbs
              .insert(db_key_to_open, e.to_string());
          }
        }
      }
    } else {
      auto_open_dbs_info.error_messages.push(format!(
        "Cound not form a valid url from {:?}",
        kvs.get(kp_service::entry_keyvalue_key::USER_NAME).cloned()
      ));
    }
  }
}

// Here we collect all data on opening a database and this is 
// based on the steps done in :common/kdbx-database-loading-complete of src/main/onekeepass/frontend/events/common.cljs
fn load_init_data(
  grouping_kind: &EntryCategoryGrouping,
  kdbx_loaded: &KdbxLoaded,
) -> Result<AutoOpenedDbInitData> {
  let child_db_key = &kdbx_loaded.db_key;

  let all_tags = kp_service::collect_entry_group_tags(child_db_key)?;

  let groups_tree = kp_service::groups_summary_data(&child_db_key)?;

  let entry_categories = kp_service::combined_category_details(child_db_key, grouping_kind)?;

  let entry_type_headers = kp_service::entry_type_headers(child_db_key)?;

  Ok(AutoOpenedDbInitData {
    kdbx_loaded: kdbx_loaded.clone(),
    all_tags,
    groups_tree,
    entry_categories,
    entry_type_headers,
  })
}