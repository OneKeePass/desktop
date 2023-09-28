use onekeepass_core::db_service as kp_service;
use super::{parsing, WindowInfo};

use std::collections::HashMap;

pub fn active_window_titles() -> kp_service::Result<Vec<WindowInfo>> {
    todo!()
}

pub fn send_sequence_to_winow(
    window: WindowInfo,
    sequence: &str,
    entry_fields: HashMap<String, String>,
  ) -> kp_service::Result<()> {
    todo!()
  }