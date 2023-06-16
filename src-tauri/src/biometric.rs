#![allow(dead_code, non_snake_case, non_upper_case_globals)]

use log::debug;
use swift_rs::{swift, Bool, Int, SRObject, SRObjectArray, SRString};

swift!(fn available_biometric_type() -> Int);
swift!(fn biometric_authentication() -> Bool);

#[non_exhaustive]
pub enum BiometryType {
    None,TouchID,FaceID
}

impl BiometryType {
    fn to_string(&self) -> String {
        let s = match *self {
            Self::None => "None",
            Self::FaceID => "FaceID",
            Self::TouchID => "TouchID",
        };
        s.to_string()
    }
}

impl From<i32> for BiometryType {
    fn from(value: i32) -> Self {
        match value {
            0 => Self::None,
            1 => Self::TouchID,
            2 => Self::FaceID,
            _ => Self::None
        }
    }
}

pub fn supported_biometric_type() -> String {

    let t = unsafe {
        available_biometric_type()
    };
    //debug!("available_biometric_type returned {}", t);
    BiometryType::from(t as i32).to_string()
}

pub fn authenticate_with_biometric(_db_key: &str) -> bool {

    let r = unsafe {
        biometric_authentication()
    };
    r
}

/* 
swift!(fn save_key_in_secure_store());
swift!(fn read_key_from_secure_store());

pub fn save_key() {
  //sample_call();
  unsafe { save_key_in_secure_store() };
}

pub fn read_key() {
  unsafe { read_key_from_secure_store() };
}



use log::info;
use swift_rs::{swift, Bool, Int, SRObject, SRObjectArray, SRString};

#[repr(C)]
struct Volume {
  pub name: SRString,
  path: SRString,
  total_capacity: Int,
  available_capacity: Int,
  is_removable: Bool,
  is_ejectable: Bool,
  is_root_filesystem: Bool,
}

#[repr(C)]
struct Test {
  pub null: bool,
}

swift!(fn get_file_thumbnail_base64(path: &SRString) -> SRString);
swift!(fn get_mounts() -> SRObjectArray<Volume>);
swift!(fn return_nullable(null: Bool) -> Option<SRObject<Test>>);

pub fn sample_call() {
  let path = "/Users";
  let thumbnail = unsafe { get_file_thumbnail_base64(&path.into()) };
  info!(
    "length of base64 encoded thumbnail: {}",
    thumbnail.as_str().len()
  );

  let mounts = unsafe { get_mounts() };
  info!("First Volume Name: {}", mounts[0].name);

  let opt = unsafe { return_nullable(true) };
  info!("function returned nil: {}", opt.is_none());

  let opt = unsafe { return_nullable(false) };
  info!("function returned data: {}", opt.is_some());
}

*/
