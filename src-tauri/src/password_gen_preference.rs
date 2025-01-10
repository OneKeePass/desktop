use std::{
    fs, io,
    path::{Path, PathBuf},
  };
  
  use log::debug;
  use onekeepass_core::db_service as kp_service;
use serde::{Deserialize, Serialize};
  
  use crate::app_paths;


  #[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct PasswordGeneratorPreference {
    phrase_generator_options: kp_service::PassphraseGenerationOptions,
}

impl Default for PasswordGeneratorPreference {
    fn default() -> Self {
        Self { phrase_generator_options: Default::default() }
    }
}

impl PasswordGeneratorPreference {
  // Copies the words list file to app's internal dir for later use
  pub(crate) fn copy_wordlist_file<P: AsRef<Path>>(
    picked_full_file_path: P,
  ) -> kp_service::Result<PathBuf> {
    let source = Path::new(picked_full_file_path.as_ref());
    let file_name = source
      .file_name()
      .ok_or_else(|| kp_service::Error::DataError("Wordlist file name is not found"))?;

    let target = app_paths::wordlists_dir().join(file_name);

    debug!(
      "Copying picked worlist file {:?} as {:?} ",
      &source, &target
    );

    fs::copy(&source, &target)?;

    debug!("Worlist file is copied");

    Ok(target)
  }

  pub(crate) fn remove_word_list_file<P: AsRef<Path>>(full_file_path: P) {
    let _ = fs::remove_file(full_file_path);
  }

  pub(crate) fn word_list_deleted(file_name: &str) {
    let p = app_paths::wordlists_dir().join(file_name);
    Self::remove_word_list_file(&p);
  }
}