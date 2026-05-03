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
use tauri::{AppHandle, State};

use tauri::{path::BaseDirectory, App, Manager, Runtime};

use crate::app_preference::{BrowserExtSupportData, Preference, PreferenceData};
use crate::biometric;
use crate::bookmarks::{self, BookmarkHandle};
use crate::constants::standard_file_names::APP_PREFERENCE_FILE;
use crate::key_secure;
use crate::sandbox;
use crate::translation::current_locale_language;
use crate::{app_paths, file_util};

use onekeepass_core::error::Result;

//////    App startup time Init call //////////

pub(crate) fn init_app(app: &App) {
    // An example of using Short Cut Key (in v1) to use with menus and auto typing
    // use tauri::GlobalShortcutManager;
    // let _r = app
    //   .app_handle()
    //   .global_shortcut_manager()
    //   .register("Alt+Shift+R", move || {
    //     println!("⌥⇧R is pressed");
    //   });
    ////

    AppState::set_global_app_handle(app);

    // Ensure that all app dir paths are created if required and available
    app_paths::init_app_paths();

    // Do we need this?
    // Defensively create the App Group container and its Logs sub-directory
    // before any IPC or logging code tries to write there. Under sandbox, an
    // entitled process can create directories inside the group container
    // directly; this is simpler than calling NSFileManager from Swift.
    if let Some(gc) = sandbox::group_container_path() {
        let _ = std::fs::create_dir_all(&gc);
        let _ = std::fs::create_dir_all(gc.join("Logs"));
    }

    let state = app.state::<AppState>();

    // Reads the app preference from a toml config file
    // loggings still not yet setup
    state.read_preference(app);

    init_log(&app_paths::app_logs_dir());
    // Now onwards all loggings calls will be effective

    // Restore scoped access to a user-picked backup directory under macOS
    // sandbox if a bookmark was persisted on a prior run. Held for the entire
    // app process lifetime; released implicitly on shutdown.
    state.init_backup_dir_scoped_access();

    // Set the resource path for latter use
    let resource_path = app_paths::app_resources_dir(app.app_handle())
        .ok()
        .map(|ref p| PathBuf::from(&p));

    state.set_resource_dir_path(resource_path);

    key_secure::init_key_main_store();

    // callback_service_provider::init_callback_service_provider(app.app_handle().clone());

    onekeepass_core::async_service::start_runtime();

    // Start the browser extennion proxy listener if required
    state.start_ext_proxy_service();

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

static GLOBAL_TAURI_APP_HANDLE: std::sync::OnceLock<AppHandle> = std::sync::OnceLock::new();

// Identifies an entry in `AppState.scoped_access`. Handles for opened DBs are
// keyed by the file path (matching the existing `db_key`); the backup directory
// has at most one active scoped access per app process and uses the BackupDir
// sentinel.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub(crate) enum ScopedAccessKey {
    Db(String),
    BackupDir,
}

// IMPORTANT:
// Need to keep all state fields behind Mutex if we need to mutuate as
// we cann't get &mut self of 'AppState'
pub(crate) struct AppState {
    // Here we're using an Arc to share memory among threads,
    // and the data - Preference struct - inside the Arc is protected with a mutex.
    pub(crate) preference: Arc<Mutex<Preference>>,
    timers_init_completed: Mutex<bool>,
    resource_dir_path: Mutex<Option<PathBuf>>,
    pub(crate) db_file_watcher: crate::db_file_watcher::DbFileWatcherState,
    // macOS App Sandbox security-scoped bookmark handles currently held open.
    // Empty on non-macOS / non-sandboxed builds. See `crate::bookmarks`.
    scoped_access: Mutex<HashMap<ScopedAccessKey, BookmarkHandle>>,
}

impl AppState {
    pub(crate) fn new() -> Self {
        Self {
            preference: Arc::new(Mutex::new(Preference::default())),
            timers_init_completed: Mutex::new(false),
            resource_dir_path: Mutex::new(None),
            db_file_watcher: crate::db_file_watcher::DbFileWatcherState::new(),
            scoped_access: Mutex::new(HashMap::new()),
        }
    }

    // This should be called once in 'init_app' fn
    fn set_global_app_handle(app: &App) {
        let app_handle = app.handle().clone();
        GLOBAL_TAURI_APP_HANDLE
            .set(app_handle.clone())
            .expect("Global Tauri AppHandle is already initialized. Should be called once only");
    }

