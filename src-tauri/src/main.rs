#![cfg_attr(
  all(not(debug_assertions), target_os = "windows"),
  windows_subsystem = "windows"
)]

mod biometric;
mod commands;
mod key_secure;
mod menu;
mod preference;
mod utils;

use log::info;
use tauri::Manager;

pub type Result<T> = std::result::Result<T, String>;

#[derive(Clone, serde::Serialize)]
/// Payload to send to the UI layer
struct WindowEventPayload {
  action: String,
}

fn main() {
  let app = tauri::Builder::default()
    .manage(utils::AppState::new())
    .setup(|app| Ok(utils::init_app(app)))
    .menu(menu::get_app_menu())
    .on_menu_event(|menu_event| {
      menu::handle_menu_events(&menu_event);
    })
    .invoke_handler(tauri::generate_handler![
      // Sorted alphabetically
      commands::analyzed_password,
      commands::authenticate_with_biometric,
      commands::close_kdbx,
      commands::collect_entry_group_tags,
      commands::create_kdbx,
      commands::delete_custom_entry_type,
      commands::delete_history_entries,
      commands::delete_history_entry_by_index,
      commands::empty_trash,
      commands::entry_summary_data,
      commands::entry_type_headers,
      commands::export_as_xml,
      commands::export_main_content_as_xml,
      commands::get_categories_to_show,
      commands::get_db_settings,
      commands::get_entry_form_data_by_id,
      commands::get_group_by_id,
      commands::groups_summary_data,
      commands::history_entries_summary,
      commands::history_entry_by_index,
      commands::insert_entry_from_form_data,
      commands::insert_group,
      commands::insert_or_update_custom_entry_type,
      commands::is_path_exists,
      commands::kdbx_context_statuses,
      commands::load_custom_svg_icons,
      commands::load_kdbx,
      commands::lock_kdbx,
      commands::mark_group_as_category,
      commands::menu_action_requested,
      commands::move_entry,
      commands::move_entry_to_recycle_bin,
      commands::move_group,
      commands::move_group_to_recycle_bin,
      commands::new_blank_group,
      commands::new_entry_form_data,
      commands::read_and_verify_db_file,
      commands::read_app_preference,
      commands::reload_kdbx,
      commands::remove_entry_permanently,
      commands::remove_group_permanently,
      commands::save_all_modified_dbs,
      commands::save_as_kdbx,
      commands::save_kdbx,
      commands::score_password,
      commands::search_term,
      commands::set_db_settings,
      commands::standard_paths,
      commands::supported_biometric_type,
      commands::svg_file,
      commands::system_info_with_preference,
      commands::unlock_kdbx,
      commands::unlock_kdbx_on_biometric_authentication,
      commands::update_entry_from_form_data,
      commands::update_group,
      commands::upload_entry_attachment,
      /*
      commands::save_as_kdbx,
      commands::save_kdbx,
      commands::save_all_modified_dbs,
      commands::close_kdbx,
      commands::load_kdbx,
      commands::create_kdbx,
      commands::lock_kdbx,
      commands::unlock_kdbx,
      commands::read_and_verify_db_file,
      commands::collect_entry_group_tags,
      commands::groups_summary_data,
      commands::get_entry_form_data_by_id,
      commands::history_entry_by_index,
      commands::delete_history_entry_by_index,
      commands::delete_history_entries,
      commands::entry_summary_data,
      commands::history_entries_summary,
      commands::new_entry_form_data,
      commands::entry_type_headers,
      commands::insert_or_update_custom_entry_type,
      commands::delete_custom_entry_type,
      commands::get_group_by_id,
      commands::update_group,
      commands::update_entry_from_form_data,
      commands::upload_entry_attachment,
      commands::insert_entry_from_form_data,
      commands::new_blank_group,
      commands::insert_group,
      commands::get_categories_to_show,
      commands::mark_group_as_category,
      commands::move_group_to_recycle_bin,
      commands::remove_group_permanently,
      commands::move_group,
      commands::remove_entry_permanently,
      commands::move_entry_to_recycle_bin,
      commands::move_entry,
      commands::empty_trash,
      commands::search_term,
      commands::read_app_preference,
      commands::system_info_with_preference,
      commands::get_db_settings,
      commands::set_db_settings,
      commands::analyzed_password,
      commands::score_password,
      commands::menu_action_requested,
      commands::kdbx_context_statuses,
      commands::standard_paths,
      commands::is_path_exists,
      commands::export_main_content_as_xml,
      commands::export_as_xml,
      commands::load_custom_svg_icons,
      commands::svg_file,
      commands::save_key,
      commands::read_key,
      */
    ])
    .build(tauri::generate_context!())
    .expect("error while building tauri application");

  app.run(|app_handle, e| match e {
    tauri::RunEvent::Ready => {
      info!("Application is ready");
    }

    tauri::RunEvent::WindowEvent { label, event, .. } => {
      match event {
        tauri::WindowEvent::CloseRequested { api, .. } => {
          info!(
            "Window event is CloseRequested and will not be closed for window {}",
            label
          );
          let app_handle = app_handle.clone();
          let window = app_handle.get_window(&label).unwrap();
          // The Window CloseRequested event is in turn sent to the UI layer
          // so that user can be informed for any saved changes before quiting
          // See onekeepass.frontend.events.tauri-events/handle-main-window-event
          let _r = window.emit(
            "MainWindowEvent",
            WindowEventPayload {
              action: "CloseRequested".into(),
            },
          );
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
