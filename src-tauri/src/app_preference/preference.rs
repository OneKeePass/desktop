use std::fs;

use crate::app_preference::password_gen_preference::PasswordGeneratorPreference;

use crate::app_preference::{BackupPreference, PreferenceData, RecentFile};

use crate::app_preference::browser_ext_preference::{BrowserExtSupport, BrowserExtSupportData};

use crate::{
    app_paths::app_backup_dir,
    constants::{standard_file_names::APP_PREFERENCE_FILE, themes::LIGHT},
    translation,
};

use onekeepass_core::error::Result;

use crate::app_paths::app_home_dir;
use serde::{Deserialize, Serialize};

// Old preference used in the earlier version v0.17.0

#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct Preference1 {
    version: String,

    // In minutes
    session_timeout: u8,

    // In seconds
    clipboard_timeout: u16,

    // Determines the theme colors etc
    theme: String,

    // Should be a two letters language id
    pub(crate) language: String,

    // Valid values one of Types,Categories,Groups,Tags
    pub(crate) default_entry_category_groupings: String,

    recent_files: Vec<String>,

    pub(crate) backup: BackupPreference,

    // Introded 'password_gen_preference' in v0.15.0
    password_gen_preference: PasswordGeneratorPreference,
}

// Old preference used through v0.20.x: recent_files were plain strings.
// Kept around so existing user TOMLs deserialize during migration to the
// current `Preference` (which carries security-scoped bookmarks per file).
#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct Preference2 {
    version: String,
    session_timeout: u8,
    clipboard_timeout: u16,
    theme: String,
    pub(crate) language: String,
    pub(crate) default_entry_category_groupings: String,
    recent_files: Vec<String>,
    pub(crate) backup: BackupPreference,
    password_gen_preference: PasswordGeneratorPreference,
    browser_ext_support: BrowserExtSupport,
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct Preference {
    version: String,

    // In minutes
    session_timeout: u8,

    // In seconds
    clipboard_timeout: u16,

    // Determines the theme colors etc
    theme: String,

    // Should be a two letters language id
    pub(crate) language: String,

    // Valid values one of Types,Categories,Groups,Tags
    pub(crate) default_entry_category_groupings: String,

    // Each entry carries the file path plus an optional macOS security-scoped
    // bookmark. Bookmark is consumed only on sandboxed builds; populated on
    // any macOS build for forward-compat with later sandboxed installs.
    recent_files: Vec<RecentFile>,

    pub(crate) backup: BackupPreference,

    password_gen_preference: PasswordGeneratorPreference,

    // Introduced browser ext releated preference in v0.17.0
    browser_ext_support: BrowserExtSupport,
    // For now this feature is not used in the UI
    // This will be used in future to allow user to select which database to use with browser extension
    // Typically user will enable browser ext support for one or more databases in the database settings
    // When user enables browser ext support for a database, we will add that database key to this list
    // and remove when user disables the browser ext support for that database
    // browser_ext_supported_databases: Vec<DatabaseBrowserExtSupport>,
}

impl Default for Preference {
    fn default() -> Self {
        Self {
            // Same as in tauri.conf.json. This is for doc purpose only as
            // this will be reset to the latest version from tauri.conf.json
            // after parsing the toml pref file in read_toml and pref file is updated accordingly
            version: "0.20.0".into(),
            session_timeout: (15 as u8),
            clipboard_timeout: (30 as u16),
            theme: LIGHT.into(),
            language: translation::current_locale_language(),
            default_entry_category_groupings: "Groups".into(),
            recent_files: vec![],
            backup: BackupPreference::default(),
            password_gen_preference: PasswordGeneratorPreference::default(),

            browser_ext_support: BrowserExtSupport::default(),
            // browser_ext_supported_databases: vec![],
        }
    }
}

impl Preference {
    fn is_pre_0_20_version(version: &str) -> bool {
        let mut parts = version.split('.');
        let major = parts.next().and_then(|v| v.parse::<u32>().ok());
        let minor = parts.next().and_then(|v| v.parse::<u32>().ok());

        matches!((major, minor), (Some(0), Some(m)) if m < 20)
    }

