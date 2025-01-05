#![allow(dead_code, non_snake_case, non_upper_case_globals)]

use secstr::SecVec;
use std::collections::HashMap;

use onekeepass_core::db_service as kp_service;

#[derive(Default)]
pub struct KeyStoreServiceImpl {
  store: HashMap<String, SecVec<u8>>,
}

#[inline]
fn formatted_key(db_key: &str) -> SRString {
  format!("OKP-{}", kp_service::service_util::string_to_simple_hash(db_key))
    .as_str()
    .into()
}

impl kp_service::KeyStoreService for KeyStoreServiceImpl {
  fn store_key(&mut self, db_key: &str, data: Vec<u8>) -> kp_service::Result<()> {
    

    #[allow(unused_assignments)]
    let mut keychain_call_success = false;

    let sr_db_key: SRString = formatted_key(db_key); //db_key.into();
    let sr_enc_key: SRString = hex::encode(&data).as_str().into();

    let staus_code = unsafe { save_key_in_key_chain(&sr_db_key, &sr_enc_key) };
    keychain_call_success = staus_code == 0;

    //info!("Swift save call completed with return code {}", staus_code);

    // 'SecItemAdd' will fail with error code "-25299" (errSecDuplicateItem) if the key is already found in the key chain.
    // Firts it needs to be deleted and then added again.
    // This 'delete and add' should not happen as we delete the key from key chain when the database is closed
    if staus_code == -25299 {
      let staus_code = unsafe { delete_key_in_key_chain(&sr_db_key) };
      keychain_call_success = staus_code == 0;

      //info!( "Swift delete call completed with return code {}",staus_code);

      if staus_code == 0 {
        let staus_code = unsafe { save_key_in_key_chain(&sr_db_key, &sr_enc_key) };
        keychain_call_success = staus_code == 0;
        //info!("Swift save again call completed with return code {}",staus_code);
      }
    }

    // As a fallback, we keep the enc key locally if the above key chain calls fail
    if !keychain_call_success {
      //info!("Saving to key chain failed and using local store");
      self.store.insert(db_key.into(), SecVec::new(data));
      return Ok(());
    }

    //debug!("Storing to the key chain is successful");

    Ok(())
  }

  fn get_key(&self, db_key: &str) -> Option<Vec<u8>> {
    // This is not expected. As a precautionary, we check the local store
    if let Some(v) = self.store.get(db_key) {
      return Some(Vec::from(v.unsecure()));
    }

    let sr_db_key: SRString = formatted_key(db_key);
    let key_str = unsafe { get_key_from_key_chain(&sr_db_key) };
    //debug!("Get key returned {}", key_str);

    // key_str will have Error prefix in case of any key chain error and hex decode will fail
    let val = match hex::decode(key_str.as_str()) {
      Ok(v) => Some(v),
      Err(e) => {
        log::error!(
          "Hex decoding failed for the value {} with error {}",
          key_str,
          e
        );
        None
      }
    };

    val
  }

  fn delete_key(&mut self, db_key: &str) -> kp_service::Result<()> {
    let sr_db_key: SRString = formatted_key(db_key);
    let _staus_code = unsafe { delete_key_in_key_chain(&sr_db_key) };
    //debug!("Key is deleted.. with status_code {}", staus_code);

    Ok(())
  }

  fn copy_key(&mut self, source_db_key: &str, target_db_key: &str) -> kp_service::Result<()> {
    // This is not expected. As a precautionary, we check the local store
    if let Some(source_db_key) = self.store.get(source_db_key) {
      self
        .store
        .insert(target_db_key.into(), source_db_key.clone());
      //debug!("Keys are copied from local store...");
      return Ok(());
    }

    let sr_db_key: SRString = formatted_key(source_db_key);
    let tr_db_key: SRString = formatted_key(target_db_key);

    let key_str = unsafe { get_key_from_key_chain(&sr_db_key) };
    //debug!("In copy_key, get key returned {}", key_str);
    if key_str.starts_with("Error") {
      return Err(onekeepass_core::error::Error::UnexpectedError(format!(
        "Copying key failed with error {}",
        &key_str
      )));
    }

    let staus_code = unsafe { save_key_in_key_chain(&tr_db_key, &key_str) };
    if staus_code != 0 {
      return Err(onekeepass_core::error::Error::UnexpectedError(format!(
        "Copying key failed with error code {}",
        staus_code
      )));
    }
    Ok(())
  }
}

//--------------------------------------------------------------------------------------
use swift_rs::{swift, Int, Int32, SRString};

// See https://developer.apple.com/documentation/security/1542001-security_framework_result_codes
// Need to select Objective-C in the doc and we can see the numerical code
//https://developer.apple.com/documentation/security/1542001-security_framework_result_codes/errsecduplicatekeychain?language=objc

// Not used
// If we use, the swift side the return value is of type KeychainActionResult
// and we can use swift!(fn save_key_in_key_chain(db_key:&SRString,data:&SRString) -> SRObject<KeychainActionResult>);
// #[repr(C)]
// struct KeychainActionResult {
//   error_code: Int,
// }

swift!(fn save_key_in_key_chain(db_key:&SRString,data:&SRString) -> Int);
swift!(fn delete_key_in_key_chain(db_key:&SRString) -> Int32);
swift!(fn get_key_from_key_chain(db_key:&SRString) -> SRString);

//--------------------------------------------------------------------------------------
