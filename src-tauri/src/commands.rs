use tauri::utils::platform::resource_dir;
use tauri::Runtime;
use tauri::State;
use tauri::{command, Emitter, Env};

use std::collections::HashMap;
use std::io::ErrorKind;

use log::{debug, info};
use std::fs::read_to_string;
use std::path::Path;
use uuid::Uuid;

use crate::app_state::SystemInfoWithPreference;
use crate::auto_open::{self, AutoOpenProperties, AutoOpenPropertiesResolved};
#[cfg(not(feature = "mas-build"))]
use crate::auto_type;
use crate::browser_service;
use crate::menu::MenuActionRequest;
use crate::{app_preference, app_state};
use crate::{biometric, OTP_TOKEN_UPDATE_EVENT};
#[cfg(not(feature = "mas-build"))]
use crate::updater;
use crate::{mas, menu, pass_phrase, translation};
use onekeepass_core::async_service as kp_async_service;
use onekeepass_core::db_service as kp_service;
use onekeepass_core::remote_storage::storage_service::{
    ConnectStatus, RemoteStorageOperation, RemoteStorageOperationType, RemoteStorageType,
    RemoteStorageTypeConfig, ServerDirEntry,
};

/*
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
*/

// IMPORTANT:
// All the internal errors (onekeepass_core::error::Error ) are converted to String
// See the 'From' implementation in onekeepass-core/src/error.rs

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
    let mut scoped_access = mas::LoadKdbxAccess::prepare(db_file_name, key_file_name);

    // key_file_name.as_deref() converts Option<&str>; same conversion the prior
    // implementation used.
    let r = kp_service::load_kdbx(db_file_name, password, key_file_name.as_deref());

    // Phase 2: classify the result. On NotFound, drop the recent entry. Any
    // started handle from Phase 1 must be released since we won't have a
    // session to bind it to.
    if let Err(kp_service::error::Error::DbFileIoError(m, ioe)) = &r {
        if let ("Database file opening failed", ErrorKind::NotFound) = (m.as_str(), ioe.kind()) {
            scoped_access.release_all();
            app_state
                .preference
                .lock()
                .unwrap()
                .remove_recent_file(db_file_name);
            return Ok(r?);
        }

        if mas::should_request_db_repick(ioe.kind(), db_file_name, &app_state) {
            scoped_access.release_all();
            return Err("BookmarkPermissionDenied".into());
        }
    }

    app_state
        .preference
        .lock()
        .unwrap()
        .add_recent_file(db_file_name);

    if r.is_ok() {
        scoped_access.store_success_handles(db_file_name, key_file_name, &app_state);
        app_state.db_file_watcher.start_watching(db_file_name);
    } else {
        // Failure path with a held handle (e.g., wrong password). The user
        // will likely retry, but we shouldn't leak scoped access in the
        // meantime. The next attempt will resolve again from the stored bookmark.
        scoped_access.release_all();
    }

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
}

#[tauri::command]
pub(crate) async fn update_preference(
    app_state: State<'_, app_state::AppState>,
    preference_data: app_preference::PreferenceData,
) -> Result<()> {
    Ok(app_state.update_preference(preference_data)?)
}

#[tauri::command]
pub(crate) async fn update_browser_ext_support_preference(
    app_state: State<'_, app_state::AppState>,
    browser_ext_support: app_preference::BrowserExtSupportData,
) -> Result<()> {
    Ok(app_state.update_browser_ext_support(browser_ext_support)?)
}

#[tauri::command]
pub(crate) async fn browser_ext_use_user_permission(
    app_state: State<'_, app_state::AppState>,
    browser_id: &str,
    confirmed: bool,
) -> Result<()> {
    Ok(app_state.browser_ext_use_user_permission(browser_id, confirmed))
}

