#![cfg_attr(
  all(not(debug_assertions), target_os = "windows"),
  windows_subsystem = "windows"
)]

mod auto_type;
mod biometric;
mod commands;
mod constants;
mod key_secure;
mod menu;
mod preference;
mod utils;

use constants::event_action_names::*;
use constants::event_names::*;

use log::info;
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

  // TODO: Reuse this preference file content in Preference::read_toml 
  let pref_str = utils::read_preference_file();
  let lng = utils::read_language_selection(&pref_str);

  //println!("lng to use is {}", &lng);

  // This loaded tranalation data is passed to menus creation call (builder.menu(..)) and there they are used
  // in preparing the system menu names in the current prefered language. 
  // If user changes the prefered language the app needs to be restarted. This is because
  // the system menus's titles can not be changed without restarting though all sub menus's titles 
  // can be changed dynamically. 

  let menu_translation =
    utils::load_system_menu_translations(&lng, &context.config(), &context.package_info());

  // let rc_dir = tauri::api::path::resource_dir(context.package_info(),&tauri::Env::default());
  // println!("== Resource dir from context is {:?}",&rc_dir);

  // on_window_event - Registers a window event handler for all windows
  // Instead of using this, we register window events in App.run closure
  // See below

  let app = tauri::Builder::default()
    .manage(utils::AppState::new())
    .setup(|app| Ok(utils::init_app(app)))
    // .on_window_event(|event| match event.event() {
    //   tauri::WindowEvent::Focused(focused) => {
    //     if !focused {
    //     } else {
    //     }
    //   }
    //   _ => {}
    // })
    .menu(menu::get_app_menu(menu_translation))
    .on_menu_event(|menu_event| {
      menu::handle_menu_events(&menu_event);
    })
    .invoke_handler(tauri::generate_handler![
      // dev test calll
      // commands::test_call,

      // Sorted alphabetically
      commands::active_window_to_auto_type,
      commands::analyzed_password,
      commands::authenticate_with_biometric,
      commands::clear_recent_files,
      commands::close_kdbx,
      commands::collect_entry_group_tags,
      commands::combined_category_details,
      commands::create_kdbx,
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
      commands::get_db_settings,
      commands::get_entry_form_data_by_id,
      commands::get_group_by_id,
      commands::groups_summary_data,
      commands::history_entries_summary,
      commands::history_entry_by_index,
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
      commands::menu_titles_change_requested,
      commands::move_entry,
      commands::move_entry_to_recycle_bin,
      commands::move_group,
      commands::move_group_to_recycle_bin,
      commands::new_blank_group,
      commands::new_entry_form_data,
      commands::parse_auto_type_sequence,
      commands::platform_window_titles,
      commands::read_and_verify_db_file,
      commands::read_app_preference,
      commands::reload_kdbx,
      commands::remove_entry_permanently,
      commands::remove_group_permanently,
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
      commands::standard_paths,
      commands::start_polling_entry_otp_fields,
      commands::stop_polling_entry_otp_fields,
      commands::stop_polling_all_entries_otp_fields,
      commands::supported_biometric_type,
      commands::svg_file,
      commands::system_info_with_preference,
      commands::unlock_kdbx,
      commands::unlock_kdbx_on_biometric_authentication,
      commands::update_entry_from_form_data,
      commands::update_group,
      commands::upload_entry_attachment,
      commands:: update_preference,
    ])
    .build(context)
    .expect("error while building tauri application");

  // App is built
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
          let window = app_handle.get_window(&label).unwrap();
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
          let window = app_handle.get_window(&label).unwrap();
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
