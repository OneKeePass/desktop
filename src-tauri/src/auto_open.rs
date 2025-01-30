use nom::bytes::complete::tag;
use nom::combinator::rest;
use nom::sequence::preceded;
use nom::{branch::alt, IResult};
use onekeepass_core::error::{self, Error, Result};
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Default, Debug, Deserialize)]
pub(crate) struct AutoOpenProperties {
  source_db_key: String,
  url_field_value: Option<String>,
  key_file_path: Option<String>,
  device_if_val: Option<String>,
}

#[derive(Default, Debug, Serialize)]
pub(crate) struct AutoOpenPropertiesResolved {
  url_field_value: Option<String>,
  key_file_path: Option<String>,
  can_open: bool,
}

impl AutoOpenProperties {
  pub(crate) fn resolve(&self) -> Result<AutoOpenPropertiesResolved> {
    let mut out = AutoOpenPropertiesResolved::default();

    self.apply_if_device_condition(&mut out);
    // Early return if this device is excluded from auto open launch
    if !out.can_open {
      return Ok(out);
    }

    let db_dir_path = Path::new(&self.source_db_key);

    // Ensure that the db file (the db that has the auto open entry) is correct
    // Will this fail any time?
    if !db_dir_path.exists() {
      let er = format!(
        "Invalid db_key {}. The db file is not found",
        &self.source_db_key
      );
      return Err(Error::UnexpectedError(er));
    }

    // We should be able to find the parent dir from the auto open master db's path
    let Some(parent_dir_path) = db_dir_path.parent() else {
      return Err(Error::UnexpectedError("Parent dir is not found".into()));
    };

    // The child database path is parsed only if it is not an empty string
    if let Some(ref db_v) = self.url_field_value.as_ref().map(|v| v.trim().to_string()) {
      if !db_v.is_empty() {
        out.url_field_value = Some(resolved_path(parent_dir_path, db_v)?);
      }
    }

    // The child database key file path is parsed only if it is not an empty string
    if let Some(ref kf_v) = self.key_file_path.as_ref().map(|v| v.trim().to_string()) {
      if !kf_v.is_empty() {
        out.key_file_path = Some(resolved_path(parent_dir_path, kf_v)?);
      }
    }

    Ok(out)
  }

  fn apply_if_device_condition(&self, out: &mut AutoOpenPropertiesResolved) {
    if let Some(val) = &self.device_if_val {
      let computer = gethostname::gethostname()
        .to_string_lossy()
        .to_string()
        .to_lowercase();

      let devices: Vec<String> = val.split(",").map(|s| s.trim().to_lowercase()).collect();

      let mut exclude = false;

      for device_id in &devices {
        if device_id.starts_with("!") {
          let exclude_matched = device_id
            .strip_prefix("!")
            .is_some_and(|s| s.trim() == &computer);

          if exclude_matched {
            // The device is explicitly excluded and opening is skipped
            exclude = true;
            break;
          }
        } else {
          if device_id == &computer {
            // The device is explicitly listed and we can open the db
            exclude = false;
            break;
          }
        }
      }
      // The database can be opened when the final  'not exclude' is true
      out.can_open = !exclude;
    } else {
      // User has not set any devices in the field 'IfDevice'
      out.can_open = true;
    }
  }
}

fn resolved_path(parent_dir_path: &Path, in_path: &String) -> Result<String> {
  let (remaining, parsed) = parse_field_value(in_path)
    .map_err(|e| error::Error::UnexpectedError(format!("Parsing failed with error {}", e)))?;

  if !remaining.is_empty() {
    return Err(Error::UnexpectedError("Url parsing is not complete".into()));
  }

  let final_path = if parsed.full_path_part.starts_with("file://") {
    parsed.full_path_part.to_string()
  } else {
    let p = parent_dir_path.join(&parsed.full_path_part);
    // If the canonicalize path is not found, then this will return an error
    p.canonicalize().map_err(|e| Error::UnexpectedError(format!("Error: Path {:?} : {}", &p,e)))?.to_string_lossy().to_string()
  };

  // let mut p = parent_dir_path.join(&parsed.full_path_part);

  // // If the canonicalize path is not found, then this will return an error
  // p = p.canonicalize().map_err(|e| Error::UnexpectedError(format!("Error: Path {:?} : {}", &p,e)))?;

  Ok(final_path)
}

#[derive(Debug)]
struct ParsedVal<'a> {
  full_path_part: &'a str,
}

