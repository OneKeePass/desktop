use std::collections::HashMap;

use onekeepass_core::db_service as kp_service;

use swift_rs::{swift, Int, Int32, SRObjectArray, SRString, UInt16, UInt8};

//use crate::auto_type::AutoTypeWindowOperation; // This will also work
use super::{parsing, AutoTypeWindowOperation, WindowInfo};

#[repr(C)]
struct WindowDetail {
  name: Option<SRString>,
  owner: Option<SRString>,
  pid: Int,
}

/// All extern C calls  

swift!(fn auto_type_window_titles() -> SRObjectArray<WindowDetail>);
// Returns optionally if there is any error
swift!(fn auto_type_activate_window(pid:Int32) -> Option<SRString>);
//
swift!(fn auto_type_send_char(char_utf16:UInt16) );
swift!(fn auto_type_send_key(vk_key_code:UInt8) );

///

pub fn active_window_titles() -> kp_service::Result<Vec<WindowInfo>> {
  let widows: SRObjectArray<WindowDetail> = unsafe { auto_type_window_titles() };
  let excludes = vec![Some("OneKeePass".into()), Some("OneKeePass.app".into())];
  let mut infos = vec![];
  for w in widows.as_slice() {
    infos.push(WindowInfo {
      owner: w.owner.as_ref().map(|s| s.to_string()),
      title: w.name.as_ref().map(|s| s.to_string()),
      process_id: w.pid as i32,
    })
  }
  let infos = infos
    .into_iter()
    .filter(|v| !excludes.contains(&v.owner))
    .collect::<Vec<WindowInfo>>();
  Ok(infos)
}

pub fn raise_window(process_id: i32) -> kp_service::Result<()> {
  let r = unsafe { auto_type_activate_window(process_id) };

  match r {
    Some(s) => Err(kp_service::Error::Other(s.to_string())),
    None => Ok(()),
  }
}

fn send_char(char_utf16: u16) {
  unsafe { auto_type_send_char(char_utf16) }
}
// Copied from /Library/Developer/CommandLineTools/SDKs/MacOSX12.1.sdk/System/Library/..
// .. Frameworks/Carbon.framework/Versions/A/Frameworks/HIToolbox.framework/Versions/A/Headers/Events.h
//
// Prevent warning because of #[warn(non_camel_case_types)]
#[allow(non_camel_case_types)]
enum KeyEventCode {
  kVK_Return = 0x24,
  kVK_Tab = 0x30,
  kVK_Space = 0x31,
  kVK_Delete = 0x33,
  kVK_Escape = 0x35,
  kVK_Command = 0x37,
  kVK_Shift = 0x38,
  kVK_CapsLock = 0x39,
  kVK_Option = 0x3A,
  kVK_Control = 0x3B,
  kVK_RightCommand = 0x36,
  kVK_RightShift = 0x3C,
  kVK_RightOption = 0x3D,
  kVK_RightControl = 0x3E,
  kVK_Function = 0x3F,
  kVK_F17 = 0x40,
  kVK_VolumeUp = 0x48,
  kVK_VolumeDown = 0x49,
  kVK_Mute = 0x4A,
  kVK_F18 = 0x4F,
  kVK_F19 = 0x50,
  kVK_F20 = 0x5A,
  kVK_F5 = 0x60,
  kVK_F6 = 0x61,
  kVK_F7 = 0x62,
  kVK_F3 = 0x63,
  kVK_F8 = 0x64,
  kVK_F9 = 0x65,
  kVK_F11 = 0x67,
  kVK_F13 = 0x69,
  kVK_F16 = 0x6A,
  kVK_F14 = 0x6B,
  kVK_F10 = 0x6D,
  kVK_F12 = 0x6F,
  kVK_F15 = 0x71,
  kVK_Help = 0x72,
  kVK_Home = 0x73,
  kVK_PageUp = 0x74,
  kVK_ForwardDelete = 0x75,
  kVK_F4 = 0x76,
  kVK_End = 0x77,
  kVK_F2 = 0x78,
  kVK_PageDown = 0x79,
  kVK_F1 = 0x7A,
  kVK_LeftArrow = 0x7B,
  kVK_RightArrow = 0x7C,
  kVK_DownArrow = 0x7D,
  kVK_UpArrow = 0x7E,
}

impl TryFrom<&str> for KeyEventCode {
  type Error = kp_service::Error;
  fn try_from(value: &str) -> kp_service::Result<Self> {
    match value {
      "TAB" => Ok(Self::kVK_Tab),
      "ENTER" => Ok(Self::kVK_Return),
      "SPACE" => Ok(Self::kVK_Space),
      _ => Err(kp_service::Error::Other(format!(
        "Unsupported key name is passed {}",
        value
      ))),
    }
  }
}

const MAX_KEY_DELAY: u64 = 500;

fn send_key(key_code: KeyEventCode) {
  unsafe { auto_type_send_key(key_code as u8) }
}

