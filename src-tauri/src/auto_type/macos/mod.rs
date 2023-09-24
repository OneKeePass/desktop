mod key_codes;

use self::key_codes::KeyEventCode;
use onekeepass_core::db_service as kp_service;
use std::collections::HashMap;
use swift_rs::{swift, Int, Int32, SRObjectArray, SRString, UInt16, UInt8};
//use crate::auto_type::WindowInfo; // This will also work
use super::{parsing, WindowInfo};

#[repr(C)]
struct WindowDetail {
  name: Option<SRString>,
  owner: Option<SRString>,
  pid: Int,
}

// All extern C calls to the implementation done is the Swift Package

swift!(fn auto_type_window_titles() -> SRObjectArray<WindowDetail>);
// Returns string error message if there is any error
swift!(fn auto_type_activate_window(pid:Int32) -> Option<SRString>);
// Called to send each char as utf16 code
swift!(fn auto_type_send_char(char_utf16:UInt16));
// Called to send keys such as tab, enter ...
swift!(fn auto_type_send_key(vk_key_code:UInt8));

///

// Milli seconds
const DEFAULT_KEY_DELAY: u64 = 25;
const MAX_KEY_DELAY: u64 = 500;
const MAX_SEND_WAIT: u64 = 10000; //10 sec

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

fn _send_char(char_utf16: u16) {
  unsafe { auto_type_send_char(char_utf16) }
}

#[inline]
fn sleep(time_in_ms: u64) {
  std::thread::sleep(std::time::Duration::from_millis(time_in_ms));
}

fn send_key(key_code: KeyEventCode) {
  unsafe { auto_type_send_key(key_code as u8) }
}

fn send_text(text: &str, inter_key_delay: u64) {
  let utf16: Vec<u16> = text.encode_utf16().collect();
  for char_utf16 in utf16 {
    unsafe { auto_type_send_char(char_utf16) };
    // wait before sending the next char
    sleep(inter_key_delay);
  }
}

pub fn send_sequence_to_winow(
  window: WindowInfo,
  sequence: &str,
  entry_fields: HashMap<String, String>,
) -> kp_service::Result<()> {

  // All field names are to be in Upper case 
  let entry_fields_case_converted:HashMap<String,String> = entry_fields.iter().map(|(k,v)| (k.to_uppercase().clone(), v.clone())).collect();
  
  // Parse sequence first
  let parsed =
    parsing::parse_auto_type_sequence(sequence,&entry_fields_case_converted).map_err(|e| kp_service::Error::Other(e))?;

  // Raise window to the top
  raise_window(window.process_id)?;

  // Send chars and key actions with required delay
  let mut inter_key_delay = DEFAULT_KEY_DELAY;

  #[allow(unused_assignments)]
  let mut send_pause_time = 1u64; // 1 millisecond

  for v in parsed {
    match v {
      parsing::ParsedPlaceHolderVal::Attribute(n) => {
        if let Some(field_value) = entry_fields_case_converted.get(&n) {
          send_text(field_value, inter_key_delay)
        }
      }
      parsing::ParsedPlaceHolderVal::KeyName(n, mut repeat) => {
        let k = KeyEventCode::try_from(n.to_uppercase().as_str())?;
        // Send the key 1 or more time
        while repeat > 0 {
          send_key(k.clone());
          repeat -= 1;
        }
      }
      parsing::ParsedPlaceHolderVal::KeyPressDelay(delay) => {
        // Sets the delay between key presses to 'delay' milliseconds
        if delay < (MAX_KEY_DELAY as i32) {
          inter_key_delay = delay as u64;
        } else {
          inter_key_delay = MAX_KEY_DELAY;
        }
        println!("inter_key_delay is set {}", &inter_key_delay);
      }
      parsing::ParsedPlaceHolderVal::Delay(delay) => {
        // New typing pause time
        send_pause_time = if delay <= (MAX_SEND_WAIT as i32) {
          delay as u64
        } else {
          MAX_SEND_WAIT
        };
        //Pause typing for 'send_pause_time' milliseconds
        sleep(send_pause_time);
      }
      parsing::ParsedPlaceHolderVal::Modfier(_) => {}
    }
  }
  Ok(())
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
  fn verify_raise_non_existing_window() {
    let r = raise_window(950000000);
    println!("r is {:?}", r);
    assert!(r.is_err());
  }

  #[test]
  fn verify_send_sequence_to_winow() {
    let w = WindowInfo {
      owner: None,
      title: None,
      // Get the process id using 'Activity Monitor'
      process_id: 965,
    };

    let seq = "{USERNAME}{TAB 3}{delay=50} {PASSWORD} {SPACE} {ENTEr}";

    let fields = HashMap::from([
      ("USERNAME".to_string(), "aTesterAcct".to_string()),
      ("PASSWORD".to_string(), "qdadasdada".to_string()),
    ]);

    let r = send_sequence_to_winow(w, seq, fields);

    println!("r is {:?}", &r);
  }
}