    // Reads the previously stored preference if any and returns the newly created Preference instance
    pub(crate) fn read_toml(version: String) -> Self {
        // As read_toml is called before log setup, any log calls will not work.
        // May need to use println if we want to see any console output during development

        let pref_file_name = app_home_dir().join(APP_PREFERENCE_FILE);
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
                // Parsing fails if the file read from file system is not deserializable to current version of Preference
                // We attempt to parse as the previous version
                if let Some(mut p) = Self::read_previous_preference(&pref_str) {
                    p.version = version.clone();
                    p.write_toml();
                    p
                } else {
                    // Parsing of the file data to any version of Preference struct failed
                    // In that case, we start using the default one
                    let mut p = Preference::default();
                    p.version = version.clone();
                    p.backup.dir = Some(app_backup_dir().as_os_str().to_string_lossy().to_string());
                    // Let us write back the default as what is read from file system is not a valid pref
                    p.write_toml();
                    p
                }

                // let mut p = Preference::default();
                // p.backup.dir = Some(app_backup_dir().as_os_str().to_string_lossy().to_string());
                // // Let us write back the default as what is read from file system is not a valid pref
                // p.write_toml();
                // p
            }
        };

        if pref.version != version {
            // The read preference from file system has version which is not the same as current one
            // So we need to write the new one

            // This is a temporary arrangement. 
            // Should it be removed after 4 or 5 future releases?
            if Self::is_pre_0_20_version(&pref.version) {
                pref.backup.enabled = false;
                pref.backup.dir = None;
            }

            pref.version = version;
            pref.write_toml();
        }
        pref
    }

    fn read_previous_preference(pref_str: &str) -> Option<Preference> {
        // Try the most recent legacy schema first (v0.20.x — Vec<String> recent_files,
        // no per-file bookmarks). Convert each path to a bookmark-less RecentFile.
        if let Ok(p2) = toml::from_str::<Preference2>(&pref_str) {
            #[cfg(feature = "onekeepass-dev")]
            println!("Found Preference2 (v0.20.x) and migrating");

            let mut p = Preference::default();
            p.version = p2.version;
            p.session_timeout = p2.session_timeout;
            p.clipboard_timeout = p2.clipboard_timeout;
            p.theme = p2.theme;
            p.language = p2.language;
            p.default_entry_category_groupings = p2.default_entry_category_groupings;
            p.recent_files = p2
                .recent_files
                .into_iter()
                .map(|path| RecentFile::new(path, None))
                .collect();
            p.backup = p2.backup;
            p.password_gen_preference = p2.password_gen_preference;
            p.browser_ext_support = p2.browser_ext_support;
            return Some(p);
        }

        if let Ok(p1) = toml::from_str::<Preference1>(&pref_str) {
            // As the logging is not enabled, we will see this logging output
            // info!("Found previous version of Preference and using that");
            #[cfg(feature = "onekeepass-dev")]
            println!("Found Preference1 (v0.17.x) and migrating");

            let mut p = Preference::default();
            p.session_timeout = p1.session_timeout;
            p.backup = p1.backup;
            p.recent_files = p1
                .recent_files
                .into_iter()
                .map(|path| RecentFile::new(path, None))
                .collect();
            p.default_entry_category_groupings = p1.default_entry_category_groupings;
            p.version = p1.version;
            p.language = p1.language;
            p.theme = p1.theme;
            p.clipboard_timeout = p1.clipboard_timeout;
            p.password_gen_preference = p1.password_gen_preference;

            Some(p)
        } else {
            None
        }
    }

    // Extracts only the language field value from the prefernce file's content
    // The arg pref_file_content_str is the previously read preference.toml as string
    pub(crate) fn read_language_selection(pref_file_content_str: &str) -> Option<String> {
        // log::debug!("In read_language_selection: pref_file_content_str {}",pref_file_content_str);

        // let tvalue = pref_file_content_str.parse::<toml::Table>().unwrap();
        // log::debug!("In read_language_selection: toml value {:?}", &tvalue);

        // let lv = tvalue["language"].as_str();

        // log::debug!("In read_language_selection: language {:?}", &lv);

        // Parsing with toml::Value and extracting 'language' from preference did not work
        // However, using toml::Table worked
        // Ref https://docs.rs/toml/latest/toml/#parsing-toml
        if let Ok(value) = pref_file_content_str.parse::<toml::Table>() {
            if let Some(s) = value.get("language") {
                log::info!("Language preference is found and it is {:#?}", s.as_str());
                s.as_str().map(|s| s.into())
            } else {
                log::info!("Language preference is not found");
                None
            }
        } else {
            log::error!("TOML Parsing of pref_file_content_str failed and language preference is not extracted");
            None
        }
    }

    // Writes the preference with any updates
    fn write_toml(&mut self) {
        // Remove old file names from the list before writing
        self.remove_old_recent_files();

        let pref_file_name = app_home_dir().join(APP_PREFERENCE_FILE);

        let toml_str_result = toml::to_string(self);
        if let Ok(toml_str) = toml_str_result {
            if let Err(err) = fs::write(pref_file_name, toml_str.as_bytes()) {
                log::error!("Prefernce write failed and error is {}", err.to_string());
            }
        }
    }

    // Update the preference with any non null values
    pub(crate) fn update(&mut self, preference_data: PreferenceData) -> Result<()> {
        // debug!("Preference update is called {:?}", &preference_data);
        // debug!("Preference before {:?}",&self);

        // TODO: Need to have a 'macro' for this
        let mut updated = false;
        if let Some(v) = preference_data.language {
            self.language = v;
            updated = true;
        }

        if let Some(v) = preference_data.theme {
            self.theme = v;
            updated = true;
        }

        if let Some(v) = preference_data.default_entry_category_groupings {
            self.default_entry_category_groupings = v;
            updated = true;
        }

        if let Some(v) = preference_data.session_timeout {
            self.session_timeout = v;
            updated = true;
        }

        if let Some(v) = preference_data.clipboard_timeout {
            self.clipboard_timeout = v;
            updated = true;
        }

        if let Some(mut v) = preference_data.backup {
            // The frontend never sends `dir_bookmark` — it doesn't know about
            // sandbox bookmarks. Re-derive one here from the picked dir while
            // the file-picker grant for that path is still active in this
            // process. None for empty / default container-relative paths.
            v.dir_bookmark = match v.dir.as_deref() {
                Some(dir) if !dir.trim().is_empty() => crate::bookmarks::create(dir),
                _ => None,
            };
            self.backup = v;
            updated = true;
        }

        if let Some(v) = preference_data.pass_phrase_options {
            self.password_gen_preference.update_pass_phrase_options(v);
            updated = true;
        }

        if let Some(v) = preference_data.browser_ext_support {
            self.browser_ext_support.update(v)?;
            updated = true;
        }

        // For now this feature is not used in the UI
        // This will be used in future to allow user to select which database to use with browser extension

        // if let Some(v) = preference_data.browser_ext_supported_databases {
        //     self.browser_ext_supported_databases = v;
        //     updated = true;
        // }

        // debug!("Preference after updated {}, {:?}",updated,&self);
        if updated {
            self.write_toml();
        }

        Ok(())
    }

    pub(crate) fn update_browser_ext_support(
        &mut self,
        browser_ext_support: BrowserExtSupportData,
    ) -> Result<()> {
        self.browser_ext_support.update(browser_ext_support)?;
        // Need to persist the preference update
        self.write_toml();
        log::debug!("BrowserExtSupport update is saved to TOML ");
        Ok(())
    }

    pub(crate) fn browser_ext_use_user_permission(&mut self, browser_id: &str, confirmed: bool) {
        self.browser_ext_support
            .user_confirmation(browser_id, confirmed);

        // Need to persist the preference update
        self.write_toml();

        log::debug!("browser_ext_use_user_permission update is saved to TOML ");
    }

    pub(crate) fn add_recent_file(&mut self, file_name: &str, bookmark: Option<String>) -> &mut Self {
        // Preserve a previously-stored bookmark if no fresh one was supplied,
        // so a re-open from a fresh file picker (no new bookmark created) does
        // not wipe out the bookmark stored on a prior open.
        let prior_bookmark = self
            .recent_files
            .iter()
            .find(|r| r.path == file_name)
            .and_then(|r| r.bookmark.clone());
        self.recent_files.retain(|r| r.path != file_name);
        let bookmark = bookmark.or(prior_bookmark);
        self.recent_files
            .insert(0, RecentFile::new(file_name.to_string(), bookmark));
        self.write_toml();
        self
    }

    pub(crate) fn remove_recent_file(&mut self, file_name: &str) -> &mut Self {
        self.recent_files.retain(|r| r.path != file_name);
        self.write_toml();
        self
    }

    // Returns the stored security-scoped bookmark blob (base64) for `file_name`
    // if a recent entry exists with one. Used at load time to regain sandbox
    // access before the kp_service file read.
    pub(crate) fn recent_file_bookmark(&self, file_name: &str) -> Option<String> {
        self.recent_files
            .iter()
            .find(|r| r.path == file_name)
            .and_then(|r| r.bookmark.clone())
    }

    // True if the given path is currently tracked in recents. Used to decide
    // whether a kp_service load failure should trigger the sandbox re-pick
    // recovery flow vs. just propagating the raw error.
    pub(crate) fn is_in_recent_files(&self, file_name: &str) -> bool {
        self.recent_files.iter().any(|r| r.path == file_name)
    }

    // Replaces the backup-dir bookmark blob in-place and persists. Called
    // when a stale-refresh of the backup-dir bookmark produced a fresh blob
    // at app startup, or when the user re-picks the backup directory.
    pub(crate) fn update_backup_dir_bookmark(&mut self, bookmark: Option<String>) -> &mut Self {
        self.backup.dir_bookmark = bookmark;
        self.write_toml();
        self
    }

    // Replaces just the bookmark on an existing entry. No-op if the entry is
    // not present. Called when a resolve flagged the prior bookmark as stale
    // and the OS gave us a refreshed blob to persist.
    pub(crate) fn update_recent_file_bookmark(
        &mut self,
        file_name: &str,
        bookmark: String,
    ) -> &mut Self {
        let mut updated = false;
        for entry in &mut self.recent_files {
            if entry.path == file_name {
                entry.bookmark = Some(bookmark.clone());
                updated = true;
                break;
            }
        }
        if updated {
            self.write_toml();
        }
        self
    }

    pub(crate) fn clear_recent_files(&mut self) -> &mut Self {
        self.recent_files.clear();
        self
    }

    pub(crate) fn browser_ext_support_preference(&self) -> &BrowserExtSupport {
        &self.browser_ext_support
    }

    // Stores a security-scoped folder bookmark for `browser_id` and persists.
    // Called after the user picks a folder via NSOpenPanel so future manifest
    // writes can resolve the bookmark without a new picker dialog.
    pub(crate) fn store_browser_dir_bookmark(&mut self, browser_id: &str, b64: String) {
        self.browser_ext_support
            .store_browser_dir_bookmark(browser_id, b64);
        self.write_toml();
    }

    // Writes the native-messaging manifest for `browser_id` using the stored
    // security-scoped folder bookmark. The bookmark must already be present
    // (stored via `store_browser_dir_bookmark`). Persists preference on success
    // so any stale-refresh of the bookmark is durable.
    pub(crate) fn write_browser_manifest(&mut self, browser_id: &str) -> onekeepass_core::error::Result<()> {
        self.browser_ext_support
            .write_browser_manifest_for(browser_id)?;
        self.write_toml();
        Ok(())
    }

    pub(crate) fn version(&self) -> &str {
        &self.version
    }

    fn remove_old_recent_files(&mut self) -> &mut Self {
        // Keeps the most recent 8 entries
        self.recent_files.truncate(8);
        self
    }

    // Reads the previously stored preference if any and returns the newly created Preference instance
    fn _read_json() -> Self {
        let pref_file_name = app_home_dir().join(APP_PREFERENCE_FILE);
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
                log::error!("Prefernce write failed and error is {}", err.to_string());
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::Preference;

    #[test]
    fn pre_0_20_versions_are_detected() {
        assert!(Preference::is_pre_0_20_version("0.19.9"));
        assert!(Preference::is_pre_0_20_version("0.18.3"));
    }

    #[test]
    fn version_0_20_and_later_are_not_detected_as_older() {
        assert!(!Preference::is_pre_0_20_version("0.20.0"));
        assert!(!Preference::is_pre_0_20_version("0.21.1"));
        assert!(!Preference::is_pre_0_20_version("1.0.0"));
    }
}
