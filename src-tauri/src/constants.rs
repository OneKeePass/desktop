pub mod event_names {
  pub const MAIN_WINDOW_EVENT: &str = "MainWindowEvent";
  pub const OTP_TOKEN_UPDATE_EVENT: &str = "OtpTokenUpdateEvent";
  pub const TAURI_MENU_EVENT: &str = "TauriMenuEvent";
}

pub mod event_action_names {
  pub const WINDOW_FOCUS_CHANGED: &str = "WindowFocusChanged";
  pub const CLOSE_REQUESTED: &str = "CloseRequested";
}

#[allow(dead_code)]
pub mod themes {
  pub const LIGHT: &str = "light";
  pub const DARK: &str = "dark";
}

pub mod standard_file_names {
  pub const APP_PREFERENCE_FILE: &str = "preference.toml";
}

pub mod standard_dirs {
  pub const BACKUP_DIR: &str = "";
}
