use std::{
  fs, io,
  path::{Path, PathBuf},
};

use log::debug;
use onekeepass_core::db_service as kp_service;

// Generates the complete backup file name for an existing database file
pub(crate) fn generate_backup_file_name(
  backup_dir_path: PathBuf,
  db_file_name: &str,
) -> Option<String> {
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

  let n = kp_service::service_util::string_to_simple_hash(&parent_dir).to_string();

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

pub(crate) fn remove_dir_files<P: AsRef<Path>>(path: P) -> io::Result<()> {
  for entry in fs::read_dir(path)? {
    fs::remove_file(entry?.path())?;
  }
  Ok(())
}
