use log::error;
use serde::{Deserialize, Serialize};
use std::fs;

use crate::utils::*;

#[derive(Clone, Serialize, Deserialize, Debug)]
pub struct BackupPreference {
  pub (crate) enabled: bool,
  pub (crate) dir: Option<String>,
}

impl Default for BackupPreference {
  fn default() -> Self {
    Self {
      enabled: true,
      dir: None,
    }
  }
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub struct Preference {
  pub version: String,
  // In minutes
  pub session_timeout: u8,
  pub default_entry_category_groupings: String, //Types,Categories,Groups
  pub recent_files: Vec<String>,
  pub backup: BackupPreference,
}

impl Default for Preference {
  fn default() -> Self {
    Self {
      // Same as in tauri.conf.json and will be reset to the latest version from tauri.conf.json
      // in read_toml
      version: "0.0.5".into(),
      session_timeout: (15 as u8),
      default_entry_category_groupings: "Types".into(),
      recent_files: vec![],
      backup: BackupPreference::default(),
    }
  }
}

impl Preference {
  /// Reads the previously stored preference if any and returns the newly created Preference instance
  pub fn read_toml(version:String) -> Self {
    // As read_toml is called before log setup, any log calls will not work. 
    // May need to use println if we want to see any console output during development

    let pref_file_name = app_home_dir().join("preference.toml");
    let pref_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
    // let pref:Preference = toml::from_str(&pref_str).unwrap();

    // If we add new fields to Preference after the app release, we need to introduce versioned
    // Preference or need to parse indiviual keys in the toml string that is read

    // For example, we can get the version number used in the preference and use that
    // to parse other fields from the toml string and populate the new Prefence
    // let value = pref_str.parse::<Value>().unwrap();
    // println!("value is  {:#?}", value["version"].as_str().unwrap());

    // let recent_files = value["recent_files"].clone().try_into::<Vec<String>>().unwrap()
    // println!("recent_files is  {:#?}", value["recent_files"].clone().try_into::<Vec<String>>() );

    // Alternatively we parse the whole 'pref_str' into Preference1 if the version is  "0.0.1"
    // 'pref_str' into Preference2 if the version is  "0.0.2"  ...
    // use typedef Preference = Preference2  to indicate the latest Preference in use

    let mut pref: Preference = match toml::from_str(&pref_str) {
      Ok(p) => p,
      _e => {
        // Parsing fails if the Preference file read from file system is not deserializable
        // In that case, we start using the default one
        let mut p = Preference::default();
        p.backup.dir = Some(app_backup_dir().as_os_str().to_string_lossy().to_string());
        // Let us write back the default as what is read from file system is not a valid pref
        p.write_toml();
        p
      }
    };

    
    if pref.version != version {
      // The read preference from file system has version whic is not the same as current one
      // So we need to write the new one
      pref.version = version;
      pref.write_toml();
    }
    pref
  }

  /// Writes the preference with any updates
  pub fn write_toml(&mut self) {
    // Remove old file names from the list before writing
    self.remove_old_recent_files();

    let pref_file_name = app_home_dir().join("preference.toml");

    let toml_str_result = toml::to_string(self);
    if let Ok(toml_str) = toml_str_result {
      if let Err(err) = fs::write(pref_file_name, toml_str.as_bytes()) {
        error!("Prefernce write failed and error is {}", err.to_string());
      }
    }
  }

  /// Reads the previously stored preference if any and returns the newly created Preference instance
  pub fn _read_json() -> Self {
    let pref_file_name = app_home_dir().join("preference.json");
    let json_str = fs::read_to_string(pref_file_name).unwrap_or("".into());
    if json_str.is_empty() {
      Self::default()
    } else {
      serde_json::from_str(&json_str).unwrap_or(Self::default())
    }
  }

  /// Writes the preference with any updates
  fn _write_json(&mut self) {
    // Remove old file names from the list before writing
    self.remove_old_recent_files();
    let json_str_result = serde_json::to_string_pretty(self);
    let pref_file_name = app_home_dir().join("preference.json");
    if let Ok(json_str) = json_str_result {
      if let Err(err) = fs::write(pref_file_name, json_str.as_bytes()) {
        error!("Prefernce write failed and error is {}", err.to_string());
      }
    }
  }

  pub fn add_recent_file(&mut self, file_name: &str) -> &mut Self {
    // First we need to remove any previously added if any
    self.recent_files.retain(|s| s != file_name);
    // most recent file goes to the top - 0 index
    self.recent_files.insert(0, file_name.into());
    // Write the preference to the file system immediately
    self.write_toml();
    self
  }

  pub fn remove_recent_file(&mut self, file_name: &str) -> &mut Self {
    self.recent_files.retain(|s| s != file_name);
    self.write_toml();
    self
  }

  fn remove_old_recent_files(&mut self) -> &mut Self {
    // Keeps the most recet 5 entries
    self.recent_files.truncate(5);
    self
  }
}