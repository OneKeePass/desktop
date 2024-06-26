use chrono::Local;
use log::{debug, LevelFilter};
use log4rs::append::console::ConsoleAppender;
use log4rs::append::file::FileAppender;
use log4rs::config::{Appender, Config, Root};
use log4rs::encode::pattern::PatternEncoder;
use serde::{Deserialize, Serialize};

use log::info;
use std::collections::HashMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::result::Result;
use std::sync::{Arc, Mutex};
use sys_locale::get_locale;

use tauri::{
  api::path::{document_dir, home_dir, resolve_path, BaseDirectory},
  App, Env, Manager, Runtime,
};

use crate::biometric;
use crate::constants::standard_file_names::APP_PREFERENCE_FILE;
use crate::key_secure;
use crate::preference::{Preference, PreferenceData};

use onekeepass_core::db_service as kp_service;

pub struct AppState {
  // Here we're using an Arc to share memory among threads,
  // and the data - Preference struct - inside the Arc is protected with a mutex.
  pub preference: Arc<Mutex<Preference>>,
  pub backup_files: Mutex<HashMap<String, String>>,
  timers_init_completed: Mutex<bool>,
}

impl AppState {
  pub fn new() -> Self {
    Self {
      preference: Arc::new(Mutex::new(Preference::default())),
      // We keep any previously determined backup file name for easy lookup and avoids
      // generating the name again
      // TODO: How to handle this when we want to include time info in the file name
      backup_files: Mutex::new(HashMap::default()),
      timers_init_completed: Mutex::new(false),
    }
  }

  pub fn timers_init_completed(&self) {
    let mut init_status = self.timers_init_completed.lock().unwrap();
    *init_status = true;
  }

  pub fn is_timers_init_completed(&self) -> bool {
    let init_status = self.timers_init_completed.lock().unwrap();
    *init_status
  }

  pub fn prefered_language(&self) -> String {
    let store_pref = self.preference.lock().unwrap();
    //store_pref.language.clone() will also works
    (*store_pref.language).to_string()
  }

  /// Reads the preference from file system and store in state
  pub fn read_preference(&self, app: &App) {
    let version = format!(
      "{}.{}.{}",
      app.package_info().version.major,
      app.package_info().version.minor,
      app.package_info().version.patch
    );

    let pref = Preference::read_toml(version);

    // store_pref is MutexGuard
    let mut store_pref = self.preference.lock().unwrap();
    //sets the preference struct value inside the MutexGuard by dereferencing
    *store_pref = pref;
  }


  pub fn update_preference(&self, preference_data: PreferenceData) {
    let mut store_pref = self.preference.lock().unwrap();
    store_pref.update(preference_data);
  }

  pub fn clear_recent_files(&self) {
    let mut store_pref = self.preference.lock().unwrap();
    store_pref.clear_recent_files();
  }

  /// Gets the full backupfile name
  pub fn get_backup_file(&self, db_file_name: &str) -> Option<String> {
    let store_pref = self.preference.lock().unwrap();
    if !store_pref.backup.enabled {
      return None;
    }

    debug!("backup_dir set is {:?}", &store_pref.backup.dir);

    let backup_dir_path = if let Some(pa) = &store_pref.backup.dir {
      PathBuf::from(pa)
    } else {
      app_backup_dir()
    };

    // Ensure that the backup dir exists. If not, there will not any backup written
    match backup_dir_path.try_exists() {
      Ok(x) if (x == false) => return None,
      Ok(_) => {}
      Err(_e) => return None,
    };

    let mut backup_files = self.backup_files.lock().unwrap();

    match backup_files.get(db_file_name) {
      Some(s) => Some(s.clone()),
      None => match generate_backup_file_name(backup_dir_path, db_file_name) {
        Some(v) => {
          backup_files.insert(db_file_name.into(), v.clone());
          Some(v)
        }
        None => None,
      },
    }
  }
}