fn send_text(text: &str, inter_key_delay: u64) {
  let utf16: Vec<u16> = text.encode_utf16().collect();
  for char_utf16 in utf16 {
    unsafe { auto_type_send_char(char_utf16) };
    std::thread::sleep(std::time::Duration::from_millis(inter_key_delay));
  }
}

pub fn raise_and_send_sequence(
  window: WindowInfo,
  sequence: &str,
  entry_fields: HashMap<String, String>,
) -> kp_service::Result<()> {
  // Parse sequence first
  let parsed =
    parsing::parse_auto_type_sequence(sequence).map_err(|e| kp_service::Error::Other(e))?;

  // Raise window to the top
  raise_window(window.process_id)?;

  // Send chars and key actions with required delay
  let mut inter_key_delay = MAX_KEY_DELAY;
  for v in parsed {
    match v {
      parsing::ParsedPlaceHolderVal::Attribute(n) => {
        if let Some(field_value) = entry_fields.get(&n) {
          send_text(field_value, inter_key_delay)
        }
      }
      parsing::ParsedPlaceHolderVal::KeyName(n, repeat) => {
        let k = KeyEventCode::try_from(n.as_str())?;
        send_key(k);
      }
      parsing::ParsedPlaceHolderVal::Delay(delay) => {
        if delay < (MAX_KEY_DELAY as i32) {
          inter_key_delay = delay as u64;
        }
      }
      parsing::ParsedPlaceHolderVal::KeyPressDelay(delay) => {}
      parsing::ParsedPlaceHolderVal::Modfier(_) => {}
    }
  }

  // let utf16: Vec<u16> = "Hello World".encode_utf16().collect();
  // for c in utf16 {
  //   //println!("Sending char {}", c);
  //   send_char(c);
  //   std::thread::sleep(std::time::Duration::from_millis(100)); //max delay 500ms
  // }
  // send_key(KeyEventCode::kVK_Tab);

  Ok(())
}

pub struct AutoTypeWindowOperationImpl {}

impl AutoTypeWindowOperation for AutoTypeWindowOperationImpl {
  fn window_titles(&self) -> kp_service::Result<Vec<WindowInfo>> {
    let widows: SRObjectArray<WindowDetail> = unsafe { auto_type_window_titles() };
    let excludes = vec![Some("OneKeePass".into()), Some("OneKeePass.app".into())];
    let mut infos = vec![];
    for w in widows.as_slice() {
      infos.push(WindowInfo {
        owner: w.owner.as_ref().map(|s| s.to_string()),
        title: w.name.as_ref().map(|s| s.to_string()),
        process_id: w.pid as i32,
      })
    }
    let infos = infos
      .into_iter()
      .filter(|v| !excludes.contains(&v.owner))
      .collect::<Vec<WindowInfo>>();
    Ok(infos)
  }

  fn raise_window(&self, process_id: i32) -> kp_service::Result<()> {
    unsafe {
      auto_type_activate_window(process_id);
    }
    Ok(())
  }

  fn send_sequence(&self, sequence: &str) -> kp_service::Result<()> {
    unimplemented!()
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  #[test]
  fn verify_window_titles() {
    let r = active_window_titles();
    assert!(r.is_ok());
    println!("Windows are \n {:?}", r.unwrap());
  }

  #[test]
  fn verify_raise_window() {
    let r = raise_window(950000000);
    println!("r is {:?}", r);
  }

  #[test]
  fn verify_raise_and_send_sequence() {
    let w = WindowInfo {
      owner: None,
      title: None,
      process_id: 950,
    };

    let seq = "{USERNAME}{TAB}{PASSWORD}";

    let fields = HashMap::from([
      ("USERNAME".to_string(), "aTesterAcct".to_string()),
      ("PASSWORD".to_string(), "qdadasdada".to_string()),
  ]);

    let r = raise_and_send_sequence(w, seq, fields);

    println!("r is {:?}", &r);
  }
}

/*
fn window_titles() {
  let widows: SRObjectArray<WindowDetail> = unsafe { auto_type_window_titles() };

  for w in widows.as_slice() {
    println!(
      " name is {} with process id {}",
      &w.name.as_ref().map_or("", |s| s.as_str()),
      &w.pid
    );
  }
}

fn activate_window(pid: i32) {
  unsafe {
    auto_type_activate_window(pid);
  }
  println!("Activated window pid {}", pid);
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TestArg {
  cmd_name: String,
  pid: Option<i32>,
}

pub fn test_call(arg: TestArg) {
  match arg.cmd_name.as_str() {
    "window_titles" => window_titles(),
    "activate_window" => {
      if let Some(p) = arg.pid {
        activate_window(p);
      } else {
        println!("activate_window is not called");
      }
    }
    _ => {
      println!("Unknown commnad in arg {:?}", arg)
    }
  }
}
*/
