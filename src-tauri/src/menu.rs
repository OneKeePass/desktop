use std::collections::HashMap;

use crate::{constants::event_names::TAURI_MENU_EVENT, utils::SystemMenuTranslation};
use log::info;
use onekeepass_core::db_service as kp_service;
use serde::{Deserialize, Serialize};
use tauri::{
  AboutMetadata, AppHandle, CustomMenuItem, Manager, Menu, MenuItem, Runtime, Submenu,
  WindowMenuEvent,
};

#[allow(dead_code)]
pub mod menu_ids {
  pub const QUIT: &str = "Quit";
  pub const APP_SETTINGS: &str = "AppSettings";
  pub const NEW_DATABASE: &str = "NewDatabase";
  pub const OPEN_DATABASE: &str = "OpenDatabase";
  pub const SAVE_DATABASE: &str = "SaveDatabase";
  pub const SAVE_DATABASE_AS: &str = "SaveDatabaseAs";
  pub const SAVE_DATABASE_BACKUP: &str = "SaveDatabaseBackup";
  pub const CLOSE_DATABASE: &str = "CloseDatabase";
  pub const LOCK_DATABASE: &str = "LockDatabase";
  pub const LOCK_ALL_DATABASES: &str = "LockAllDatabases";

  pub const NEW_GROUP: &str = "NewGroup";
  pub const EDIT_GROUP: &str = "EditGroup";

  pub const SEARCH: &str = "Search";
  pub const NEW_ENTRY: &str = "NewEntry";
  pub const EDIT_ENTRY: &str = "EditEntry";
  pub const PASSWORD_GENERATOR: &str = "PasswordGenerator";
}
use menu_ids::*;

pub fn get_app_menu(system_menu_translation: SystemMenuTranslation) -> Menu {
  let app_name = "OneKeePass";

  let app_settings = CustomMenuItem::new(
    APP_SETTINGS,
    system_menu_translation.sub_menu(APP_SETTINGS, "Settings..."),
  ).accelerator("CmdOrControl+,");

  let quit = CustomMenuItem::new(
    QUIT,
    system_menu_translation.sub_menu(QUIT, "Quit OneKeePass"),
  ).accelerator("CmdOrControl+Q");

  #[allow(unused_mut)]
  let mut first_menu;
  #[cfg(target_os = "macos")]
  {
    let about = MenuItem::About(app_name.to_string(), AboutMetadata::default());
    first_menu = Menu::with_items([
      about.into(),
      MenuItem::Separator.into(),
      app_settings.into(),
      MenuItem::Separator.into(),
      quit.into(),
    ]);
  }

  #[cfg(not(target_os = "macos"))]
  {
    first_menu = Menu::with_items([quit.into()]);
  }

  // In mac these are needed for Cut, Copy and Paste to work when user uses keyboard
  let edit_sub_menu = Menu::with_items([
    MenuItem::Cut.into(),
    MenuItem::Copy.into(),
    MenuItem::Paste.into(),
    MenuItem::Separator.into(),
    CustomMenuItem::new(SEARCH, system_menu_translation.sub_menu(SEARCH, "Search"))
      .accelerator("CmdOrControl+F")
      .disabled()
      .into(),
  ]);

  let database_sub_menu = Menu::with_items([
    CustomMenuItem::new(
      NEW_DATABASE,
      system_menu_translation.sub_menu(NEW_DATABASE, "New Database"),
    )
    .accelerator("Shift+CmdOrControl+N")
    .into(),
    CustomMenuItem::new(
      OPEN_DATABASE,
      system_menu_translation.sub_menu(OPEN_DATABASE, "Open Database"),
    )
    .accelerator("CmdOrControl+O")
    .into(),
    CustomMenuItem::new(
      SAVE_DATABASE,
      system_menu_translation.sub_menu(SAVE_DATABASE, "Save Database"),
    )
    .accelerator("CmdOrControl+S")
    .disabled()
    .into(),
    CustomMenuItem::new(
      SAVE_DATABASE_AS,
      system_menu_translation.sub_menu(SAVE_DATABASE_AS, "Save Database As"),
    )
    .accelerator("Shift+CmdOrControl+S")
    .disabled()
    .into(),
    CustomMenuItem::new(
      SAVE_DATABASE_BACKUP,
      system_menu_translation.sub_menu(SAVE_DATABASE_BACKUP, "Save Database Backup"),
    )
    .disabled()
    .into(),
    CustomMenuItem::new(
      CLOSE_DATABASE,
      system_menu_translation.sub_menu(CLOSE_DATABASE, "Close Database"),
    )
    .accelerator("CmdOrControl+W")
    .disabled()
    .into(),
    MenuItem::Separator.into(),
    CustomMenuItem::new(
      LOCK_DATABASE,
      system_menu_translation.sub_menu(LOCK_DATABASE, "Lock Database"),
    )
    .accelerator("CmdOrControl+L")
    .disabled()
    .into(),
    CustomMenuItem::new(
      LOCK_ALL_DATABASES,
      system_menu_translation.sub_menu(LOCK_ALL_DATABASES, "Lock All Databases"),
    )
    .accelerator("Shift+CmdOrControl+L")
    .disabled()
    .into(),
  ]);

  let entries_sub_menu = Menu::with_items([
    CustomMenuItem::new(
      NEW_ENTRY,
      system_menu_translation.sub_menu(NEW_ENTRY, "New Entry"),
    )
    .accelerator("CmdOrControl+N")
    .disabled()
    .into(),
    MenuItem::Separator.into(),
    CustomMenuItem::new(
      EDIT_ENTRY,
      system_menu_translation.sub_menu(EDIT_ENTRY, "Edit Entry"),
    )
    .accelerator("CmdOrControl+E")
    .disabled()
    .into(),
  ]);

  let groups_sub_menu = Menu::with_items([
    CustomMenuItem::new(
      NEW_GROUP,
      system_menu_translation.sub_menu(NEW_GROUP, "New Group"),
    )
    .disabled()
    .into(),
    MenuItem::Separator.into(),
    CustomMenuItem::new(
      EDIT_GROUP,
      system_menu_translation.sub_menu(EDIT_GROUP, "Edit Group"),
    )
    .disabled()
    .into(),
  ]);

  // let tools_sub_menu = Menu::with_items([CustomMenuItem::new(
  //   PASSWORD_GENERATOR,
  //   system_menu_translation.sub_menu(PASSWORD_GENERATOR, "Password Generator"),
  // )
  // .accelerator("CmdOrControl+G")
  // .disabled()
  // .into()]);

  let pw_gen_menu_item = CustomMenuItem::new(
    PASSWORD_GENERATOR,
    system_menu_translation.sub_menu(PASSWORD_GENERATOR, "Password Generator"),
  )
  .accelerator("CmdOrControl+G")
  .disabled();

  #[cfg(target_os = "macos")]
  {
    let tools_sub_menu = Menu::with_items([pw_gen_menu_item.into()]);

    Menu::new()
      .add_submenu(Submenu::new("App Menu", first_menu))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Edit"),
        edit_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Database"),
        database_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Entries"),
        entries_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Groups"),
        groups_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Tools"),
        tools_sub_menu,
      ))
  }

  #[cfg(not(target_os = "macos"))]
  {
    let tools_sub_menu = Menu::with_items([pw_gen_menu_item.into(),app_settings.into()]);
    Menu::new()
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("File"),
        first_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Edit"),
        edit_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Database"),
        database_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Entries"),
        entries_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Groups"),
        groups_sub_menu,
      ))
      .add_submenu(Submenu::new(
        system_menu_translation.main_menu("Tools"),
        tools_sub_menu,
      ))
  }
}

