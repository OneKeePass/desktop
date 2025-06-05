#![allow(dead_code, non_snake_case, non_upper_case_globals)]

#[cfg(target_os = "macos")]
pub fn supported_biometric_type() -> String {
  macos::supported_biometric_type()
}
#[cfg(target_os = "macos")]
pub fn authenticate_with_biometric(db_key: &str) -> bool {
  macos::authenticate_with_biometric(db_key)
}

#[cfg(any(target_os = "windows", target_os = "linux"))]
pub fn supported_biometric_type() -> String {
  "None".into()
}
#[cfg(any(target_os = "windows", target_os = "linux"))]
pub fn authenticate_with_biometric(db_key: &str) -> bool {
  false
}

#[cfg(target_os = "macos")]
mod macos {
  use swift_rs::{swift, Bool, Int};

  swift!(fn available_biometric_type() -> Int);
  swift!(fn biometric_authentication() -> Bool);

  #[non_exhaustive]
  pub enum BiometryType {
    None,
    TouchID,
    FaceID,
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
        _ => Self::None,
      }
    }
  }

  pub fn supported_biometric_type() -> String {
    let t = unsafe { available_biometric_type() };
    //debug!("available_biometric_type returned {}", t);
    BiometryType::from(t as i32).to_string()
  }

  pub fn authenticate_with_biometric(_db_key: &str) -> bool {
    let r = unsafe { biometric_authentication() };
    r
  }
}
