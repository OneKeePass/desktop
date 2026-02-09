use crate::{app_state, translation};
use crate::{constants::event_names::TAURI_MENU_EVENT, translation::SystemMenuTranslation};
use log::info;
use onekeepass_core::db_service as kp_service;
use serde::{Deserialize, Serialize};
use tauri::menu::{
  AboutMetadata, MenuBuilder, MenuEvent, MenuItem, MenuItemBuilder, Submenu, SubmenuBuilder,
};
use tauri::{AppHandle, Emitter, Runtime};

#[allow(dead_code)]
pub mod menu_ids {

  pub const MAIN_MENU_EDIT: &str = "Edit";
  pub const MAIN_MENU_DATABASE: &str = "Database";
  pub const MAIN_MENU_ENTRIES: &str = "Entries";
  pub const MAIN_MENU_GROUPS: &str = "Groups";
  pub const MAIN_MENU_TOOLS: &str = "Tools";

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

  pub const MERGE_DATABASE: &str = "MergeDatabase";
  pub const IMPORT: &str = "Import";

  pub const NEW_GROUP: &str = "NewGroup";
  pub const EDIT_GROUP: &str = "EditGroup";

  pub const SEARCH: &str = "Search";
  pub const NEW_ENTRY: &str = "NewEntry";
  pub const EDIT_ENTRY: &str = "EditEntry";
  pub const PASSWORD_GENERATOR: &str = "PasswordGenerator";
}
use menu_ids::*;

pub(crate) fn build_menus<R: Runtime>(app_handle: &AppHandle<R>) -> Result<(), tauri::Error> {
  let app_name = "OneKeePass";
  let about_name = "About OneKeePass";

  let pref_str = app_state::read_preference_file();
  let language = app_state::read_language_selection(&pref_str);

  let system_menu_translation: SystemMenuTranslation =
    translation::load_system_menu_translations(&language, app_handle);

  let app_settings =
    MenuItemBuilder::new(system_menu_translation.sub_menu(APP_SETTINGS, "Settings..."))
      .id(APP_SETTINGS)
      .accelerator("CmdOrCtrl+,")
      .build(app_handle)?;

  let quit = MenuItemBuilder::new(system_menu_translation.sub_menu(QUIT, "Quit OneKeePass"))
    .id(QUIT)
    .accelerator("CmdOrControl+Q")
    .build(app_handle)?;

  // let mut ab = AboutMetadata::default();
  // ab.name = Some(app_name.to_string());

  let app_submenu = SubmenuBuilder::with_id(app_handle, app_name, app_name)
    .about_with_text(about_name, Some(AboutMetadata::default()))
    .separator()
    .item(&app_settings)
    .separator()
    .item(&quit)
    .build()?;

  let search_menu_item = MenuItemBuilder::new(system_menu_translation.sub_menu(SEARCH, "Search"))
    .id(SEARCH)
    .accelerator("CmdOrControl+F")
    .enabled(false)
    .build(app_handle)?;

  let edit_sub_menu = SubmenuBuilder::with_id(
    app_handle,
    MAIN_MENU_EDIT,
    system_menu_translation.main_menu(MAIN_MENU_EDIT),
  )
  .cut()
  .copy()
  .paste()
  .separator()
  .item(&search_menu_item)
  .build()?;

  let db_menu = build_database_menus(app_handle, &system_menu_translation)?;

  let menu = MenuBuilder::new(app_handle)
    .items(&[
      &app_submenu,
      &edit_sub_menu,
      &db_menu,
      &build_entries_menus(app_handle, &system_menu_translation)?,
      &build_groups_menus(app_handle, &system_menu_translation)?,
      &build_tools_menus(app_handle, &system_menu_translation)?,
    ])
    .build()?;

  app_handle.set_menu(menu)?;

  Ok(())
}

