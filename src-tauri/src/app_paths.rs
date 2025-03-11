use std::{
  fs,
  path::{Path, PathBuf},
};

use tauri::{
  api::path::{home_dir, resolve_path, BaseDirectory},
  Env, Runtime,
};

use crate::file_util;

// Should be called on app startup so that all app dirs are created 
pub(crate) fn init_app_paths() {
  let app_dir = app_home_dir();
  let log_dir = app_logs_dir();
  let backups_dir = app_backup_dir();
  let word_list_dir = wordlists_dir();

  if !app_dir.exists() {
    let _ = fs::create_dir_all(&app_dir);
  }

  if !backups_dir.exists() {
    let _ = fs::create_dir_all(&backups_dir);
  }

  if !log_dir.exists() {
    let _ = fs::create_dir_all(&log_dir);
  } else {
    // Each time we remove any old log file.
    // TODO: Explore the use file rotation
    let _r = file_util::remove_dir_files(&log_dir);
  }

  if !word_list_dir.exists() {
    if let Err(e) = fs::create_dir_all(&word_list_dir) {
      log::error!(
        "Creating dir {:?} failed with error: {} ",
        &word_list_dir,
        e
      );
    }
  }
}

// IMPORTANT: unwrap() is used. What is the alternative ?
pub(crate) fn app_home_dir() -> PathBuf {
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

// All fns getting app dir should be called only after 'init_app_paths' is called

pub(crate) fn app_logs_dir() -> PathBuf {
  app_home_dir().join("logs")
}

pub(crate) fn app_backup_dir() -> PathBuf {
  app_home_dir().join("backups")
}

pub(crate) fn wordlists_dir() -> PathBuf {
  app_home_dir().join("wordlists")
}

#[allow(dead_code)]
pub fn create_sub_dir_path<P: AsRef<Path>>(root_dir: P, sub: &str) -> PathBuf {
  // Initialize with the root_dir itself
  let mut final_full_path_dir = Path::new(root_dir.as_ref()).to_path_buf();

  let full_path_dir = Path::new(root_dir.as_ref()).join(sub);

  if !full_path_dir.exists() {
    if let Err(e) = std::fs::create_dir_all(&full_path_dir) {
      // This should not happen!
      log::error!(
        "Directory at {} creation failed {:?}",
        &full_path_dir.display(),
        e
      );
    } else {
      // As fallback use the full_path_dir of root_dir
      final_full_path_dir = full_path_dir;
    }
  } else {
    final_full_path_dir = full_path_dir;
  }
  final_full_path_dir
}

pub(crate) fn app_resources_dir<R: Runtime>(app: tauri::AppHandle<R>) -> Result<String, String> {
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
