use tauri::utils::platform::resource_dir;
use tauri::Runtime;
use tauri::State;
use tauri::{command, Emitter, Env};

use std::collections::HashMap;
use std::io::ErrorKind;

use log::{debug, info};
use serde::{Deserialize, Serialize};
use std::fs::read_to_string;
use std::path::Path;
use uuid::Uuid;

use crate::app_state::SystemInfoWithPreference;
use crate::auto_open::{self, AutoOpenProperties, AutoOpenPropertiesResolved};
use crate::menu::MenuActionRequest;
use crate::{app_preference, app_state};
use crate::{auto_type, biometric, OTP_TOKEN_UPDATE_EVENT};
use crate::{menu, pass_phrase, translation};
use onekeepass_core::async_service as kp_async_service;
use onekeepass_core::db_service as kp_service;

#[derive(Clone, Serialize, Deserialize, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub enum UpdateType {
  GroupUpdate,
  EntryUpdate,
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub struct UpdatePayload {
  pub update_type: UpdateType,
}

pub type Result<T> = std::result::Result<T, String>;

#[tauri::command]
pub(crate) async fn init_timers<R: Runtime>(
  _app: tauri::AppHandle<R>,
  window: tauri::Window<R>,
  app_state: State<'_, app_state::AppState>,
) -> Result<()> {
  // Need to ensure, we call init_entry_channels once only and
  // also spawn fn should be called once
  // This is an issue when we do 'reload' during development
  // Also this may be an issue when 'bg-init-timers'is called more than once
  // for any reason. By keeping the status in app_state, we can prevent
  // the repeat calling
  if !app_state.is_timers_init_completed() {
    let mut rx = kp_async_service::init_entry_channels();
    debug!("Init timer is called in window {} ", window.label(),);

    // Need to listen for the periodic update of otp tokens
    kp_async_service::async_runtime().spawn(async move {
      loop {
        //debug!("Going to wait for value...");
        let reply: Option<kp_async_service::AsyncResponse> = rx.recv().await;

        //debug!("Received value as {:?}", &reply);

        // Only valid values are send to UI
        if let Some(ov) = reply {
          match ov {
            kp_async_service::AsyncResponse::EntryOtpToken(t) => {
              window.emit(OTP_TOKEN_UPDATE_EVENT, &t).unwrap();
            }
            kp_async_service::AsyncResponse::Tick(t) => {
              window.emit("TIMER_EVENT", &t).unwrap();
            }
            kp_async_service::AsyncResponse::ServiceStopped => {
              break;
            }
          }
        } else {
          debug!("No reply of type 'AsyncResponse' was received in channel");
        }
      }

      info!("Exited the AsyncResponse handling loop and closing channel receiver...");
      rx.close();
      info!("Closed receiver side of the channel");
    });
    app_state.timers_init_completed();
  }

  Ok(())
}

#[tauri::command]
pub(crate) async fn start_polling_entry_otp_fields(
  db_key: &str,
  entry_uuid: Uuid,
  otp_fields: kp_async_service::OtpTokenTtlInfoByField,
) -> Result<()> {
  kp_async_service::start_polling_entry_otp_fields(db_key, &entry_uuid, otp_fields);
  Ok(())
}

#[tauri::command]
pub(crate) async fn stop_polling_entry_otp_fields(_db_key: &str, entry_uuid: Uuid) -> Result<()> {
  kp_async_service::stop_polling_entry_otp_fields(&entry_uuid);
  Ok(())
}

#[tauri::command]
pub(crate) async fn stop_polling_all_entries_otp_fields(_db_key: &str) -> Result<()> {
  kp_async_service::stop_polling_all_entries_otp_fields();
  Ok(())
}

// #[tauri::command]
// pub(crate) async fn tokio_runtime_start() -> Result<()> {
//   kp_async_service::start_runtime();
//   Ok(())
// }

// #[tauri::command]
// pub(crate) async fn tokio_runtime_shutdown() -> Result<()> {
//   kp_async_service::shutdown_runtime();
//   Ok(())
// }

// ----------

