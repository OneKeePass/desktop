use log::debug;
use serde::{Deserialize, Serialize};

use log::info;
use std::collections::HashMap;
use std::fs;

use sys_locale::get_locale;

use tauri::{path::BaseDirectory, AppHandle, Manager, Runtime};

use crate::app_state::AppState;

use onekeepass_core::db_service as kp_service;

#[inline]
pub fn current_locale_language() -> String {
  // "en-US" language+region
  // We use the language part only to locate translation json files
  let lng = get_locale().unwrap_or_else(|| String::from("en"));

  // Returns the language id ( two letters)
  lng
    .split("-")
    .map(|s| s.to_string())
    .next()
    .unwrap_or_else(|| String::from("en"))
}

#[derive(Serialize, Deserialize)]
pub struct TranslationResource {
  current_locale_language: String,
  prefered_language: String,
  translations: HashMap<String, String>,
}

// "../resources/public/translations" should be included in "resources" key in  /desktop/src-tauri/tauri.conf.json
const TRANSLATION_RESOURCE_DIR: &str = "../resources/public/translations";

// Loads language translation strings for the passed language ids
// Typically it should be en and the preferedd or the locale language
pub fn load_language_translations<R: Runtime>(
  app: &tauri::AppHandle<R>,
  language_ids: Vec<String>,
) -> kp_service::Result<TranslationResource> {
  let current_locale_lng = current_locale_language(); //get_locale().unwrap_or_else(|| String::from("en")); // "en-US" language+region
  debug!("current_locale is {}", &current_locale_lng);

  let state = app.state::<AppState>();

  // prefered_language as stored in Preference is either current locale language or
  // language selected by user in App settings screen
  let prefered_language = state.prefered_language();

  debug!("prefered_language is {}", &prefered_language);

  let language_ids_to_load = if !language_ids.is_empty() {
    language_ids
  } else {
    if prefered_language != "en" {
      vec![String::from("en"), prefered_language.clone()]
    } else {
      // locale is 'en'
      vec![String::from("en")]
    }
  };

  //IMPORTANT:
  // "../resources/public/translations" should be included in "resources" key in  /desktop/src-tauri/tauri.conf.json
  // Note: ../ in path will add _up_

  let path = app
    .path()
    .resolve(TRANSLATION_RESOURCE_DIR, BaseDirectory::Resource)
    .map_err(|e| {
      kp_service::Error::UnexpectedError(format!(
        "Resource translations dir locations not found. Error is {} ",
        e
      ))
    })?;

  info!("Translation files root dir for i18n is {:?} ", &path);

  let mut translations: HashMap<String, String> = HashMap::new();

  for lng in language_ids_to_load {
    let p = path.join(format!("{}.json", &lng));
    //debug!("Going to load translation file  {:?} ",&p);

    let data = fs::read_to_string(p)
      .ok()
      .map_or_else(|| "{}".to_string(), |d| d);
    //let data = fs::read_to_string(p)?;

    translations.insert(lng, data);
  }

  Ok(TranslationResource {
    current_locale_language: current_locale_lng,
    prefered_language,
    translations,
  })
}

// As this struct has only "system_menus" field, serde json deserialization
// will parse only that part of data from the file translation.json
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SystemMenuTranslation {
  system_menus: HashMap<String, HashMap<String, String>>,
}

impl SystemMenuTranslation {
  // Gets the translated string for a main menu from "main" map
  pub fn main_menu(&self, name: &str) -> String {
    let d = HashMap::default();
    let s = self.system_menus.get("main").map_or_else(|| &d, |n| n);
    s.get(name).map_or_else(|| name.into(), |v| v.into())
  }

  // Gets the translated string for a submenu from the "subMenus" map
  pub fn sub_menu(&self, menu_id: &str, default_name: &str) -> String {
    let d = HashMap::default();
    let s = self.system_menus.get("subMenus").map_or_else(|| &d, |n| n);
    let sm = s
      .get(menu_id)
      .map_or_else(|| default_name.into(), |v| v.into());
    // debug!("Submenu for menu_id {} is {}", menu_id, &sm);
    sm
  }
}

// Called to load the menu string translations before building the app
pub(crate) fn load_system_menu_translations<R:Runtime>(
  language: &str,
  app_handle: &AppHandle<R>,
) -> SystemMenuTranslation {
  let mut system_menu_tr = SystemMenuTranslation {
    system_menus: HashMap::default(),
  };

  let Ok(path) = app_handle
    .path()
    .resolve(TRANSLATION_RESOURCE_DIR, BaseDirectory::Resource)
  else {
    return system_menu_tr;
  };

  let p = path.join(format!("{}.json", &language));

  let data = fs::read_to_string(p)
    .ok()
    .map_or_else(|| "{}".to_string(), |d| d);

  // As SystemMenuTranslation struct has only "system_menus" field, serde json deserialization
  // will parse only that part of data from the file translation.json

  system_menu_tr =
    serde_json::from_str::<SystemMenuTranslation>(&data).map_or(system_menu_tr, |v| v);

  //println!("Loaded system menus {:?}", system_menu_tr);

  system_menu_tr
}