    pub(crate) fn global_app_handle() -> &'static AppHandle {
        GLOBAL_TAURI_APP_HANDLE
            .get()
            .expect("Global Tauri AppHandle is not yet initialized")
    }

    pub(crate) fn state_instance() -> State<'static, AppState> {
        AppState::global_app_handle().state::<AppState>()
    }

    fn set_resource_dir_path(&self, resource_path: Option<PathBuf>) {
        // println!("set_resource_dir_path is {:?}",&resource_path);
        let mut resource_dir_path = self.resource_dir_path.lock().unwrap();
        *resource_dir_path = resource_path;
    }

    pub(crate) fn resource_dir_path(&self) -> Option<PathBuf> {
        let resource_dir_path = self.resource_dir_path.lock().unwrap();
        resource_dir_path.clone()
    }

    pub(crate) fn timers_init_completed(&self) {
        let mut init_status = self.timers_init_completed.lock().unwrap();
        *init_status = true;
    }

    pub(crate) fn is_timers_init_completed(&self) -> bool {
        let init_status = self.timers_init_completed.lock().unwrap();
        *init_status
    }

    // Reads the preference from file system and store in state
    fn read_preference(&self, app: &App) {
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

    // Gets the full backupfile name
    pub(crate) fn get_backup_file(&self, db_file_name: &str) -> Option<String> {
        let store_pref = self.preference.lock().unwrap();
        let (backup_dir_path, use_timestamped_name) = if store_pref.backup.enabled {
            let backup_dir = store_pref.backup.dir.as_ref()?.trim();
            if backup_dir.is_empty() {
                return None;
            }
            (PathBuf::from(backup_dir), true)
        } else {
            (app_paths::app_backup_dir(), false)
        };

        // Ensure that the backup dir exists. If not, there will not any backup written
        match backup_dir_path.try_exists() {
            Ok(x) if (x == false) => return None,
            Ok(_) => {}
            Err(_e) => return None,
        };

        debug!(
            "Resolved backup path mode. enabled={} dir={:?}",
            store_pref.backup.enabled, &store_pref.backup.dir
        );

        if use_timestamped_name {
            file_util::generate_timestamped_backup_file_name(backup_dir_path, db_file_name)
        } else {
            file_util::generate_backup_file_name(backup_dir_path, db_file_name)
        }
    }
}

// All preference access fns are grouped under this impl

impl AppState {
    pub(crate) fn prefered_language(&self) -> String {
        let store_pref = self.preference.lock().unwrap();
        //store_pref.language.clone() will also works
        (*store_pref.language).to_string()
    }

    pub(crate) fn default_entry_category_groupings(&self) -> String {
        let store_pref = self.preference.lock().unwrap();
        store_pref.default_entry_category_groupings.clone()
    }

    pub(crate) fn update_preference(&self, preference_data: PreferenceData) -> Result<()> {
        let prior_dir = self.preference.lock().unwrap().backup.dir.clone();

        let result = {
            let mut store_pref = self.preference.lock().unwrap();
            store_pref.update(preference_data)
        };

        // If the backup dir actually changed, rotate the scoped-access handle
        // so the new dir's bookmark backs file writes for the rest of the
        // session. No-op outside macOS sandbox.
        let (current_dir, current_bookmark) = {
            let pref = self.preference.lock().unwrap();
            (pref.backup.dir.clone(), pref.backup.dir_bookmark.clone())
        };
        if prior_dir != current_dir {
            self.release_scoped_access(&ScopedAccessKey::BackupDir);
            if let Some(b64) = &current_bookmark {
                if let Ok((handle, _)) = bookmarks::resolve_and_start(b64) {
                    self.store_scoped_access(ScopedAccessKey::BackupDir, handle);
                }
            }
        }

        result
    }

    pub(crate) fn remove_app_home_backup_file(&self, db_file_name: &str) {
        let backup_file_name =
            file_util::generate_backup_file_name(app_paths::app_backup_dir(), db_file_name);

        if let Some(path) = backup_file_name {
            if let Err(err) = file_util::remove_file_if_exists(&path) {
                log::error!("Removing internal backup file failed for {:?}: {}", path, err);
            }
        }
    }

    pub(crate) fn update_browser_ext_support(
        &self,
        browser_ext_support: BrowserExtSupportData,
    ) -> Result<()> {
        let mut store_pref = self.preference.lock().unwrap();
        store_pref.update_browser_ext_support(browser_ext_support)
    }

    // Called from the 'verifier' module
    pub(crate) fn is_browser_extension_use_enabled(&self, browser_id: &str) -> bool {
        let store_pref = self.preference.lock().unwrap();
        store_pref
            .browser_ext_support_preference()
            .is_extension_use_enabled(browser_id)
    }

    // pub(crate) fn is_allowed_browser(&self, browser_id: &str) -> bool {
    //     let store_pref = self.preference.lock().unwrap();
    //     store_pref
    //         .browser_ext_support_preference()
    //         .is_allowed_browser(browser_id)
    // }