#[command]
pub(crate) async fn load_kdbx(
  db_file_name: &str,
  password: Option<&str>,
  key_file_name: Option<&str>,
  app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxLoaded> {
  // key_file_name.as_deref() converts Option<String> to Option<&str> - https://stackoverflow.com/questions/31233938/converting-from-optionstring-to-optionstr

  let r = kp_service::load_kdbx(db_file_name, password, key_file_name.as_deref());

  if let Err(kp_service::error::Error::DbFileIoError(m, ioe)) = &r {
    // Remove from the recent list only if the file opening failed because of the file is not found in the passed file path
    if let ("Database file opening failed", ErrorKind::NotFound) = (m.as_str(), ioe.kind()) {
      app_state
        .preference
        .lock()
        .unwrap()
        .remove_recent_file(db_file_name);
      return Ok(r?);
    }
  }

  // Appends this file name to the most recently opened file list
  app_state
    .preference
    .lock()
    .unwrap()
    .add_recent_file(db_file_name);

  Ok(r?)
}

/// UI layer redirects any backend menu actions
#[tauri::command]
pub(crate) async fn menu_action_requested<R: Runtime>(
  app_handle: tauri::AppHandle<R>,
  request: MenuActionRequest,
) -> Result<()> {
  menu::menu_action_requested(request, &app_handle);
  Ok(())
}

#[command]
pub(crate) async fn is_path_exists(in_path: String) -> bool {
  Path::new(&in_path).exists()
}

#[tauri::command]
pub(crate) async fn read_app_preference(
  app_state: State<'_, app_state::AppState>,
) -> Result<app_preference::Preference> {
  let g = app_state.preference.lock().unwrap();
  Ok(g.clone())
}

#[tauri::command]
pub(crate) async fn system_info_with_preference<R: Runtime>(
  app: tauri::AppHandle<R>,
) -> Result<SystemInfoWithPreference> {
  Ok(SystemInfoWithPreference::init(app))
  // Ok(SystemInfoWithPreference::init(app_state.inner()))
}

#[tauri::command]
pub(crate) async fn update_preference(
  app_state: State<'_, app_state::AppState>,
  preference_data: app_preference::PreferenceData,
) -> Result<()> {
  Ok(app_state.update_preference(preference_data))
}

//clear_recent_files
#[tauri::command]
pub(crate) async fn clear_recent_files(app_state: State<'_, app_state::AppState>) -> Result<()> {
  Ok(app_state.clear_recent_files())
}

#[tauri::command]
pub(crate) async fn get_db_settings(db_key: &str) -> Result<kp_service::DbSettings> {
  Ok(kp_service::get_db_settings(db_key)?)
}

#[tauri::command]
pub(crate) async fn set_db_settings(
  db_key: &str,
  db_settings: kp_service::DbSettings,
) -> Result<()> {
  Ok(kp_service::set_db_settings(db_key, db_settings)?)
}

//generate_key_file
#[tauri::command]
pub(crate) async fn generate_key_file(key_file_name: &str) -> Result<()> {
  Ok(kp_service::generate_key_file(key_file_name)?)
}

#[tauri::command]
pub(crate) async fn create_kdbx(
  new_db: kp_service::NewDatabase,
  app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxLoaded> {
  let r = kp_service::create_kdbx(new_db)?;
  // Appends this file name to the most recently opned file list
  app_state
    .preference
    .lock()
    .unwrap()
    .add_recent_file(&r.db_key);
  Ok(r)
}

#[command]
pub(crate) async fn move_group_to_recycle_bin(db_key: &str, group_uuid: Uuid) -> Result<()> {
  Ok(kp_service::move_group_to_recycle_bin(db_key, group_uuid)?)
}

#[command]
pub(crate) async fn move_group(db_key: &str, group_uuid: Uuid, new_parent_id: Uuid) -> Result<()> {
  Ok(kp_service::move_group(db_key, group_uuid, new_parent_id)?)
}

#[command]
pub(crate) async fn move_entry_to_recycle_bin(db_key: &str, entry_uuid: Uuid) -> Result<()> {
  Ok(kp_service::move_entry_to_recycle_bin(db_key, entry_uuid)?)
}

#[command]
pub(crate) async fn move_entry(db_key: &str, entry_uuid: Uuid, new_parent_id: Uuid) -> Result<()> {
  Ok(kp_service::move_entry(db_key, entry_uuid, new_parent_id)?)
}

#[command]
pub(crate) async fn remove_group_permanently(db_key: &str, group_uuid: Uuid) -> Result<()> {
  Ok(kp_service::remove_group_permanently(db_key, group_uuid)?)
}

#[command]
pub(crate) async fn remove_entry_permanently(db_key: &str, entry_uuid: Uuid) -> Result<()> {
  Ok(kp_service::remove_entry_permanently(db_key, entry_uuid)?)
}

#[command]
pub(crate) async fn empty_trash(db_key: &str) -> Result<()> {
  Ok(kp_service::empty_trash(db_key)?)
}

#[command]
pub(crate) async fn kdbx_context_statuses(db_key: &str) -> Result<kp_service::KdbxContextStatus> {
  Ok(kp_service::kdbx_context_statuses(db_key)?)
}

#[command]
pub(crate) async fn get_entry_form_data_by_id(
  db_key: &str,
  entry_uuid: Uuid,
) -> Result<kp_service::EntryFormData> {
  Ok(kp_service::get_entry_form_data_by_id(&db_key, &entry_uuid)?)
}

#[command]
pub(crate) async fn entry_form_current_otp(
  db_key: &str,
  entry_uuid: Uuid,
  otp_field_name: &str,
) -> Result<kp_service::CurrentOtpTokenData> {
  Ok(kp_service::entry_form_current_otp(
    db_key,
    &entry_uuid,
    otp_field_name,
  )?)
}

#[tauri::command]
pub(crate) async fn entry_form_current_otps(
  db_key: &str,
  entry_uuid: Uuid,
  otp_field_names: Vec<String>,
) -> Result<HashMap<String, kp_service::CurrentOtpTokenData>> {
  Ok(kp_service::entry_form_current_otps(
    db_key,
    &entry_uuid,
    otp_field_names,
  )?)
}

#[command]
pub(crate) async fn form_otp_url(otp_settings: kp_service::OtpSettings) -> Result<String> {
  Ok(kp_service::form_otp_url(&otp_settings)?)
}

#[command]
pub(crate) async fn resolve_auto_open_properties(
  auto_open_properties: AutoOpenProperties,
) -> Result<AutoOpenPropertiesResolved> {
  Ok(auto_open_properties.resolve()?)
}

#[command]
pub(crate) async fn open_all_auto_open_dbs(
  db_key: &str,
  app_state: State<'_, app_state::AppState>,
) -> Result<auto_open::AutoOpenDbsInfo> {
  Ok(auto_open::open_all_auto_open_dbs(db_key, app_state)?)
}

#[command]
pub(crate) async fn auto_open_group_uuid(db_key: &str) -> Result<Option<Uuid>> {
  Ok(kp_service::auto_open_group_uuid(db_key)?)
}

#[command]
pub(crate) async fn history_entry_by_index(
  db_key: &str,
  entry_uuid: Uuid,
  index: i32,
) -> Result<kp_service::EntryFormData> {
  Ok(kp_service::history_entry_by_index(
    &db_key,
    &entry_uuid,
    index,
  )?)
}

#[command]
pub(crate) async fn delete_history_entry_by_index(
  db_key: &str,
  entry_uuid: Uuid,
  index: i32,
) -> Result<()> {
  Ok(kp_service::delete_history_entry_by_index(
    &db_key,
    &entry_uuid,
    index,
  )?)
}

#[command]
pub(crate) async fn delete_history_entries(db_key: &str, entry_uuid: Uuid) -> Result<()> {
  Ok(kp_service::delete_history_entries(&db_key, &entry_uuid)?)
}

#[command]
pub(crate) async fn groups_summary_data(db_key: String) -> Result<kp_service::GroupTree> {
  Ok(kp_service::groups_summary_data(&db_key)?)
}

#[tauri::command]
pub(crate) async fn entry_summary_data(
  db_key: String,
  entry_category: kp_service::EntryCategory,
) -> Result<Vec<kp_service::EntrySummary>> {
  Ok(kp_service::entry_summary_data(&db_key, entry_category)?)
}

#[tauri::command]
pub async fn history_entries_summary(
  db_key: &str,
  entry_uuid: Uuid,
) -> Result<Vec<kp_service::EntrySummary>> {
  Ok(kp_service::history_entries_summary(&db_key, &entry_uuid)?)
}

#[tauri::command]
pub(crate) async fn new_entry_form_data(
  db_key: &str,
  entry_type_uuid: Uuid,
  parent_group_uuid: Option<Uuid>,
) -> Result<kp_service::EntryFormData> {
  Ok(kp_service::new_entry_form_data_by_id(
    db_key,
    &entry_type_uuid,
    parent_group_uuid.as_ref().as_deref(),
  )?)
}

#[tauri::command]
pub(crate) async fn clone_entry(
  db_key: &str,
  entry_uuid: Uuid,
  entry_clone_option: kp_service::EntryCloneOption,
) -> Result<Uuid> {
  Ok(kp_service::clone_entry(
    db_key,
    &entry_uuid,
    &entry_clone_option,
  )?)
}

#[tauri::command]
pub(crate) async fn entry_type_headers(db_key: &str) -> Result<kp_service::EntryTypeHeaders> {
  Ok(kp_service::entry_type_headers(db_key)?)
}

#[tauri::command]
pub(crate) async fn insert_or_update_custom_entry_type(
  db_key: &str,
  entry_type_form_data: kp_service::EntryTypeFormData,
) -> Result<Uuid> {
  Ok(kp_service::insert_or_update_custom_entry_type(
    db_key,
    &entry_type_form_data,
  )?)
}

#[tauri::command]
pub(crate) async fn delete_custom_entry_type(
  db_key: &str,
  entry_type_uuid: Uuid,
) -> Result<kp_service::EntryTypeHeader> {
  Ok(kp_service::delete_custom_entry_type_by_id(
    db_key,
    &entry_type_uuid,
  )?)
}

#[tauri::command]
pub(crate) async fn get_group_by_id(db_key: String, group_uuid: Uuid) -> Result<kp_service::Group> {
  Ok(kp_service::get_group_by_id(&db_key, &group_uuid)?)
}

#[tauri::command]
pub(crate) async fn update_group(
  db_key: String,
  group: kp_service::Group,
  _window: tauri::Window,
) -> Result<()> {
  kp_service::update_group(&db_key, group)?;
  // Leaving it here as example to send an event from a command
  // let _r = window.emit(
  //   "group_update",
  //   UpdatePayload {
  //     update_type: UpdateType::GroupUpdate,
  //   },
  // );
  Ok(())
}

#[tauri::command]
pub(crate) async fn update_entry_from_form_data(
  db_key: &str,
  form_data: kp_service::EntryFormData,
) -> Result<()> {
  Ok(kp_service::update_entry_from_form_data(db_key, form_data)?)
}

#[tauri::command]
pub(crate) async fn insert_entry_from_form_data(
  db_key: &str,
  form_data: kp_service::EntryFormData,
) -> Result<()> {
  Ok(kp_service::insert_entry_from_form_data(db_key, form_data)?)
}

#[tauri::command]
pub(crate) async fn new_blank_group(mark_as_category: bool) -> kp_service::Group {
  kp_service::new_blank_group(mark_as_category)
}

#[tauri::command]
pub(crate) async fn insert_group(db_key: String, group: kp_service::Group) -> Result<()> {
  Ok(kp_service::insert_group(&db_key, group)?)
}

#[tauri::command]
pub(crate) async fn sort_sub_groups(
  db_key: String,
  group_uuid: Uuid,
  criteria: kp_service::GroupSortCriteria,
) -> Result<()> {
  Ok(kp_service::sort_sub_groups(
    &db_key,
    &group_uuid,
    &criteria,
  )?)
}

#[tauri::command]
pub(crate) async fn combined_category_details(
  db_key: String,
  grouping_kind: kp_service::EntryCategoryGrouping,
) -> Result<kp_service::EntryCategories> {
  Ok(kp_service::combined_category_details(
    &db_key,
    &grouping_kind,
  )?)
}

#[tauri::command]
pub(crate) async fn mark_group_as_category(
  db_key: String,
  group_id: String,
  _window: tauri::Window,
) -> Result<()> {
  kp_service::mark_group_as_category(&db_key, &group_id)?;
  //As the group data is modified, the "group_update" event is emitted and appropriate listener
  //in the UI reacts accordingly
  // let _r = window.emit(
  //   "group_update",
  //   UpdatePayload {
  //     update_type: UpdateType::GroupUpdate,
  //   },
  // );
  Ok(())
}

#[command]
pub(crate) async fn upload_entry_attachment(
  db_key: &str,
  file_name: &str,
) -> Result<kp_service::AttachmentUploadInfo> {
  Ok(kp_service::upload_entry_attachment(db_key, file_name)?)
}

#[command]
pub(crate) async fn save_attachment_as_temp_file(
  db_key: &str,
  name: &str,
  data_hash_str: &str,
) -> Result<String> {
  let data_hash = kp_service::service_util::parse_attachment_hash(data_hash_str)?;
  Ok(kp_service::save_attachment_as_temp_file(
    db_key, name, &data_hash,
  )?)
}

#[command]
pub(crate) async fn save_attachment_as(
  db_key: &str,
  full_file_name: &str,
  data_hash_str: &str,
) -> Result<()> {
  let data_hash = kp_service::service_util::parse_attachment_hash(data_hash_str)?;
  Ok(kp_service::save_attachment_as(
    db_key,
    full_file_name,
    &data_hash,
  )?)
}

#[command]
pub(crate) async fn save_as_kdbx(
  db_key: &str,
  db_file_name: &str,
  app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxLoaded> {
  //key_secure::copy_key(db_key, db_file_name)?;

  let r = kp_service::save_as_kdbx(db_key, db_file_name)?;

  //key_secure::delete_key(db_key);

  // Appends this file name to the most recently opened file list
  app_state
    .preference
    .lock()
    .unwrap()
    .add_recent_file(db_file_name);
  Ok(r)
}

#[command]
pub(crate) async fn save_kdbx(
  db_key: &str,
  overwrite: bool,
  app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxSaved> {
  // db_key is the full database file name and backup file name is derived from that
  let backup_file_name = app_state.get_backup_file(db_key);
  Ok(kp_service::save_kdbx_with_backup(
    db_key,
    backup_file_name.as_deref(),
    overwrite,
  )?)
}

#[command]
pub(crate) async fn save_to_db_file(db_key: &str, full_file_name: &str) -> Result<()> {
  Ok(kp_service::save_to_db_file(db_key, full_file_name)?)
}

#[tauri::command]
pub(crate) async fn save_all_modified_dbs(
  db_keys: Vec<String>,
  app_state: State<'_, app_state::AppState>,
) -> Result<Vec<kp_service::SaveAllResponse>> {
  // Need to prepare back file paths for all db_keys
  let dbs_with_backups: Vec<(String, Option<String>)> = db_keys
    .iter()
    .map(|s| (s.clone(), app_state.get_backup_file(s)))
    .collect();

  Ok(kp_service::save_all_modified_dbs_with_backups(
    dbs_with_backups,
  )?)
}

#[command]
pub(crate) async fn close_kdbx(db_key: &str) -> Result<()> {
  kp_service::close_kdbx(db_key)?;
  Ok(())
}

#[command]
pub(crate) async fn lock_kdbx(_db_key: &str) -> Result<()> {
  //TODO:
  // Need to remove the session encryption key from memory in 'key_secure' module
  // This key need to be retreived during 'unlock_kdbx' call

  Ok(())
}

#[command]
pub(crate) async fn unlock_kdbx_on_biometric_authentication(
  db_key: &str,
) -> Result<kp_service::KdbxLoaded> {
  Ok(kp_service::unlock_kdbx_on_biometric_authentication(db_key)?)
}

#[command]
pub(crate) async fn unlock_kdbx(
  db_key: &str,
  password: Option<&str>,
  key_file_name: Option<&str>,
) -> Result<kp_service::KdbxLoaded> {
  // We need to get the session encryption key from KeyChain(macOS)
  // In case of Linux and Windows, the key is kept in memory and need to use Linux and Windows specific credential stores
  // similiar to macOS KeyChain

  Ok(kp_service::unlock_kdbx(db_key, password, key_file_name)?)
}

#[command]
pub(crate) async fn read_and_verify_db_file(db_key: &str) -> Result<()> {
  Ok(kp_service::read_and_verify_db_file(db_key)?)
}

#[command]
pub(crate) async fn reload_kdbx(db_key: &str) -> Result<kp_service::KdbxLoaded> {
  Ok(kp_service::reload_kdbx(db_key)?)
}

#[command]
pub(crate) async fn merge_databases(
  target_db_key: &str,
  source_db_key: &str,
  password: Option<&str>,
  key_file_name: Option<&str>,
) -> Result<kp_service::MergeResult> {
  Ok(kp_service::merge_databases(
    target_db_key,
    source_db_key,
    password,
    key_file_name,
  )?)
}

#[command]
pub(crate) async fn import_csv_file(file_full_path: &str) -> Result<kp_service::CvsHeaderInfo> {
  Ok(kp_service::CsvImport::read_from_path(file_full_path, None)?)
}

#[command]
pub(crate) async fn clear_csv_data_cache() -> Result<()> {
  Ok(kp_service::CsvImport::clear_stored_records())
}

#[command]
pub(crate) async fn create_new_db_with_imported_csv(
  new_db: kp_service::NewDatabase,
  mapping: kp_service::CsvImportMapping,
  app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxLoaded> {
  let r = mapping.create_new_db(new_db)?;
  // Need to add to the most recent list as done in create_kdbx call
  app_state
    .preference
    .lock()
    .unwrap()
    .add_recent_file(&r.db_key);
  Ok(r)
}

#[command]
pub(crate) async fn update_db_with_imported_csv(
  db_key: &str,
  mapping: kp_service::CsvImportMapping,
) -> Result<()> {
  Ok(mapping.import_into_db(db_key)?)
}

#[command]
pub(crate) async fn collect_entry_group_tags(db_key: &str) -> Result<kp_service::AllTags> {
  Ok(kp_service::collect_entry_group_tags(db_key)?)
}

#[command]
pub(crate) async fn search_term(db_key: &str, term: &str) -> Result<kp_service::EntrySearchResult> {
  Ok(kp_service::search_term(db_key, term)?)
}

#[command]
pub(crate) async fn analyzed_password(
  password_options: kp_service::PasswordGenerationOptions,
) -> Result<kp_service::AnalyzedPassword> {
  Ok(password_options.analyzed_password()?)
}

#[command]
pub(crate) async fn generate_password_phrase(
  app: tauri::AppHandle,
  password_phrase_options: kp_service::PassphraseGenerationOptions,
) -> Result<kp_service::GeneratedPassPhrase> {
  let loader = pass_phrase::WordListLoaderImpl::new(app);
  // loader impl may be called to load word list file from resource in generate fn
  Ok(password_phrase_options.generate(&loader)?)
}

#[command]
pub(crate) async fn score_password(password: &str) -> Result<kp_service::PasswordScore> {
  Ok(password.into())
}

#[command]
pub(crate) async fn export_main_content_as_xml(db_key: &str, xml_file_name: &str) -> Result<()> {
  // This will just export the main content
  Ok(kp_service::export_main_content_as_xml(
    db_key,
    xml_file_name,
  )?)
}

#[command]
pub(crate) async fn export_as_xml(db_key: &str, xml_file_name: &str) -> Result<()> {
  // This will refresh struct before xml export
  Ok(kp_service::export_as_xml(db_key, xml_file_name)?)
}

#[tauri::command]
pub async fn load_language_translations<R: Runtime>(
  app: tauri::AppHandle<R>,
  language_ids: Vec<String>,
) -> Result<translation::TranslationResource> {
  Ok(translation::load_language_translations(&app, language_ids)?)
}

#[tauri::command]
pub async fn load_custom_svg_icons<R: Runtime>(
  app: tauri::AppHandle<R>,
) -> Result<HashMap<String, String>> {
  Ok(app_state::load_custom_svg_icons(&app))
}

// TODO: Remove this or need to clean up if required
// Leaving it here as example for the future use if any
#[tauri::command]
pub async fn svg_file<R: Runtime>(app: tauri::AppHandle<R>, name: &str) -> Result<String> {
  println!(
    "Resources dir is {:?}",
    resource_dir(app.package_info(), &Env::default())
  );
  // println!("Home dir is {:?}", home_dir());
  // println!("Runtime dir {:?}", runtime_dir());
  // println!("App Config dir {:?}", app_config_dir(&app.config().clone()));

  // let path = resolve_path(
  //   &app.config(),
  //   app.package_info(),
  //   &Env::default(),
  //   "../resources/public/icons/custom-svg",
  //   Some(BaseDirectory::Resource),
  // )
  // .unwrap();
  // println!("resolved path  is {:?}", path);

  let svg_path = resource_dir(app.package_info(), &Env::default())
    .unwrap()
    .join("_up_/resources/public/icons/custom-svg")
    .join(name);
  println!("svg_path  is {:?}", svg_path);
  let s = read_to_string(svg_path).unwrap();
  //Ok("Done".into())
  Ok(s)
}

#[tauri::command]
pub async fn supported_biometric_type() -> Result<String> {
  Ok(biometric::supported_biometric_type())
}

#[tauri::command]
pub async fn authenticate_with_biometric(db_key: &str) -> Result<bool> {
  Ok(biometric::authenticate_with_biometric(db_key))
}

///--------------   All auto typing command calls

#[tauri::command]
pub async fn parse_auto_type_sequence(
  sequence: &str,
  entry_fields: HashMap<String, String>,
) -> Result<Vec<auto_type::ParsedPlaceHolderVal>> {
  auto_type::parse_auto_type_sequence(sequence, &entry_fields)
}

#[tauri::command]
pub async fn platform_window_titles() -> Result<Vec<auto_type::WindowInfo>> {
  Ok(auto_type::window_titles()?)
}

#[tauri::command]
pub async fn active_window_to_auto_type() -> Option<auto_type::WindowInfo> {
  // None is returned if there is no other window is open other than the app
  auto_type::active_window_to_auto_type()
}

#[tauri::command]
pub async fn send_sequence_to_winow_async(
  db_key: &str,
  entry_uuid: Uuid,
  window_info: auto_type::WindowInfo,
  sequence: &str,
) -> Result<()> {
  let entry_fields = kp_service::entry_key_value_fields(db_key, &entry_uuid)?;
  Ok(auto_type::send_sequence_to_winow_async(window_info, sequence, entry_fields).await?)
}

// -------------- Test commands

// #[tauri::command]
// pub(crate) async fn test_call() -> Result<()> {
//   Ok(onekeepass_core::async_service::start())
// }

//
// #[tauri::command]
// pub(crate) async fn test_call(
//   arg:crate::auto_type::TestArg
// ) -> Result<()> {

//   Ok(crate::auto_type::test_call(arg))
// }
//

///////////////////////////////////////////////////////////////////////////////////////////////
// Tried these to use with macOS 13 and macOS 10. Only the async version will work with all macOS
/*
#[tauri::command]
pub async fn send_sequence_to_winow(db_key: &str,entry_uuid: Uuid,window_info:auto_type::WindowInfo,sequence:&str) -> Result<()> {
  let entry_fields = kp_service::entry_key_value_fields(db_key,&entry_uuid)?;
  Ok(auto_type::send_sequence_to_winow(window_info, sequence, entry_fields)?)
}

#[tauri::command]
pub fn send_sequence_to_winow_sync(db_key: &str,entry_uuid: Uuid,window_info:auto_type::WindowInfo,sequence:&str) -> Result<()> {
  let entry_fields = kp_service::entry_key_value_fields(db_key,&entry_uuid)?;
  Ok(auto_type::send_sequence_to_winow(window_info, sequence, entry_fields)?)
}

*/
