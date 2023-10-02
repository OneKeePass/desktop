mod parsing;

#[cfg(target_os = "macos")]
#[path = "macos/mod.rs"]
mod platform;


#[cfg(any(target_os = "windows", target_os = "linux"))]
#[path = "other.rs"]
mod platform;

use std::collections::HashMap;

pub use parsing::{parse_auto_type_sequence, ParsedPlaceHolderVal};

use onekeepass_core::db_service as kp_service;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WindowInfo {
  pub owner: Option<String>,
  pub title: Option<String>,
  pub process_id: i32,
}

#[inline]
pub fn window_titles() -> kp_service::Result<Vec<WindowInfo>> {
  platform::active_window_titles()
}

// The window to which auto typing sequence will be sent
pub fn active_window_to_auto_type() -> Option<WindowInfo> {
  // None is returned if there is no other window is open other than the app
  window_titles().ok().map(|v| v.first().cloned()).flatten()
}

pub async fn send_sequence_to_winow_async(
  window: WindowInfo,
  sequence: &str,
  entry_fields: HashMap<String, String>,
) -> kp_service::Result<()> {
  platform::send_sequence_to_winow_async(window, sequence, entry_fields).await?;
  Ok(())
}


/*
pub fn send_sequence_to_winow(
  window: WindowInfo,
  sequence: &str,
  entry_fields: HashMap<String, String>,
) -> kp_service::Result<()> {
  platform::send_sequence_to_winow(window, sequence, entry_fields)?;
  Ok(())
}


*/