    // Called from frontend through Commands api when user allows a browser ext connection
    pub(crate) fn browser_ext_use_user_permission(&self, browser_id: &str, confirmed: bool) {
        let mut store_pref = self.preference.lock().unwrap();
        store_pref.browser_ext_use_user_permission(browser_id, confirmed);
    }

    // Shows a folder picker (via tauri-plugin-dialog) pre-targeted at the
    // browser's standard NativeMessagingHosts directory. On user confirmation,
    // creates a security-scoped bookmark for that folder, persists it into the
    // browser_dir_bookmarks preference field, and performs the manifest write
    // within the just-granted scope. On cancellation (None returned by the
    // picker), returns Ok(()) so the cljs side can re-try later.
    // Only meaningful on macOS when sandboxed; on other platforms it is a no-op.
    pub(crate) fn browser_ext_pick_install_dir<R: Runtime>(
        &self,
        app: &AppHandle<R>,
        browser_id: &str,
    ) -> Result<()> {
        use tauri_plugin_dialog::DialogExt;

        // NSOpenPanel ignores set_directory when the target doesn't exist yet.
        // Walk up to the nearest existing ancestor so the picker opens nearby
        // rather than falling back to whatever the user last opened elsewhere.
        let default_dir = sandbox::browser_manifest_dir(browser_id).and_then(|mut path| loop {
            if path.exists() {
                break Some(path);
            }
            if !path.pop() {
                break None;
            }
        });

        let mut builder = app.dialog().file();
        if let Some(dir) = default_dir {
            builder = builder.set_directory(dir);
        }

        let picked = builder
            .set_title("Allow OneKeePass to install the browser-extension manifest")
            .blocking_pick_folder();

        let picked_path = match picked {
            Some(p) => p,
            None => {
                log::info!("browser_ext_pick_install_dir: user cancelled for {}", browser_id);
                return Ok(());
            }
        };

        let path_str = picked_path.to_string();

        // Create a security-scoped bookmark while the panel grant is still active.
        let b64 = match crate::bookmarks::create(&path_str) {
            Some(b) => b,
            None => {
                log::error!("browser_ext_pick_install_dir: bookmark creation failed for {}", &path_str);
                return Err(onekeepass_core::error::Error::UnexpectedError(
                    "Failed to create a security-scoped bookmark for the selected folder".into(),
                ));
            }
        };

        // Persist the bookmark so future writes resolve it without a new picker.
        {
            let mut store_pref = self.preference.lock().unwrap();
            store_pref.store_browser_dir_bookmark(browser_id, b64);
        }

        // Now re-run the manifest write; the stored bookmark will be resolved
        // by write_manifest_with_scope inside browser_ext_support.update.
        {
            let mut store_pref = self.preference.lock().unwrap();
            store_pref.write_browser_manifest(browser_id)?;
        }

        Ok(())
    }

    // Called onetime when the app starts
    fn start_ext_proxy_service(&self) {
        let store_pref = self.preference.lock().unwrap();
        store_pref
            .browser_ext_support_preference()
            .start_proxy_handling_service();
    }

    pub(crate) fn clear_recent_files(&self) {
        let mut store_pref = self.preference.lock().unwrap();
        store_pref.clear_recent_files();
    }

    pub(crate) fn remove_recent_file(&self, file_name: &str) {
        let mut store_pref = self.preference.lock().unwrap();
        store_pref.remove_recent_file(file_name);
    }

    pub(crate) fn app_version(&self) -> String {
        let store_pref = self.preference.lock().unwrap();
        store_pref.version().to_string()
    }
}

// Scoped-access lifecycle for macOS App Sandbox security-scoped bookmarks.
// On non-sandboxed / non-macOS builds these calls are still safe (the
// underlying bookmark FFI is a no-op) so call sites do not need to gate.
impl AppState {
    // Records a scoped-access handle and releases any prior handle stored under
    // the same key (defensive — prevents leaks if a second open of the same db
    // occurs without an intervening close).
    pub(crate) fn store_scoped_access(&self, key: ScopedAccessKey, handle: BookmarkHandle) {
        let prior = {
            let mut store = self.scoped_access.lock().unwrap();
            store.insert(key, handle)
        };
        if let Some(h) = prior {
            bookmarks::release(h);
        }
    }

    // Releases (and forgets) the handle for `key` if one is held. No-op if the
    // key is not present.
    pub(crate) fn release_scoped_access(&self, key: &ScopedAccessKey) {
        let prior = {
            let mut store = self.scoped_access.lock().unwrap();
            store.remove(key)
        };
        if let Some(h) = prior {
            bookmarks::release(h);
        }
    }