#[derive(Serialize, Deserialize, Debug)]
pub enum MenuAction {
  Close,
  Enable,
  Disable,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MenuActionRequest {
  menu_id: String,
  menu_action: MenuAction,
}

#[derive(Clone, serde::Serialize)]
/// Payload to send to the UI layer
struct MenuPayload {
  menu_id: String,
}

/// Called to act on some menu action or to enable/disable certain App menus based
/// on the UI state. Called in a tauri command from UI.
/// See 'onekeepass.frontend.background/menu-action-requested' ,
/// 'onekeepass.frontend.events.tauri-events/enable-app-menu'
pub fn menu_action_requested<R: Runtime>(request: MenuActionRequest, app: AppHandle<R>) {
  let menu_id = request.menu_id.as_str();
  match menu_id {
    QUIT => {
      info!("Quit requested from UI {:?}", request);
      let _r = kp_service::close_all_databases();

      info!("Closed all databases");

      let r = kp_service::remove_app_temp_dir_content();
      info!("Temp cache dir removed - result {:?} ", r);

      app.exit(0);
    }
    EDIT_ENTRY | NEW_ENTRY | EDIT_GROUP | NEW_GROUP | SAVE_DATABASE | SAVE_DATABASE_AS
    | SAVE_DATABASE_BACKUP | LOCK_DATABASE | LOCK_ALL_DATABASES | CLOSE_DATABASE
    | PASSWORD_GENERATOR | SEARCH => {
      if let Some(main_window) = app.get_window("main") {
        let menu_handle = main_window.menu_handle();
        let t = match request.menu_action {
          MenuAction::Enable => true,
          MenuAction::Disable => false,
          _ => false,
        };
        if let Err(e) = menu_handle.get_item(menu_id).set_enabled(t) {
          log::error!(
            "Unexpectd error {:?} for the UI menu action {} ",
            e,
            menu_id
          );
        }
      }
    }
    _ => {
      info!("Not yet handled menu action: {:?}", request);
    }
  }
}

// This handles all menu events for any window ('main' window or any other window if used) for now
// All requested menu_id are just forwarded to the UI layer and UI layer decides what to do
// See functions in 'onekeepass.frontend.events.tauri-events' particularly 'handle-menu-events'
pub fn handle_menu_events(menu_event: &WindowMenuEvent) {
  menu_event
    .window()
    .emit(
      TAURI_MENU_EVENT,
      MenuPayload {
        menu_id: menu_event.menu_item_id().into(),
      },
    )
    .unwrap();
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MenuTitleChangeRequest {
  menu_titles: HashMap<String, String>,
}

pub fn menu_titles_change_requested<R: Runtime>(
  request: MenuTitleChangeRequest,
  app: AppHandle<R>,
) {
  for (menu_id, title) in request.menu_titles.iter() {
    if let Some(main_window) = app.get_window("main") {
      let menu_handle = main_window.menu_handle();
      // Need to use try_get_item
      if let Err(e) = menu_handle.get_item(menu_id).set_title(title) {
        log::error!(
          "Unexpectd error {:?} while changing title of menu id {} ",
          e,
          menu_id
        );
      }
    }
  }
}
