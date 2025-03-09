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
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use tauri::{
  api::path::{document_dir, resolve_path, BaseDirectory},
  App, Env, Manager, Runtime,
};

use crate::app_preference::{Preference, PreferenceData};
use crate::biometric;
use crate::constants::standard_file_names::APP_PREFERENCE_FILE;
use crate::key_secure;
use crate::translation::current_locale_language;
use crate::{app_paths, file_util};

//////    App startup time Init call //////////

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

  // Ensure that all app dir paths are created if required and available
  app_paths::init_app_paths();

  let state = app.state::<AppState>();
  // loggings still not yet setup
  state.read_preference(app);

  init_log(&app_paths::app_logs_dir());
  // Now onwards all loggings calls will be effective

  // Se the resource path for latter use
  let resource_path = app_paths::app_resources_dir(app.app_handle())
    .ok()
    .map(|ref p| PathBuf::from(&p));

  state.set_resource_dir_path(resource_path);

  key_secure::init_key_main_store();

  // callback_service_provider::init_callback_service_provider(app.app_handle().clone());

  onekeepass_core::async_service::start_runtime();

  info!("{}", "Intit app is done");
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

////////////////////////////////////////////

// IMPORTANT:
// Need to keep all state fields behind Mutex if we need to mutuate as
// we cann't get &mut self of 'AppState'
pub struct AppState {
  // Here we're using an Arc to share memory among threads,
  // and the data - Preference struct - inside the Arc is protected with a mutex.
  pub preference: Arc<Mutex<Preference>>,
  pub backup_files: Mutex<HashMap<String, String>>,
  timers_init_completed: Mutex<bool>,
  resource_dir_path: Mutex<Option<PathBuf>>,
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
      resource_dir_path: Mutex::new(None),
    }
  }

  fn set_resource_dir_path(&self, resource_path: Option<PathBuf>) {
    let mut resource_dir_path = self.resource_dir_path.lock().unwrap();
    *resource_dir_path = resource_path;
  }

  pub(crate) fn resource_dir_path(&self) -> Option<PathBuf> {
    let resource_dir_path = self.resource_dir_path.lock().unwrap();
    resource_dir_path.clone()
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

  pub(crate) fn default_entry_category_groupings(&self) -> String {
    let store_pref = self.preference.lock().unwrap();
    store_pref.default_entry_category_groupings.clone()
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
      app_paths::app_backup_dir()
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
      None => match file_util::generate_backup_file_name(backup_dir_path, db_file_name) {
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
  let pref_file_name = app_paths::app_home_dir().join(APP_PREFERENCE_FILE);
  fs::read_to_string(pref_file_name).unwrap_or("".into())
}

#[cfg(test)]
mod tests {
  use crate::app_state::Preference;
  use std::{collections::HashMap, fs, path::PathBuf};
  use toml::Value;

  use crate::file_util::generate_backup_file_name;
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

    #[derive(Debug, PartialEq, serde::Deserialize)]
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
    assert_eq!(t, d);
  }
}
