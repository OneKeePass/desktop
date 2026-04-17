use std::{
  fs, io,
  path::{Path, PathBuf},
};

use chrono::{Local, NaiveDateTime};
use log::debug;
use onekeepass_core::db_service as kp_service;

fn normalized_backup_source_stem(file_stem: &str) -> String {
  const BACKUP_TIMESTAMP_FORMAT: &str = "%Y-%m-%d %H %M %S";
  const TIMESTAMP_LEN: usize = 19;
  const HASH_LEN: usize = 8;

  let mut current = file_stem.to_string();
  let mut stripped_timestamped_suffix = false;

  loop {
    if current.len() <= HASH_LEN + TIMESTAMP_LEN + 2 {
      break;
    }

    let timestamp_start = current.len() - TIMESTAMP_LEN;
    let hash_end = timestamp_start - 1;
    let hash_start = hash_end.saturating_sub(HASH_LEN);

    if &current[hash_end..timestamp_start] != "-" {
      break;
    }

    let timestamp_part = &current[timestamp_start..];
    if NaiveDateTime::parse_from_str(timestamp_part, BACKUP_TIMESTAMP_FORMAT).is_err() {
      break;
    }

    if hash_start == 0 || &current[hash_start - 1..hash_start] != "-" {
      break;
    }

    let hash_part = &current[hash_start..hash_end];
    let valid_hash = hash_part.len() == HASH_LEN
      && hash_part.chars().all(|c| c.is_ascii_hexdigit());

    if !valid_hash {
      break;
    }

    current.truncate(hash_start - 1);
    stripped_timestamped_suffix = true;
  }

  if stripped_timestamped_suffix {
    loop {
      if current.len() <= HASH_LEN + 1 {
        break;
      }

      let hash_start = current.len() - HASH_LEN;
      if &current[hash_start - 1..hash_start] != "-" {
        break;
      }

      let hash_part = &current[hash_start..];
      let valid_hash = hash_part.chars().all(|c| c.is_ascii_hexdigit());
      if !valid_hash {
        break;
      }

      current.truncate(hash_start - 1);
    }
  }

  current
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
  fn normal_name_stays_unchanged() {
    assert_eq!(normalized_backup_source_stem("Testcsv"), "Testcsv");
  }

  #[test]
  fn backup_name_normalizes_to_original_file_name() {
    assert_eq!(
      normalized_backup_source_stem("Testcsv-2cba3048-2026-04-17 11 15 52"),
      "Testcsv"
    );
  }

  #[test]
  fn backup_of_backup_name_also_normalizes_to_original_file_name() {
    assert_eq!(
      normalized_backup_source_stem("Testcsv-2cba3048-f2c6ac7f-2026-04-17 11 40 38"),
      "Testcsv"
    );
  }

  #[test]
  fn generated_name_for_original_file_uses_original_stem_and_one_hash() {
    let backup_file_name = generate_timestamped_backup_file_name(
      PathBuf::from("/mybackups"),
      "/path/to/db_file/Testcsv.kdbx",
    )
    .unwrap();

    assert!(backup_file_name.starts_with("/mybackups/Testcsv-"));
    assert!(backup_file_name.ends_with(".kdbx"));
  }

  #[test]
  fn generated_name_for_backup_file_resets_to_original_stem_before_new_hash() {
    let backup_file_name = generate_timestamped_backup_file_name(
      PathBuf::from("/mybackups"),
      "/path/to/db_file/Testcsv-2cba3048-2026-04-17 11 15 52.kdbx",
    )
    .unwrap();

    assert!(backup_file_name.starts_with("/mybackups/Testcsv-"));
    assert!(!backup_file_name.starts_with("/mybackups/Testcsv-2cba3048-"));
    assert!(backup_file_name.ends_with(".kdbx"));
  }

  #[test]
  fn generated_name_for_older_buggy_backup_chain_also_resets_to_original_stem() {
    let backup_file_name = generate_timestamped_backup_file_name(
      PathBuf::from("/mybackups"),
      "/path/to/db_file/Testcsv-2cba3048-f2c6ac7f-2026-04-17 11 40 38.kdbx",
    )
    .unwrap();

    assert!(backup_file_name.starts_with("/mybackups/Testcsv-"));
    assert!(!backup_file_name.starts_with("/mybackups/Testcsv-2cba3048-"));
    assert!(backup_file_name.ends_with(".kdbx"));
  }
}
