mod browser_ext_preference;
mod password_gen_preference;
pub(crate) mod preference;

pub(crate) use preference::*;

use serde::{Deserialize, Serialize};

use onekeepass_core::db_service as kp_service;

pub(crate) use crate::app_preference::browser_ext_preference::BrowserExtSupportData;

fn default_max_backup_copies() -> u16 {
    crate::file_util::DEFAULT_MAX_BACKUP_COPIES
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct BackupPreference {
    pub(crate) enabled: bool,
    pub(crate) dir: Option<String>,

    // How many timestamped backups to keep per database. Only meaningful while
    // 'enabled' is set - the app-home backup used otherwise is a single file
    // that is overwritten. serde default keeps pre-0.25.0 preference.toml files
    // (which have no such key) loading.
    #[serde(default = "default_max_backup_copies")]
    pub(crate) max_copies: u16,
}

impl Default for BackupPreference {
    fn default() -> Self {
        Self {
            enabled: false,
            dir: None,
            max_copies: default_max_backup_copies(),
        }
    }
}

// One entry in the recent-files list. Replaces the previous `Vec<String>`.
#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct RecentFile {
    pub(crate) path: String,
}

impl RecentFile {
    pub(crate) fn new(path: String) -> Self {
        Self { path }
    }
}

#[derive(Debug, Deserialize)]
pub(crate) struct PreferenceData {
    // In minutes; the settings UI accepts 1-1440 (issue #80)
    session_timeout: Option<u16>,
    clipboard_timeout: Option<u16>,
    default_entry_category_groupings: Option<String>,
    theme: Option<String>,
    language: Option<String>,
    backup: Option<BackupPreference>,
    pass_phrase_options: Option<kp_service::PassphraseGenerationOptions>,
    password_options: Option<kp_service::PasswordGenerationOptions>,
    browser_ext_support: Option<BrowserExtSupportData>,
    // Global SSH agent enable flag. Like browser_ext_support, the frontend sends
    // it on every save; Preference::update merges it and AppState::update_preference
    // starts/stops the listener when it changes.
    ssh_agent_support: Option<SshAgentSupport>,
    // Auto-save after an edit action (issue #90). Like the two flags above, the
    // frontend sends it on every settings save and Preference::update merges it.
    auto_save: Option<AutoSavePreference>,
    // browser_ext_supported_databases:Option<Vec<DatabaseBrowserExtSupport>>,
}
