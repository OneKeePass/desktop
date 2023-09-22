mod macos;
mod parsing;

#[cfg(target_os = "macos")]
#[path = "macos.rs"]
mod platform;

pub use parsing::{parse_auto_type_sequence,ParsedPlaceHolderVal};

use self::platform::AutoTypeWindowOperationImpl;

use onekeepass_core::db_service as kp_service;
use serde::{Serialize, Deserialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WindowInfo {
  pub owner:Option<String>,
  pub title:Option<String>,
  pub process_id:i32,
}

#[inline]
pub fn window_titles() -> kp_service::Result<Vec<WindowInfo>> {
  platform::active_window_titles()
  // let aty = AutoTypeWindowOperationImpl{};
  // aty.window_titles()
}

// The window to which auto typing sequence will be sent
pub fn active_window_to_auto_type() -> Option<WindowInfo> {
  // None is returned if there is no other window is open other than the app
  window_titles().ok().map(|v| v.first().cloned()).flatten()
}


// ----------

pub (crate) trait AutoTypeWindowOperation {
  fn window_titles(&self) -> kp_service::Result<Vec<WindowInfo>>;
  fn raise_window(&self,process_id:i32) -> kp_service::Result<()> ;
  fn send_sequence(&self,sequence:&str) -> kp_service::Result<()> ;
  //fn raise_and_send_sequence(&self,window:WindowInfo,sequence:&str) ;
}
