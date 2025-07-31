#![cfg_attr(
  all(not(debug_assertions), target_os = "windows"),
  windows_subsystem = "windows"
)]

mod app_paths;
mod app_preference;
mod app_state;
mod auto_open;
mod auto_type;
mod biometric;
mod browser_service;
mod commands;
mod constants;
mod file_util;
mod key_secure;
mod menu;
mod pass_phrase;
mod password_gen_preference;
mod translation;
// mod callback_service_provider;

use constants::event_action_names::*;
use constants::event_names::*;

use log::info;
use tauri::Emitter;
use tauri::Manager;

pub type Result<T> = std::result::Result<T, String>;

#[derive(Clone, serde::Serialize)]
// Payload to send to the UI layer
struct WindowEventPayload {
  action: String,
  focused: Option<bool>,
}

impl WindowEventPayload {
  fn new(action: &str) -> Self {
    Self {
      action: action.to_string(),
      focused: None,
    }
  }
}

fn main() {
  // Need to create 'context' here before building the app so that we can load language translation files
  // for the current prefered language from the resource dir in order to prepare Menus
  let context = tauri::generate_context!();

  // on_window_event - Registers a window event handler for all windows
  // Instead of using this, we register window events in App.run closure
  // See below

  let app = tauri::Builder::default()
    .plugin(tauri_plugin_dialog::init())
    .plugin(tauri_plugin_clipboard_manager::init())
    .plugin(tauri_plugin_global_shortcut::Builder::new().build())
    .plugin(tauri_plugin_process::init())
    .plugin(tauri_plugin_shell::init())
    .manage(app_state::AppState::new())
    .setup(|app| {
      app_state::init_app(app);
      Ok(menu::build_menus(app.app_handle())?)
    })
    // .on_window_event(|event| match event.event() {
    //   tauri::WindowEvent::Focused(focused) => {
    //     if !focused {
    //     } else {
    //     }
    //   }
    //   _ => {}
    // })
    // .menu(menu::get_app_menu(menu_translation))
    .on_menu_event(|app_handle, menu_event| {
      let _ = menu::handle_menu_events(app_handle, &menu_event);
    })
    .invoke_handler(tauri::generate_handler![
      // This is a test command that may be used to experiment any backend call during dev time 
      commands::test_call,

      // Sorted alphabetically
      commands::active_window_to_auto_type,
      commands::analyzed_password,
      commands::authenticate_with_biometric,
      commands::auto_open_group_uuid,
      commands::clear_csv_data_cache,
      commands::clear_recent_files,
      commands::clone_entry,
      commands::close_kdbx,
      commands::collect_entry_group_tags,
      commands::combined_category_details,
      commands::create_kdbx,
      commands::create_new_db_with_imported_csv,
      commands::delete_custom_entry_type,
      commands::delete_history_entries,
      commands::delete_history_entry_by_index,
      commands::empty_trash,
      commands::entry_form_current_otp,
      commands::entry_form_current_otps,
      commands::entry_summary_data,
      commands::entry_type_headers,
      commands::export_as_xml,
      commands::export_main_content_as_xml,
      commands::form_otp_url,
      commands::generate_key_file,
      commands::generate_password_phrase,
      commands::get_db_settings,
      commands::get_entry_form_data_by_id,
      commands::get_group_by_id,
      commands::groups_summary_data,
      commands::history_entries_summary,
      commands::history_entry_by_index,
      commands::import_csv_file,
      commands::insert_entry_from_form_data,
      commands::insert_group,
      commands::insert_or_update_custom_entry_type,
      commands::init_timers,
      commands::is_path_exists,
      commands::kdbx_context_statuses,
      commands::load_custom_svg_icons,
      commands::load_language_translations,
      commands::load_kdbx,
      commands::lock_kdbx,
      commands::mark_group_as_category,
      commands::menu_action_requested,
      commands::merge_databases,
      // commands::menu_titles_change_requested,
      commands::move_entry,
      commands::move_entry_to_recycle_bin,
      commands::move_group,
      commands::move_group_to_recycle_bin,
      commands::new_blank_group,
      commands::new_entry_form_data,
      commands::open_all_auto_open_dbs,
      commands::parse_auto_type_sequence,
      commands::platform_window_titles,
      commands::read_and_verify_db_file,
      commands::read_app_preference,
      commands::reload_kdbx,
      commands::remove_entry_permanently,
      commands::remove_group_permanently,
      commands::resolve_auto_open_properties,
      commands::save_all_modified_dbs,
      commands::save_as_kdbx,
      commands::save_attachment_as_temp_file,
      commands::save_attachment_as,
      commands::save_kdbx,
      commands::save_to_db_file,
      commands::score_password,
      commands::search_term,
      commands::set_db_settings,
      // commands::send_sequence_to_winow,
      // commands::send_sequence_to_winow_sync,
      commands::send_sequence_to_winow_async,
      commands::sort_sub_groups,
      // commands::standard_paths,
      commands::start_polling_entry_otp_fields,
      commands::stop_polling_entry_otp_fields,
      commands::stop_polling_all_entries_otp_fields,
      commands::supported_biometric_type,
      commands::svg_file,
      commands::system_info_with_preference,
      // commands::tokio_runtime_shutdown,
      // commands::tokio_runtime_start,
      commands::unlock_kdbx,
      commands::unlock_kdbx_on_biometric_authentication,
      commands::update_db_with_imported_csv,
      commands::update_entry_from_form_data,
      commands::update_group,
      commands::upload_entry_attachment,
      commands::update_preference,
    ])
    .build(context)
    .expect("error while building tauri application");

  // App is built and now the app
  app.run(|app_handle, e| match e {
    tauri::RunEvent::Ready => {
      info!("Application is ready");
    }

    tauri::RunEvent::WindowEvent { label, event, .. } => {
      match event {
        tauri::WindowEvent::Focused(focused) => {
          let app_handle = app_handle.clone();
          // window label will be 'main' for now as we have only
          // one window
          let window = app_handle.get_webview_window(&label).unwrap();
          let mut wr = WindowEventPayload::new(WINDOW_FOCUS_CHANGED);
          wr.focused = Some(focused);
          let _r = window.emit(MAIN_WINDOW_EVENT, wr);
        }
        tauri::WindowEvent::CloseRequested { api, .. } => {
          info!(
            "Window event is CloseRequested and will not be closed for window {}",
            label
          );
          let app_handle = app_handle.clone();
          let window = app_handle.get_webview_window(&label).unwrap();
          // The Window CloseRequested event is in turn sent to the UI layer
          // so that user can be informed for any saved changes before quiting
          // See onekeepass.frontend.events.tauri-events/handle-main-window-event
          let _r = window.emit(MAIN_WINDOW_EVENT, WindowEventPayload::new(CLOSE_REQUESTED));
          // "Main Window close requested"
          // use the exposed close api, and prevent the event loop to close
          // The window will be closed when UI side finally send the "Quit" event
          api.prevent_close();
        }
        _ => {}
      }
    }

    _ => {}
  })
}