fn parse_field_value(field_value: &str) -> IResult<&str, ParsedVal<'_>> {
  // The incoming 'field_value' should be parsed by one of the 'alt' parsers
  let r = alt((
    // rest parser will have the part after removing the matching tag
    preceded(tag("kdbx://{DB_DIR}/"), rest),
    preceded(tag("kdbx://"), rest),
    // This will match all other str value that do not start with 'kdbx'
    rest,
  ))(field_value)?;

  // r.0 is remaining 'str'. It should be empty on successful parsing
  Ok((
    r.0,
    ParsedVal {
      full_path_part: r.1,
    },
  ))
}

#[cfg(test)]
mod tests {

  use super::AutoOpenProperties;

  #[test]
  fn verify_path_resolve() {
    let source_db_key = "/Users/jeyasankar/Documents/OneKeePass/TestAutoOpenXC.kdbx";
    let k_url = "./f1//mytestkey2.keyx";
    let db_url = "kdbx://{DB_DIR}/f1/PasswordsUsesKeyFile2.kdbx";

    let my_computer = gethostname::gethostname().to_string_lossy().to_string();
    // my_computer is excluded
    let devices = format!(
      "Computer-one, Computer-Two,  ! Computer-Six, !Computer-nine, !{}",
      &my_computer
    );

    let mut input = AutoOpenProperties {
      source_db_key: source_db_key.to_string(),
      url_field_value: Some(db_url.into()),
      key_file_path: Some(k_url.into()),
      device_if_val: Some(devices),
    };

    let resolved = input.resolve().unwrap();

    println!("resolved is {:?}", resolved);

    assert_eq!(resolved.can_open, false, "Expected false");
    assert_eq!(resolved.url_field_value, None);
    assert_eq!(resolved.key_file_path, None);

    //////

    // my_computer is included
    let devices = format!(
      "Computer-one, Computer-Two,  ! Computer-Six, !Computer-nine, {}",
      &my_computer
    );

    input.device_if_val = Some(devices);

    let resolved = input.resolve().unwrap();

    println!("resolved is {:?}", resolved);

    assert_eq!(resolved.can_open, true, "Expected true");
    assert_eq!(
      resolved.url_field_value,
      Some("/Users/jeyasankar/Documents/OneKeePass/f1/PasswordsUsesKeyFile2.kdbx".into())
    );
  }

  #[test]
  fn verify_file_path_resolve() {
    let source_db_key = "/Users/jeyasankar/Documents/OneKeePass/TestAutoOpenXC.kdbx";
    let k_url = "file://f1//mytestkey2.keyx";
    let db_url = "kdbx://file://myshare/f1/PasswordsUsesKeyFile2.kdbx";

    let input = AutoOpenProperties {
      source_db_key: source_db_key.to_string(),
      url_field_value: Some(db_url.into()),
      key_file_path: Some(k_url.into()),
      device_if_val: None,
    };

    let resolved = input.resolve().unwrap();

    println!("resolved is {:?}", resolved);

  }
}

/*
#[test]
  fn verify2() {
    let cv = "My-MacBook-Pro16-M1.local".to_lowercase();
    let s = "Computer-one, Computer-Two,    ! Computer-Six, !Computer-nine,!My-MacBook-Pro16-M1.local";
    let v: Vec<String> = s.split(",").map(|s| s.trim().to_lowercase()).collect();
    println!("v is {:?}", &v);

    let mut exclude = false;
    for c in &v {
      if c.starts_with("!") {
        let v1 = c.strip_prefix("!");
        let v2 = c.strip_prefix("!").is_some_and(|s| s.trim() == &cv);
        println!("no ! {:?},{}", &v1, v2);
        if v2 {
          exclude = true;
          break;
        }
      } else {
        if c == &cv {
          exclude = false;
          break;
        }
      }
    }

    println!("Exclude is {}", &exclude);
  }

  #[test]
  fn verify1() {
    //println!("Host name is {:?}", hostname::get());

    //println!("Host name 2 is {:?}", gethostname::gethostname().to_string_lossy().to_string());

    println!(
      "Result {:?} ",
      super::parse_field_value("kdbx://{DB_DIR}/../Test14.kdbx")
    );

    println!("Result {:?} ", super::parse_field_value("./Test14.kdbx"));

    println!(
      "Result {:?} ",
      super::parse_field_value("kdbx://../Test14.kdbx")
    );

    // let b = "/Users/jeyasankar/Documents/OneKeePass";
    // let mut p = PathBuf::from(&b);
    // p.push("/f1/PasswordsUsesKeyFile2.kdbx");
    // //let s = p.push("f1/PasswordsUsesKeyFile2.kdbx");

    // println!("s is {:?}", &p);
  }

*/