#[derive(Serialize, Deserialize)]
pub struct StandardDirs {
  pub document_dir: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct SystemInfoWithPreference {
  pub os_name: String,
  pub os_version: String,
  pub arch: String,
  pub path_sep: String,
  pub standard_dirs: StandardDirs,
  pub biometric_type_available: String,
  pub preference: Preference,
}

impl SystemInfoWithPreference {
  pub fn init(app_state: &AppState) -> Self {
    let p = app_state.preference.lock().unwrap();

    let os_name = std::env::consts::OS.into();
    let os_version = os_info::get().version().to_string();
    let arch = std::env::consts::ARCH.to_string();

    info!(
      "OS details: name: {}, version: {}, arch: {} ",
      &os_name, &os_version, &arch
    );

    Self {
      os_name,
      os_version,
      arch,
      path_sep: std::path::MAIN_SEPARATOR.to_string(),
      standard_dirs: StandardDirs {
        document_dir: document_dir().and_then(|p| p.as_path().to_str().map(|s| s.into())),
      },
      biometric_type_available: biometric::supported_biometric_type(),
      // document_dir().and_then(|d| Some(d.as_os_str().to_string_lossy().to_string())),
      preference: p.clone(),
    }
  }
}

// IMPORTANT: unwrap() is used. What is the alternative ?
pub fn app_home_dir() -> PathBuf {
  #[cfg(not(feature = "onekeepass-dev"))]
  let p = home_dir()
    .unwrap()
    .join(Path::new(".onekeepass"))
    .join(Path::new("prod"));

  // To activate this feature during development, we need to use 'cargo tauri dev -f onekeepass-dev'
  #[cfg(feature = "onekeepass-dev")]
  let p = home_dir()
    .unwrap()
    .join(Path::new(".onekeepass"))
    .join(Path::new("dev"));
  p
}

pub fn app_logs_dir() -> PathBuf {
  app_home_dir().join("logs")
}

pub fn app_backup_dir() -> PathBuf {
  app_home_dir().join("backups")
}

fn remove_dir_contents<P: AsRef<Path>>(path: P) -> io::Result<()> {
  for entry in fs::read_dir(path)? {
    fs::remove_file(entry?.path())?;
  }
  Ok(())
}

// Generates the complete backup file name for an existing database file
pub fn generate_backup_file_name(backup_dir_path: PathBuf, db_file_name: &str) -> Option<String> {
  if db_file_name.trim().is_empty() {
    return None;
  }

  let db_path = Path::new(db_file_name);
  let parent_dir = db_path.parent().map_or_else(
    || "Root".into(),
    |p| p.as_os_str().to_string_lossy().to_string(),
  );

  let fname_no_extension = db_path.file_stem().map_or_else(
    || "DB_FILE_NAME".into(),
    |s| s.to_string_lossy().to_string(),
  );

  let n = kp_service::string_to_simple_hash(&parent_dir).to_string();

  // The backup_file_name will be of form "MyPassword_10084644638414928086.kdbx" for
  // the original file name "MyPassword.kdbx" where 10084644638414928086 is a hash of the dir part of full path
  let backup_file_name = vec![fname_no_extension.as_str(), "_", &n, ".kdbx"].join("");

  debug!("backup_file_name is {}", backup_file_name);
  // Should not use any explicit /  like .join("/") while joing components
  backup_dir_path
    .join(backup_file_name)
    .to_str()
    .map(|s| s.to_string())
}

pub fn init_app(app: &App) {
  // An example of using Short Cut Key to use with menus and auto typing
  use tauri::GlobalShortcutManager;
  let _r = app
    .app_handle()
    .global_shortcut_manager()
    .register("Alt+Shift+R", move || {
      println!("⌥⇧R is pressed");
    });
  ////

  let app_dir = app_home_dir();
  let log_dir = app_logs_dir();
  let backups_dir = app_backup_dir();

  if !app_dir.exists() {
    fs::create_dir_all(&app_dir).unwrap();
  }

  if !backups_dir.exists() {
    fs::create_dir_all(&backups_dir).unwrap();
  }

  if !log_dir.exists() {
    fs::create_dir_all(&log_dir).unwrap();
  } else {
    // Each time we remove any old log file.
    // TODO: Explore the use file rotation
    let _r = remove_dir_contents(&log_dir);
  }

  let state = app.state::<AppState>();
  state.read_preference(app);

  init_log(&log_dir);
  // Now onwards all loggings calls will be effective

  key_secure::init_key_main_store();

  onekeepass_core::async_service::start_runtime();

  info!("{}", "Intit app is done");
}

pub fn app_resources_dir<R: Runtime>(app: tauri::AppHandle<R>) -> Result<String, String> {
  if let Ok(path) = resolve_path(
    &app.config(),
    app.package_info(),
    &Env::default(),
    "../resources/public/",
    Some(BaseDirectory::Resource),
  ) {
    path
      .as_path()
      .to_str()
      .map(|s| s.into())
      .ok_or("Could not resolve resource public dir".into())
  } else {
    Err("Could not resolve resource public dir".into())
  }
}

// TODO Return Result<HashMap<>>
pub fn load_custom_svg_icons<R: Runtime>(app: tauri::AppHandle<R>) -> HashMap<String, String> {
  //IMPORTANT:
  // "../resources/public/icons/custom-svg" should be included in "resources" key in  /desktop/src-tauri/tauri.conf.json

  // Note: ../ in path will add _up_

  let path = resolve_path(
    &app.config(),
    app.package_info(),
    &Env::default(),
    "../resources/public/icons/custom-svg",
    Some(BaseDirectory::Resource),
  )
  .unwrap();

  //info!("Resolved resource path is {:?}", path);

  let mut files: HashMap<String, String> = HashMap::new();

  if let Ok(entries) = fs::read_dir(path) {
    for entry in entries {
      if let Ok(entry) = entry {
        // Here, `entry` is a `DirEntry`.
        // println!("{:?}", entry.file_name());
        if let Ok(s) = fs::read_to_string(entry.path()) {
          files.insert(entry.file_name().to_string_lossy().into_owned(), s);
        }
      }
    }
  }
  files
}

// Find out the language preferecne before app is initiated so that 
// language specific translations can be used for system menu build ups
pub fn read_language_selection(preference_file_content: &str) -> String {
  let Some(s) = Preference::read_language_selection(preference_file_content) else {
    println!("No language preference is found and will use current locale language instead");
    return current_locale_language();
  };
  s
}

pub fn read_preference_file() -> String {
  let pref_file_name = app_home_dir().join(APP_PREFERENCE_FILE);
  fs::read_to_string(pref_file_name).unwrap_or("".into())
}

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
  app: tauri::AppHandle<R>,
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