fn build_database_menus<R: Runtime>(
  app_handle: &AppHandle<R>,
  system_menu_translation: &SystemMenuTranslation,
) -> Result<Submenu<R>, tauri::Error> {
  let db_menus = SubmenuBuilder::new(
    app_handle,
    system_menu_translation.main_menu(MAIN_MENU_DATABASE),
  )
  .id(MAIN_MENU_DATABASE)
  .items(&[
    &MenuItem::with_id(
      app_handle,
      NEW_DATABASE,
      system_menu_translation.sub_menu(NEW_DATABASE, "New Database"),
      true,
      Some("Shift+CmdOrControl+N"),
    )?,
    &MenuItem::with_id(
      app_handle,
      OPEN_DATABASE,
      system_menu_translation.sub_menu(OPEN_DATABASE, "Open Database"),
      true,
      Some("CmdOrControl+O"),
    )?,
    &MenuItem::with_id(
      app_handle,
      SAVE_DATABASE,
      system_menu_translation.sub_menu(SAVE_DATABASE, "Save Database"),
      false,
      Some("CmdOrControl+S"),
    )?,
    &MenuItem::with_id(
      app_handle,
      SAVE_DATABASE_AS,
      system_menu_translation.sub_menu(SAVE_DATABASE_AS, "Save Database As"),
      false,
      Some("Shift+CmdOrControl+S"),
    )?,
    &MenuItem::with_id(
      app_handle,
      SAVE_DATABASE_BACKUP,
      system_menu_translation.sub_menu(SAVE_DATABASE_BACKUP, "Save Database Backup"),
      false,
      None::<&str>,
    )?,
    &MenuItem::with_id(
      app_handle,
      CLOSE_DATABASE,
      system_menu_translation.sub_menu(CLOSE_DATABASE, "Close Database"),
      false,
      Some("CmdOrControl+W"),
    )?,
  ])
  .separator()
  .items(&[
    &MenuItem::with_id(
      app_handle,
      LOCK_DATABASE,
      system_menu_translation.sub_menu(LOCK_DATABASE, "Lock Database"),
      false,
      Some("CmdOrControl+L"),
    )?,
    &MenuItem::with_id(
      app_handle,
      LOCK_ALL_DATABASES,
      system_menu_translation.sub_menu(LOCK_ALL_DATABASES, "Lock All Databases"),
      false,
      Some("Shift+CmdOrControl+L"),
    )?,
  ])
  .separator()
  .items(&[
    &MenuItem::with_id(
      app_handle,
      MERGE_DATABASE,
      system_menu_translation.sub_menu(MERGE_DATABASE, "Merge Database..."),
      false,
      None::<&str>,
    )?,
  ])
  .separator()
  .items(&[
    &MenuItem::with_id(
      app_handle,
      IMPORT,
      system_menu_translation.sub_menu(IMPORT, "Import"),
      true,
      None::<&str>,
    )?,
  ])
  .build();

  db_menus
}

fn build_entries_menus<R: Runtime>(
  app_handle: &AppHandle<R>,
  system_menu_translation: &SystemMenuTranslation,
) -> Result<Submenu<R>, tauri::Error> {
  let entries_menus = SubmenuBuilder::new(
    app_handle,
    system_menu_translation.main_menu(MAIN_MENU_ENTRIES),
  )
  .id(MAIN_MENU_ENTRIES)
  .items(&[
    &MenuItem::with_id(
      app_handle,
      NEW_ENTRY,
      system_menu_translation.sub_menu(NEW_ENTRY, "New Entry"),
      false,
      Some("CmdOrControl+N"),
    )?,
    &MenuItem::with_id(
      app_handle,
      EDIT_ENTRY,
      system_menu_translation.sub_menu(EDIT_ENTRY, "Edit Entry"),
      false,
      Some("CmdOrControl+E"),
    )?,
  ])
  .build();

  entries_menus
}

fn build_groups_menus<R: Runtime>(
  app_handle: &AppHandle<R>,
  system_menu_translation: &SystemMenuTranslation,
) -> Result<Submenu<R>, tauri::Error> {
  let groups_menus = SubmenuBuilder::new(
    app_handle,
    system_menu_translation.main_menu(MAIN_MENU_GROUPS),
  )
  .id(MAIN_MENU_GROUPS)
  .item(&MenuItem::with_id(
    app_handle,
    NEW_GROUP,
    system_menu_translation.sub_menu(NEW_GROUP, "New Group"),
    false,
    None::<&str>,
  )?)
  .separator()
  .item(&MenuItem::with_id(
    app_handle,
    EDIT_GROUP,
    system_menu_translation.sub_menu(EDIT_GROUP, "Edit Group"),
    false,
    None::<&str>,
  )?)
  .build();

  groups_menus
}