    // Resolves the user-picked backup directory bookmark (if any) and starts
    // scoped access for the lifetime of the process. Called once at app boot.
    // No-op outside macOS sandbox or when the user's `backup.dir` is the
    // default (container-relative) path that doesn't need a bookmark.
    pub(crate) fn init_backup_dir_scoped_access(&self) {
        if !sandbox::is_sandboxed() {
            return;
        }
        let bookmark_b64 = {
            let pref = self.preference.lock().unwrap();
            if !pref.backup.enabled {
                return;
            }
            pref.backup.dir_bookmark.clone()
        };
        let Some(b64) = bookmark_b64 else {
            return;
        };

        match bookmarks::resolve_and_start(&b64) {
            Ok((handle, refreshed)) => {
                if let Some(refreshed_b64) = refreshed {
                    let mut pref = self.preference.lock().unwrap();
                    pref.update_backup_dir_bookmark(Some(refreshed_b64));
                    debug!("Refreshed stale backup-dir bookmark and persisted");
                }
                self.store_scoped_access(ScopedAccessKey::BackupDir, handle);
            }
            Err(e) => {
                log::warn!(
                    "Failed to resolve backup-dir bookmark; backup writes to a non-default \
                     dir will be skipped until the user re-picks. Cause: {}",
                    e
                );
            }
        }
    }
}

fn format_os_version(info: &os_info::Info) -> String {
    let version_str = info.version().to_string();

    if info.os_type() == os_info::Type::Windows {
        if let Some(build_str) = version_str.split('.').nth(2) {
            if let Ok(build) = build_str.parse::<u32>() {
                if build >= 22000 {
                    return "11".to_string();
                }
            }
        }
    }

    version_str
}

#[derive(Serialize, Deserialize)]
pub(crate) struct StandardDirs {
    document_dir: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub(crate) struct SystemInfoWithPreference {
    os_name: String,
    os_version: String,
    arch: String,
    path_sep: String,
    standard_dirs: StandardDirs,
    biometric_type_available: String,
    preference: Preference,
    dev_mode: bool,
    // True when this binary was compiled with the `mas-build` Cargo feature
    // (Mac App Store variant). Used by the cljs UI to hide features that are
    // either kernel-blocked under App Sandbox (auto-type) or otherwise
    // unavailable in the MAS build, so users don't see non-functional UI.
    mas_build: bool,
}
//app_state: State<'_, app_state::AppState>
// app: tauri::AppHandle<R>,
impl SystemInfoWithPreference {
    // pub fn init(app_state: &AppState) -> Self {

    pub fn init<R: Runtime>(app: tauri::AppHandle<R>) -> Self {
        let app_state: State<'_, AppState> = app.state();

        let pref = app_state.preference.lock().unwrap();

        let os_name = std::env::consts::OS.into();
        let os_version = format_os_version(&os_info::get());
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
                document_dir: app.path().document_dir().map_or_else(
                    |_| None,
                    |d| Some(d.as_os_str().to_string_lossy().to_string()),
                ),
            },
            biometric_type_available: biometric::supported_biometric_type(),
            // document_dir().and_then(|d| Some(d.as_os_str().to_string_lossy().to_string())),
            preference: pref.clone(),
            dev_mode: cfg!(feature = "onekeepass-dev"),
            mas_build: cfg!(feature = "mas-build"),
        }
    }
}

// TODO Return Result<HashMap<>>
pub(crate) fn load_custom_svg_icons<R: Runtime>(
    app: &tauri::AppHandle<R>,
) -> HashMap<String, String> {
    //IMPORTANT:
    // "../resources/public/icons/custom-svg" should be included in "resources" key in  /desktop/src-tauri/tauri.conf.json

    // Note: ../ in path will add _up_

    let path = app
        .path()
        .resolve(
            "../resources/public/icons/custom-svg",
            BaseDirectory::Resource,
        )
        .unwrap();

    info!("Resolved resource path is {:?}", path);

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
pub(crate) fn read_language_selection(preference_file_content: &str) -> String {
    let Some(s) = Preference::read_language_selection(preference_file_content) else {
        println!("No language preference is found and will use current locale language instead");
        return current_locale_language();
    };
    s
}

pub(crate) fn read_preference_file() -> String {
    let pref_file_name = app_paths::app_home_dir().join(APP_PREFERENCE_FILE);
    fs::read_to_string(pref_file_name).unwrap_or("".into())
}

#[cfg(test)]
mod tests {
    use crate::app_state::Preference;
    use std::{collections::HashMap, fs, path::PathBuf};
    use toml::Value;

    use crate::file_util::{generate_backup_file_name, generate_timestamped_backup_file_name};
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
    fn verify_timestamped_backup_file_name() {
        let backup_file_name = generate_timestamped_backup_file_name(
            PathBuf::from("/mybackups"),
            "/path/to/db_file/MY_All_Passwords.kdbx",
        )
        .unwrap();

        assert!(backup_file_name.starts_with("/mybackups/MY_All_Passwords-"));
        assert!(backup_file_name.ends_with(".kdbx"));
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