  let path = resolve_path(
    &app.config(),
    app.package_info(),
    &Env::default(),
    TRANSLATION_RESOURCE_DIR,
    Some(BaseDirectory::Resource),
  )
  .map_err(|e| {
    kp_service::Error::UnexpectedError(format!(
      "Resource translations dir locations not found. Error is {} ",
      e
    ))
  })?;

  info!("Translation files root dir for i18n is {:?} ", &path);

  let mut translations: HashMap<String, String> = HashMap::new();

  for lng in language_ids_to_load {
    
    let p = path.join(format!("{}.json",&lng));
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
    debug!("Submenu for menu_id {} is {}", menu_id, &sm);
    sm
  }
}

// Called to load the menu string translations before building the app
pub fn load_system_menu_translations(
  language: &str,
  config: &tauri::Config,
  package_info: &tauri::PackageInfo,
) -> SystemMenuTranslation {
  let mut system_menu_tr = SystemMenuTranslation {
    system_menus: HashMap::default(),
  };

  let Ok(path) = resolve_path(
    &config,
    &package_info,
    &Env::default(),
    TRANSLATION_RESOURCE_DIR,
    Some(BaseDirectory::Resource),
  ) else {
    return system_menu_tr;
  };

  let p = path.join(format!("{}.json",&language));

  //println!("Json file is {:?}", &p);

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

fn init_log(log_dir: &PathBuf) {
  let local_time = Local::now().format("%Y-%m-%d-%H%M%S").to_string();
  let log_file = format!("{}.log", local_time);
  let log_file = log_dir.join(log_file);

  let time_format = "{d(%Y-%m-%d %H:%M:%S)} - {m}{n}";
  let stdout = ConsoleAppender::builder()
    .encoder(Box::new(PatternEncoder::new(time_format)))
    .build();
  let tofile = FileAppender::builder()
    .encoder(Box::new(PatternEncoder::new(time_format)))
    .build(log_file)
    .unwrap();

  #[cfg(not(feature = "onekeepass-dev"))]
  let level = LevelFilter::Info;

  #[cfg(feature = "onekeepass-dev")]
  let level = LevelFilter::Debug;

  let config = Config::builder()
    .appender(Appender::builder().build("stdout", Box::new(stdout)))
    .appender(Appender::builder().build("file", Box::new(tofile)))
    .build(Root::builder().appenders(["stdout", "file"]).build(level))
    .unwrap();

  log4rs::init_config(config).unwrap();
}

#[cfg(test)]
mod tests {
  use crate::utils::Preference;
  use std::{collections::HashMap, fs, path::PathBuf};
  use toml::Value;

  use super::generate_backup_file_name;
  #[test]
  fn verify_preference_reading() {
    let pref_file_name = "/Users/jeyasankar/.onekeepass/dev/preference.toml";
    let pref_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
    //println!("In verify_preference_reading {:#?}", pref_str);
    let value = pref_str.parse::<Value>().unwrap();
    println!("value is  {:#?}", value["version"].as_str().unwrap());
    println!("backkuplocation is  {:#?}", value.get("backkuplocation"));
    println!(
      "recent_files is  {:#?}",
      value["recent_files"].clone().try_into::<Vec<String>>()
    ); //<toml::Value as Into<T>>::into(`, `)`
       //println!("recent_files is  {:#?}", value.get("recent_files").unwrap().as_array().unwrap());

    let pref: Result<Preference, toml::de::Error> = toml::from_str(&pref_str);
    println!("pref is {:#?}", pref);
  }

  #[test]
  fn verify_backup_file_name() {
    let backup_file_name = generate_backup_file_name(
      PathBuf::new(),
      "/Users/jeyasankar/mytemp/Keepass-sample/RustDevSamples/PasswordsXC1-Tags-1.kdbx",
    );
    println!("backup_file_name is {:#?}", backup_file_name);
  }

  #[test]
  fn verify_serde_using_alias() {
    // Json has 'menu.labels' as field name and it is mapped to 
    // rust's menu_labels 
    let input1 = r#"{
      "menu.labels": {"addField": "Add field","addCategory": "Add category"}
    }"#;

    #[derive(Debug, PartialEq,serde::Deserialize)]
    struct Translation {
      #[serde(alias = "menu.labels")]
      menu_labels: HashMap<String, String>,
    }
    let t = Translation {
      menu_labels: HashMap::from([
        ("addField".into(), "Add field".into()),
        ("addCategory".into(), "Add category".into()),
      ]),
    };
    let d: Translation = serde_json::from_str(input1).unwrap();
    //println!("D is {:?}, t is {:?}", d,t);
    assert_eq!(t,d);
  }
}
