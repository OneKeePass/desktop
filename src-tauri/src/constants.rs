pub mod event_names {
    pub const MAIN_WINDOW_EVENT: &str = "MainWindowEvent";
    pub const OTP_TOKEN_UPDATE_EVENT: &str = "OtpTokenUpdateEvent";
    pub const TAURI_MENU_EVENT: &str = "TauriMenuEvent";
    pub const BROWSER_CONNECTION_REQUEST_EVENT: &str = "BrowserConnectionRequestEvent";
    pub const PASSKEY_DATA_CHANGED_EVENT: &str = "PasskeyDataChangedEvent";
    pub const DB_FILE_CHANGED_EVENT: &str = "DbFileChangedEvent";
    pub const SSH_AGENT_SIGN_REQUEST_EVENT: &str = "SshAgentSignRequestEvent";
}

pub mod event_action_names {
    pub const WINDOW_FOCUS_CHANGED: &str = "WindowFocusChanged";
    pub const CLOSE_REQUESTED: &str = "CloseRequested";
    pub const FILE_DROP: &str = "FileDrop";
    // Emitted after the backend has locked all open databases in response to an
    // OS suspend/sleep signal (see power_monitor). The UI reacts by showing the
    // lock screen for every open database on resume.
    pub const DATABASES_LOCKED: &str = "DatabasesLocked";
}

#[allow(dead_code)]
pub mod themes {
    pub const LIGHT: &str = "light";
    pub const DARK: &str = "dark";
}

pub mod standard_file_names {
    pub const APP_PREFERENCE_FILE: &str = "preference.toml";
}

#[allow(dead_code)]
pub mod standard_dirs {
    pub const BACKUP_DIR: &str = "";
}

#[allow(dead_code)]
pub mod window_labels {
    // src-tauri/tauri.conf.json -> "app" -> "windows"  ( not "bundle" -> "windows" one)
    //
    // https://v2.tauri.app/reference/config/#label default value is 'main'

    pub const MAIN_WINDOW_LABEL: &str = "main";
}
