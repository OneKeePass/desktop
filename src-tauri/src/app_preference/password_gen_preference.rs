use std::{
    fs,
    path::{Path, PathBuf},
};

use log::debug;
use onekeepass_core::db_service as kp_service;
use serde::{Deserialize, Serialize};

use crate::app_paths;

// Default character-password options used when a preference file has no stored
// password options yet (e.g. an older file predating this field, via serde
// default). Kept in sync with the UI defaults in the password generator dialog:
// length 16, all character sets on, similar characters excluded, strict on.
fn default_password_generation_options() -> kp_service::PasswordGenerationOptions {
    kp_service::PasswordGenerationOptions {
        length: 16,
        numbers: true,
        lowercase_letters: true,
        uppercase_letters: true,
        symbols: true,
        spaces: false,
        exclude_similar_characters: true,
        strict: true,
    }
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct PasswordGeneratorPreference {
    phrase_generator_options: kp_service::PassphraseGenerationOptions,

    // Introduced in a later release than phrase_generator_options; serde default
    // keeps older preference files (without this field) loadable.
    #[serde(default = "default_password_generation_options")]
    password_generation_options: kp_service::PasswordGenerationOptions,
}

impl Default for PasswordGeneratorPreference {
    fn default() -> Self {
        Self {
            phrase_generator_options: Default::default(),
            password_generation_options: default_password_generation_options(),
        }
    }
}

impl PasswordGeneratorPreference {
    pub(crate) fn update_pass_phrase_options(
        &mut self,
        phrase_generator_options: kp_service::PassphraseGenerationOptions,
    ) {
        self.phrase_generator_options = phrase_generator_options;
    }

    pub(crate) fn update_password_options(
        &mut self,
        password_generation_options: kp_service::PasswordGenerationOptions,
    ) {
        self.password_generation_options = password_generation_options;
    }

    // Copies the words list file to app's internal dir for later use
    #[allow(dead_code)]
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

    #[allow(dead_code)]
    pub(crate) fn remove_word_list_file<P: AsRef<Path>>(full_file_path: P) {
        let _ = fs::remove_file(full_file_path);
    }

    #[allow(dead_code)]
    pub(crate) fn word_list_deleted(file_name: &str) {
        let p = app_paths::wordlists_dir().join(file_name);
        Self::remove_word_list_file(&p);
    }
}
