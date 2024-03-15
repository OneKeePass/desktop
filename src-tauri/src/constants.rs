pub mod event_names {
  pub const MAIN_WINDOW_EVENT: &str = "MainWindowEvent";
  pub const OTP_TOKEN_UPDATE_EVENT: &str = "OtpTokenUpdateEvent";
  pub const TAURI_MENU_EVENT:&str = "TauriMenuEvent";
}

pub mod event_action_names {
  pub const WINDOW_FOCUS_CHANGED: &str = "WindowFocusChanged";
  pub const CLOSE_REQUESTED: &str = "CloseRequested";
}
