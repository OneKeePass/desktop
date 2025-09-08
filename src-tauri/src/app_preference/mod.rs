mod browser_ext_preference;
mod password_gen_preference;
pub(crate) mod preference;

pub(crate) use preference::*;

use serde::{Deserialize, Serialize};

use onekeepass_core::db_service as kp_service;

pub(crate) use crate::app_preference::browser_ext_preference::{BrowserExtSupportData, DatabaseBrowserExtSupport};

#[derive(Clone, Serialize, Deserialize, Debug)]
pub(crate) struct BackupPreference {
    pub(crate) enabled: bool,
    pub(crate) dir: Option<String>,
}

impl Default for BackupPreference {
    fn default() -> Self {
        Self {
            enabled: true,
            dir: None,
        }
    }
}

#[derive(Debug,Deserialize)]
pub(crate) struct PreferenceData {
    session_timeout: Option<u8>,
    clipboard_timeout: Option<u16>,
    default_entry_category_groupings: Option<String>,
    theme: Option<String>,
    language: Option<String>,
    pass_phrase_options: Option<kp_service::PassphraseGenerationOptions>,
    browser_ext_supported_databases:Option<Vec<DatabaseBrowserExtSupport>>,
}