fn build_tools_menus<R: Runtime>(
  app_handle: &AppHandle<R>,
  system_menu_translation: &SystemMenuTranslation,
) -> Result<Submenu<R>, tauri::Error> {
  let tools_menus = SubmenuBuilder::new(
    app_handle,
    system_menu_translation.main_menu(MAIN_MENU_TOOLS),
  )
  .id(MAIN_MENU_TOOLS)
  .item(&MenuItem::with_id(
    app_handle,
    PASSWORD_GENERATOR,
    system_menu_translation.sub_menu(PASSWORD_GENERATOR, "Password Generator"),
    true,
    Some("CmdOrControl+G"),
  )?)
  .build();

  tools_menus
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

// This handles all menu events for any window ('main' window or any other window if used) for now
// All requested menu_id are just forwarded to the UI layer and UI layer decides what to do
// See functions in 'onekeepass.frontend.events.tauri-events' particularly 'handle-menu-events'
pub fn handle_menu_events<R: Runtime>(
  app_handle: &AppHandle<R>,
  menu_event: &MenuEvent,
) -> Result<(), tauri::Error> {
  app_handle.emit(
    TAURI_MENU_EVENT,
    MenuPayload {
      menu_id: menu_event.id().0.clone(),
    },
  )
}

fn toggle_enable_disable<R: Runtime>(
  app_hanle: &AppHandle<R>,
  sub_menu_id: &str,
  menu_item_id: &str,
  enabled: bool,
) {
  // debug!("sub_menu_id is {}", &sub_menu_id);

  // Need to get the sub menu first and then menu item under that submenu
  if let Some(mk1) = app_hanle.menu().and_then(|m| m.get(sub_menu_id)) {
    // Get the menu item from the sub menu
    if let Some(mk2) = mk1.as_submenu().and_then(|sm1| sm1.get(menu_item_id)) {
      if let Some(i) = mk2.as_menuitem() {
        if i.id() == menu_item_id {
          let _ = i.set_enabled(enabled);
        }
      }
    }
  }
}

// Called to act on some menu action or to enable/disable certain App menus based
// on the UI state. Called in a tauri command from UI.
// See 'onekeepass.frontend.background/menu-action-requested' ,
// 'onekeepass.frontend.events.tauri-events/enable-app-menu'
pub fn menu_action_requested<R: Runtime>(request: MenuActionRequest, app_handle: &AppHandle<R>) {
  let menu_enabled = match request.menu_action {
    MenuAction::Enable => true,
    MenuAction::Disable => false,
    _ => false,
  };
  let menu_id = request.menu_id.as_str();
  match menu_id {
    QUIT => {
      info!("Quit requested from UI {:?}", request);
      let _r = kp_service::close_all_databases();
      info!("Closed all databases");

      let r = kp_service::remove_app_temp_dir_content();
      info!("Temp cache dir removed - result {:?} ", r);

      app_handle.exit(0);
    }
    SEARCH => {
      // all_menus(app_handle);
      //debug!("Calling toggle_enable_disable for SEARCH");
      toggle_enable_disable(app_handle, MAIN_MENU_EDIT, menu_id, menu_enabled);
    }
    LOCK_DATABASE | LOCK_ALL_DATABASES | CLOSE_DATABASE | SAVE_DATABASE | SAVE_DATABASE_AS
    | SAVE_DATABASE_BACKUP | MERGE_DATABASE => {
      toggle_enable_disable(app_handle, MAIN_MENU_DATABASE, menu_id, menu_enabled);
    }
    EDIT_ENTRY | NEW_ENTRY => {
      toggle_enable_disable(app_handle, MAIN_MENU_ENTRIES, menu_id, menu_enabled);
    }
    EDIT_GROUP | NEW_GROUP => {
      toggle_enable_disable(app_handle, MAIN_MENU_GROUPS, menu_id, menu_enabled);
    }
    // PASSWORD_GENERATOR => {
    //   toggle_enable_disable(app_handle, MAIN_MENU_TOOLS, menu_id, menu_enabled);
    // }

    _ => {
      info!("Not yet handled menu action: {:?}", request);
    }
  }
}



/*
fn print_menu<R: Runtime>(menu: &Menu<R>) {
  use MenuItemKind::*;
  menu.items().unwrap().iter().for_each(|m| match m {
    MenuItem(i) => {
      debug!("MenuItem id is {:?}", i.id());
    }
    Submenu(sm) => {
      debug!("Submenu id is {:?}", sm.id());
      if sm.id() == "Database" {
        if let Some(ik) = sm.get("LockDatabase") {
          debug!("Unwrapped item {:?}", ik.as_menuitem().unwrap().id())
        }
      }
    }

    Predefined(pi) => {
      debug!("Predefined id is {:?}", pi.id());
    }

    x => {
      debug!("X id is {:?}", x.id());
    }
  });
}

fn print_debug_msg<R: Runtime>(mk: &MenuItemKind<R>) {
  use MenuItemKind::*;
  match mk {
    MenuItem(i) => {
      debug!("MenuItem id is {:?}", i.id());
    }
    Submenu(sm) => {
      debug!("Submenu id is {:?}", sm.id());
      if let Ok(items) = sm.items() {
        items.iter().for_each(|mk| print_debug_msg(mk));
      }
    }
    Predefined(pi) => {
      debug!("Predefined id is {:?}", pi.id());
    }
    Check(ci) => {
      debug!("Check id is {:?}", ci.id());
    }
    Icon(ii) => {
      debug!("Check id is {:?}", ii.id());
    }
  }
}

fn all_menus<R: Runtime>(app_hanle: &AppHandle<R>) {
  app_hanle
    .menu()
    .unwrap()
    .items()
    .unwrap()
    .iter()
    .for_each(|mk| print_debug_msg(mk));
}
*/


