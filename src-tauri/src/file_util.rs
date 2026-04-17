use std::{
  fs, io,
  path::{Path, PathBuf},
};

use chrono::{Local, NaiveDateTime};
use log::debug;
use onekeepass_core::db_service as kp_service;

fn normalized_backup_source_stem(file_stem: &str) -> String {
  const BACKUP_TIMESTAMP_FORMAT: &str = "%Y-%m-%d %H %M %S";
  const TIMESTAMP_WITH_SEPARATOR_LEN: usize = 20;

  if file_stem.len() <= TIMESTAMP_WITH_SEPARATOR_LEN {
    return file_stem.to_string();
  }

  let split_at = file_stem.len() - TIMESTAMP_WITH_SEPARATOR_LEN;
  let (base_name, suffix) = file_stem.split_at(split_at);

  if !suffix.starts_with('-') {
    return file_stem.to_string();
  }

  let ts = &suffix[1..];
  if NaiveDateTime::parse_from_str(ts, BACKUP_TIMESTAMP_FORMAT).is_ok() {
    base_name.to_string()
  } else {
    file_stem.to_string()
  }
}

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

// Generates a timestamped backup file name of form
// "MY_All_Passwords-a4f29c1d-2026-04-17 10 30 45.kdbx"
pub(crate) fn generate_timestamped_backup_file_name(
  backup_dir_path: PathBuf,
  db_file_name: &str,
) -> Option<String> {
  if db_file_name.trim().is_empty() {
    return None;
  }

  let db_path = Path::new(db_file_name);
  let fname_no_extension = db_path.file_stem().map_or_else(
    || "DB_FILE_NAME".into(),
    |s| normalized_backup_source_stem(&s.to_string_lossy()),
  );

  let source_hash = format!(
    "{:08x}",
    kp_service::service_util::string_to_simple_hash(db_file_name) & 0xffff_ffff
  );
  let ts = Local::now().format("%Y-%m-%d %H %M %S").to_string();
  let backup_file_name = format!("{fname_no_extension}-{source_hash}-{ts}.kdbx");

  debug!("timestamped backup_file_name is {}", backup_file_name);
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

pub(crate) fn remove_file_if_exists<P: AsRef<Path>>(path: P) -> io::Result<()> {
  match fs::remove_file(path.as_ref()) {
    Ok(()) => Ok(()),
    Err(err) if err.kind() == io::ErrorKind::NotFound => Ok(()),
    Err(err) => Err(err),
  }
}

#[cfg(test)]
mod tests {
  use super::{generate_timestamped_backup_file_name, normalized_backup_source_stem};
  use std::path::PathBuf;

  #[test]
  fn strips_one_trailing_backup_timestamp_suffix() {
    assert_eq!(
      normalized_backup_source_stem("MY_All_Passwords-2026-04-17 10 30 45"),
      "MY_All_Passwords"
    );
  }

  #[test]
  fn keeps_non_backup_timestamp_names_intact() {
    assert_eq!(
      normalized_backup_source_stem("MY_All_Passwords-archive"),
      "MY_All_Passwords-archive"
    );
  }

  #[test]
  fn timestamped_backup_name_uses_normalized_stem_and_hash() {
    let backup_file_name = generate_timestamped_backup_file_name(
      PathBuf::from("/mybackups"),
      "/path/to/db_file/MY_All_Passwords-2026-04-17 10 30 45.kdbx",
    )
    .unwrap();

    assert!(backup_file_name.starts_with("/mybackups/MY_All_Passwords-"));
    assert!(!backup_file_name.contains("10 30 45-"));
    assert!(backup_file_name.ends_with(".kdbx"));
  }
}
