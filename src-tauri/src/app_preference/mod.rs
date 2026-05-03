mod browser_ext_preference;
mod password_gen_preference;
pub(crate) mod preference;

pub(crate) use preference::*;

use serde::{Deserialize, Serialize};

use onekeepass_core::db_service as kp_service;

pub(crate) use crate::app_preference::browser_ext_preference::{BrowserExtSupportData};

#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct BackupPreference {
    pub(crate) enabled: bool,
    pub(crate) dir: Option<String>,
    // Base64-encoded security-scoped bookmark for `dir`, used under macOS App Sandbox
    // to reopen access on subsequent launches when the user-picked dir is outside
    // the per-app container. None for default container-relative dirs and on
    // non-macOS / non-sandboxed builds.
    // Introduced in MAS release 0.21.0
    #[serde(default)]
    pub(crate) dir_bookmark: Option<String>,
}

impl Default for BackupPreference {
    fn default() -> Self {
        Self {
            enabled: false,
            dir: None,
            dir_bookmark: None,
        }
    }
}

// One entry in the recent-files list. Replaces the previous `Vec<String>`.
// Under macOS App Sandbox, raw paths cannot be reopened across launches —
// resolving the security-scoped `bookmark` is required to regain file access.
#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct RecentFile {
    pub(crate) path: String,
    #[serde(default)]
    pub(crate) bookmark: Option<String>,
}

impl RecentFile {
    pub(crate) fn new(path: String, bookmark: Option<String>) -> Self {
        Self { path, bookmark }
    }
}

#[derive(Debug,Deserialize)]
pub(crate) struct PreferenceData {
    session_timeout: Option<u8>,
    clipboard_timeout: Option<u16>,
    default_entry_category_groupings: Option<String>,
    theme: Option<String>,
    language: Option<String>,
    backup: Option<BackupPreference>,
    pass_phrase_options: Option<kp_service::PassphraseGenerationOptions>,
    browser_ext_support:Option<BrowserExtSupportData>,
    // browser_ext_supported_databases:Option<Vec<DatabaseBrowserExtSupport>>,
}