// Opens a folder picker (Powerbox-vended NSOpenPanel under macOS App Sandbox)
// pre-targeted at the browser's standard NativeMessagingHosts directory.
// On confirmation: creates a security-scoped bookmark for the picked folder,
// persists it, and writes the native-messaging manifest within the granted scope.
// On cancellation: returns Ok(()) — the cljs side may re-attempt on next save.
#[tauri::command]
pub(crate) async fn browser_ext_pick_install_dir<R: tauri::Runtime>(
    app: tauri::AppHandle<R>,
    app_state: State<'_, app_state::AppState>,
    browser_id: String,
) -> Result<()> {
    Ok(app_state.browser_ext_pick_install_dir(&app, &browser_id)?)
}

#[tauri::command]
pub(crate) async fn browser_ext_manifest_statuses()
    -> Result<Vec<browser_service::NativeMessagingManifestStatus>>
{
    Ok(browser_service::native_messaging_manifest_statuses()?)
}

//clear_recent_files
#[tauri::command]
pub(crate) async fn clear_recent_files(app_state: State<'_, app_state::AppState>) -> Result<()> {
    Ok(app_state.clear_recent_files())
}

// remove_recent_file
#[tauri::command]
pub(crate) async fn remove_recent_file(
    file_name: &str,
    app_state: State<'_, app_state::AppState>,
) -> Result<()> {
    Ok(app_state.remove_recent_file(file_name))
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
    mas::record_user_granted_db_file(&r.db_key, &app_state);
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
pub(crate) async fn move_entry_to_other_db(
    source_db_key: &str,
    entry_uuid: Uuid,
    target_db_key: &str,
    target_parent_group_uuid: Uuid,
) -> Result<kp_service::CrossDbMoveSummary> {
    Ok(kp_service::move_entry_to_other_db(
        source_db_key,
        &entry_uuid,
        target_db_key,
        &target_parent_group_uuid,
    )?)
}

#[command]
pub(crate) async fn move_group_to_other_db(
    source_db_key: &str,
    group_uuid: Uuid,
    target_db_key: &str,
    target_parent_group_uuid: Uuid,
) -> Result<kp_service::CrossDbMoveSummary> {
    Ok(kp_service::move_group_to_other_db(
        source_db_key,
        &group_uuid,
        target_db_key,
        &target_parent_group_uuid,
    )?)
}

#[command]
pub(crate) async fn clone_entry_to_other_db(
    source_db_key: &str,
    entry_uuid: Uuid,
    target_db_key: &str,
    target_parent_group_uuid: Uuid,
) -> Result<kp_service::CrossDbCloneSummary> {
    Ok(kp_service::clone_entry_to_other_db(
        source_db_key,
        &entry_uuid,
        target_db_key,
        &target_parent_group_uuid,
    )?)
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
pub(crate) async fn open_attachment_temp_file(file_path: &str) -> Result<()> {
    let requested = std::path::PathBuf::from(file_path);
    let canonical =
        std::fs::canonicalize(&requested).map_err(|e| format!("Invalid attachment path: {e}"))?;

    let allowed_root = std::fs::canonicalize(std::env::temp_dir().join("okp_cache"))
        .map_err(|e| format!("okp_cache dir not available: {e}"))?;

    if !canonical.starts_with(&allowed_root) {
        return Err("Attachment path is outside the allowed temp directory".into());
    }

    open::that_detached(&canonical).map_err(|e| format!("Failed to open file: {e}"))
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

    mas::record_user_granted_db_file(db_file_name, &app_state);
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
    // Remote dbs (Sftp-/Webdav- prefixed db_keys) cannot go through the local
    // backup-then-rename path: there is no local file to write to, the
    // backup-file resolver would produce a nonsense path containing slashes
    // from the prefix, and the local checksum-based external-change check
    // would always fire. Route them to the remote save path which does its
    // own mtime-based conflict detection against the remote server.
    if crate::remote_storage::is_remote_db_key(db_key) {
        let recorded_mtime = app_state.remote_mtime(db_key);
        let backup_file_name = app_state.get_backup_file(db_key);
        let db_key_owned = db_key.to_string();
        let db_key_for_cache = db_key_owned.clone();
        let (kdbx_saved, remote_mtime) = tokio::task::spawn_blocking(move || {
            crate::remote_storage::rs_save_kdbx(
                &db_key_owned,
                overwrite,
                recorded_mtime,
                backup_file_name.as_deref(),
            )
        })
        .await
        .map_err(spawn_blocking_join_err)??;
        app_state.set_remote_mtime(&db_key_for_cache, remote_mtime);
        return Ok(kdbx_saved);
    }

    // db_key is the full database file name and backup file name is derived from that
    let backup_file_name = app_state.get_backup_file(db_key);
    let _db_file_access = mas::db_file_access(db_key);
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
    // Partition local vs remote. Local dbs go through the existing batched
    // save-with-backup path; remote dbs are saved one-by-one via the remote
    // save path (which handles its own mtime-based conflict detection and
    // does not use any local backup file).
    let (remote_keys, local_keys): (Vec<String>, Vec<String>) = db_keys
        .into_iter()
        .partition(|k| crate::remote_storage::is_remote_db_key(k));

    let _db_file_access: Vec<_> = local_keys
        .iter()
        .map(|db_key| mas::db_file_access(db_key))
        .collect();

    let dbs_with_backups: Vec<(String, Option<String>)> = local_keys
        .iter()
        .map(|s| (s.clone(), app_state.get_backup_file(s)))
        .collect();

    let mut results = kp_service::save_all_modified_dbs_with_backups(dbs_with_backups)?;

    for db_key in remote_keys {
        let recorded_mtime = app_state.remote_mtime(&db_key);
        let backup_file_name = app_state.get_backup_file(&db_key);
        let db_key_for_cache = db_key.clone();
        let db_key_for_task = db_key.clone();
        let response = match tokio::task::spawn_blocking(move || {
            crate::remote_storage::rs_save_kdbx(
                &db_key_for_task,
                false,
                recorded_mtime,
                backup_file_name.as_deref(),
            )
        })
        .await
        .map_err(spawn_blocking_join_err)?
        {
            Ok((_kdbx_saved, remote_mtime)) => {
                app_state.set_remote_mtime(&db_key_for_cache, remote_mtime);
                kp_service::SaveAllResponse {
                    db_key,
                    save_status: kp_service::SaveStatus::Success,
                }
            }
            Err(e) => kp_service::SaveAllResponse {
                db_key,
                save_status: kp_service::SaveStatus::Failed(format!("{}", e)),
            },
        };
        results.push(response);
    }

    Ok(results)
}

#[command]
pub(crate) async fn close_kdbx(
    db_key: &str,
    app_state: State<'_, app_state::AppState>,
) -> Result<()> {
    app_state.db_file_watcher.stop_watching(db_key);
    // Release the scoped-access handle paired with this DB's load_kdbx (if any).
    // Safe to call on non-macOS / non-sandboxed paths — it's a HashMap remove.
    app_state.release_scoped_access(&mas::ScopedAccessKey::Db(db_key.to_string()));
    kp_service::close_kdbx(db_key)?;
    app_state.remove_app_home_backup_file(db_key);
    // Drop any in-memory connection config cached while this remote db was open.
    crate::remote_storage::clear_cached_connection_config(db_key);
    Ok(())
}

#[command]
pub(crate) async fn merge_kdbx_with_disk_version(
    db_key: &str,
    app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::MergeResult> {
    let result = kp_service::merge_kdbx_with_disk_version(db_key)?;
    app_state.db_file_watcher.clear_notification_pending(db_key);
    Ok(result)
}

#[command]
pub(crate) async fn acknowledge_db_file_change(
    db_key: &str,
    app_state: State<'_, app_state::AppState>,
) -> Result<()> {
    app_state.db_file_watcher.clear_notification_pending(db_key);
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
    mas::record_user_granted_db_file(&r.db_key, &app_state);
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

#[cfg(not(feature = "mas-build"))]
#[tauri::command]
pub async fn parse_auto_type_sequence(
    sequence: &str,
    entry_fields: HashMap<String, String>,
) -> Result<Vec<auto_type::ParsedPlaceHolderVal>> {
    auto_type::parse_auto_type_sequence(sequence, &entry_fields)
}

#[cfg(not(feature = "mas-build"))]
#[tauri::command]
pub async fn platform_window_titles() -> Result<Vec<auto_type::WindowInfo>> {
    Ok(auto_type::window_titles()?)
}

#[cfg(not(feature = "mas-build"))]
#[tauri::command]
pub async fn active_window_to_auto_type() -> Option<auto_type::WindowInfo> {
    // None is returned if there is no other window is open other than the app
    auto_type::active_window_to_auto_type()
}

#[cfg(not(feature = "mas-build"))]
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
//     log::debug!("test_call is called and going to call browser_service::start_proxy_handler(");
//     Ok(browser_service::start_proxy_handler())
// }

// #[tauri::command]
// pub(crate) async fn test_simulate_verified_flag_preference(confirmed: bool) -> Result<()> {
//     log::debug!("test_simulate_verified_flag_preference is called");
//     Ok(browser_service::simulate_verified_flag_preference(confirmed).await)
// }

// #[tauri::command]
// pub(crate) async fn test_simulate_run_verifier(confirmed: bool) -> Result<()> {
//     log::debug!("test_simulate_run_verifier is called");
//     Ok(browser_service::run_verifier(confirmed).await)
// }

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

///-------------- Custom icon commands

#[tauri::command]
pub async fn list_custom_icons(db_key: &str) -> Result<Vec<kp_service::CustomIconSummary>> {
    Ok(kp_service::list_custom_icons(db_key)?)
}

#[tauri::command]
pub async fn get_custom_icon_data(db_key: &str, custom_icon_uuid: &str) -> Result<String> {
    let data = kp_service::get_custom_icon(db_key, custom_icon_uuid)?.data;
    Ok(data_encoding::BASE64.encode(&data))
}

#[tauri::command]
pub async fn add_custom_icon_from_url(
    db_key: &str,
    url: &str,
) -> Result<kp_service::CustomIconSummary> {
    let png = onekeepass_core::favicon::download_favicon(url)
        .await
        .map_err(|e| e.to_string())?;
    // Extract hostname as icon name without pulling in the url crate.
    // Strip scheme ("https://"), then take up to the first '/' or end of string.
    let name = url
        .split("://")
        .nth(1)
        .unwrap_or(url)
        .split('/')
        .next()
        .unwrap_or(url)
        .to_string();
    let uuid = kp_service::add_custom_icon(db_key, name, png)?;
    let icons = kp_service::list_custom_icons(db_key)?;
    icons
        .into_iter()
        .find(|i| i.uuid == uuid)
        .ok_or_else(|| "Icon not found after add".to_string())
}

#[tauri::command]
pub async fn add_custom_icon_from_file(
    db_key: &str,
    file_path: &str,
) -> Result<kp_service::CustomIconSummary> {
    let bytes = std::fs::read(file_path).map_err(|e| e.to_string())?;
    let png =
        onekeepass_core::favicon::normalize_image_to_png(&bytes, 64).map_err(|e| e.to_string())?;
    let name = std::path::Path::new(file_path)
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("icon")
        .to_string();
    let uuid = kp_service::add_custom_icon(db_key, name, png)?;
    let icons = kp_service::list_custom_icons(db_key)?;
    icons
        .into_iter()
        .find(|i| i.uuid == uuid)
        .ok_or_else(|| "Icon not found after add".to_string())
}

#[tauri::command]
pub async fn remove_custom_icon(db_key: &str, custom_icon_uuid: &str) -> Result<()> {
    Ok(kp_service::remove_custom_icon(db_key, custom_icon_uuid)?)
}

#[tauri::command]
pub async fn set_entry_custom_icon(
    db_key: &str,
    entry_uuid: &str,
    custom_icon_uuid: Option<String>,
) -> Result<()> {
    Ok(kp_service::set_entry_custom_icon(
        db_key,
        entry_uuid,
        custom_icon_uuid,
    )?)
}

#[tauri::command]
pub async fn set_group_custom_icon(
    db_key: &str,
    group_uuid: &str,
    custom_icon_uuid: Option<String>,
) -> Result<()> {
    Ok(kp_service::set_group_custom_icon(
        db_key,
        group_uuid,
        custom_icon_uuid,
    )?)
}

// ----- Remote storage (SFTP / WebDAV) commands -----
//
// Desktop is kdbx-only: connection configs live as entries inside open
// databases, not in a local blob store. There are no rs_remote_storage_configs
// / rs_read_configs / rs_delete_config commands on desktop — those are the
// blob-store CRUD surface that only mobile exposes. To delete a desktop
// connection the user edits or deletes the kdbx entry directly.

// The SFTP/WebDAV layer in onekeepass-core uses
// `tokio::sync::oneshot::Receiver::blocking_recv()` (via the
// `receive_from_async_fn!` macro) which panics if called from a thread that
// is being driven by an async executor. Tauri's async commands run on its
// Tokio worker pool, so each command that ends up in the macro must hand the
// blocking work off via `tokio::task::spawn_blocking`. AppState mtime cache
// updates are applied around the spawn_blocking call to avoid borrowing
// `State` into the 'static closure.

fn spawn_blocking_join_err(e: tokio::task::JoinError) -> String {
    format!("Remote storage worker join error: {}", e)
}

#[tauri::command]
pub async fn rs_connect_and_retrieve_root_dir(
    rs_operation_type: RemoteStorageOperationType,
) -> Result<ConnectStatus> {
    Ok(tokio::task::spawn_blocking(move || {
        rs_operation_type.connect_and_retrieve_root_dir()
    })
    .await
    .map_err(spawn_blocking_join_err)??)
}

#[tauri::command]
pub async fn rs_connect_by_id_and_retrieve_root_dir(
    rs_operation_type: RemoteStorageOperationType,
) -> Result<ConnectStatus> {
    Ok(tokio::task::spawn_blocking(move || {
        rs_operation_type.connect_by_id_and_retrieve_root_dir()
    })
    .await
    .map_err(spawn_blocking_join_err)??)
}

#[tauri::command]
pub async fn rs_list_sub_dir(
    rs_operation_type: RemoteStorageOperationType,
) -> Result<ServerDirEntry> {
    Ok(tokio::task::spawn_blocking(move || rs_operation_type.list_sub_dir())
        .await
        .map_err(spawn_blocking_join_err)??)
}

#[tauri::command]
pub async fn rs_read_kdbx(
    db_file_name: String,
    password: Option<String>,
    key_file_name: Option<String>,
    app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxLoaded> {
    let db_key_for_cache = db_file_name.clone();
    let (kdbx_loaded, remote_mtime) = tokio::task::spawn_blocking(move || {
        crate::remote_storage::rs_read_kdbx(
            &db_file_name,
            password.as_deref(),
            key_file_name.as_deref(),
        )
    })
    .await
    .map_err(spawn_blocking_join_err)??;
    app_state.set_remote_mtime(&db_key_for_cache, remote_mtime);
    if crate::remote_storage::is_kdbx_entry_backed(&db_key_for_cache) {
        app_state
            .preference
            .lock()
            .unwrap()
            .add_recent_file(&db_key_for_cache);
    }
    Ok(kdbx_loaded)
}

#[tauri::command]
pub async fn rs_save_kdbx(
    db_key: String,
    overwrite: bool,
    app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxSaved> {
    let recorded_mtime = app_state.remote_mtime(&db_key);
    let backup_file_name = app_state.get_backup_file(&db_key);
    let db_key_for_cache = db_key.clone();
    let (kdbx_saved, remote_mtime) = tokio::task::spawn_blocking(move || {
        crate::remote_storage::rs_save_kdbx(
            &db_key,
            overwrite,
            recorded_mtime,
            backup_file_name.as_deref(),
        )
    })
    .await
    .map_err(spawn_blocking_join_err)??;
    app_state.set_remote_mtime(&db_key_for_cache, remote_mtime);
    Ok(kdbx_saved)
}

#[tauri::command]
pub async fn rs_create_kdbx(
    new_db: kp_service::NewDatabase,
    app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::KdbxLoaded> {
    let backup_file_name = app_state.get_backup_file(&new_db.database_file_name);
    let (kdbx_loaded, db_key, remote_mtime) =
        tokio::task::spawn_blocking(move || {
            crate::remote_storage::rs_create_kdbx(new_db, backup_file_name.as_deref())
        })
        .await
        .map_err(spawn_blocking_join_err)??;
    app_state.set_remote_mtime(&db_key, remote_mtime);
    if crate::remote_storage::is_kdbx_entry_backed(&db_key) {
        app_state
            .preference
            .lock()
            .unwrap()
            .add_recent_file(&db_key);
    }
    Ok(kdbx_loaded)
}

// Refreshes the cached remote mtime to the current server value
// without touching the in-memory db. Called when the user chooses
// "Ignore" on the conflict dialog for a remote db, so the next
// focus-poll doesn't re-prompt for the same diverged state.
#[tauri::command]
pub async fn rs_acknowledge_remote_change(
    db_key: String,
    app_state: State<'_, app_state::AppState>,
) -> Result<()> {
    let db_key_for_call = db_key.clone();
    let current = tokio::task::spawn_blocking(move || {
        crate::remote_storage::rs_current_remote_mtime(&db_key_for_call)
    })
    .await
    .map_err(spawn_blocking_join_err)??;
    app_state.set_remote_mtime(&db_key, current);
    Ok(())
}

// Returns true when the remote file's mtime has diverged from what was
// recorded at open / last save. Polled by the frontend on window-focus
// for every open remote db. A false-positive-free design: when either
// the recorded value or the current value is missing (some servers
// don't report mtime), returns false rather than prompting the user.
#[tauri::command]
pub async fn rs_check_remote_modified(
    db_key: String,
    app_state: State<'_, app_state::AppState>,
) -> Result<bool> {
    let recorded = app_state.remote_mtime(&db_key);
    let Some(recorded) = recorded else {
        return Ok(false);
    };
    let db_key_for_call = db_key.clone();
    let current = tokio::task::spawn_blocking(move || {
        crate::remote_storage::rs_current_remote_mtime(&db_key_for_call)
    })
    .await
    .map_err(spawn_blocking_join_err)??;
    let Some(current) = current else {
        return Ok(false);
    };
    Ok(current != recorded)
}

// Downloads the current remote bytes and merges them into the in-memory
// db using the stored composite key (no credential re-entry). On success
// the in-memory db is marked save_pending, and the recorded mtime is
// updated to the just-downloaded version so the user isn't immediately
// re-prompted by the next focus-poll.
#[tauri::command]
pub async fn rs_merge_with_remote(
    db_key: String,
    app_state: State<'_, app_state::AppState>,
) -> Result<kp_service::MergeResult> {
    let db_key_for_cache = db_key.clone();
    let (merge_result, remote_mtime) = tokio::task::spawn_blocking(move || {
        crate::remote_storage::rs_merge_with_remote(&db_key)
    })
    .await
    .map_err(spawn_blocking_join_err)??;
    app_state.set_remote_mtime(&db_key_for_cache, remote_mtime);
    Ok(merge_result)
}

#[tauri::command]
pub async fn rs_list_kdbx_source_connections(
    rs_storage_type: RemoteStorageType,
) -> Result<Vec<kp_service::RemoteConnectionEntrySummary>> {
    Ok(crate::remote_storage::list_kdbx_source_connections(
        rs_storage_type,
    )?)
}

#[tauri::command]
pub async fn rs_get_remote_storage_config(
    rs_storage_type: RemoteStorageType,
    connection_id: &str,
) -> Result<RemoteStorageTypeConfig> {
    Ok(crate::remote_storage::get_remote_storage_config(
        rs_storage_type,
        connection_id,
    )?)
}

// Mac App Store builds: Apple requires updates to flow through the App
// Store, so we omit the update-check command entirely. The cljs side
// silently no-ops on missing-command errors via the :silent? branch.
#[cfg(not(feature = "mas-build"))]
#[tauri::command]
pub(crate) async fn check_for_updates(
    app_state: State<'_, app_state::AppState>,
) -> Result<updater::UpdateCheckResult> {
    let current_version = app_state.app_version();
    updater::check_for_updates(current_version).await
}

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
