//#[cfg(all(target_os = "macos", not(feature = "onekeepass-dev")))]
#[cfg(target_os = "macos")]
#[path = "macos.rs"]
mod imp;

#[cfg(any(target_os = "windows", target_os = "linux"))]
//#[cfg(any(target_os = "windows", target_os = "linux", feature = "onekeepass-dev"))]
#[path = "other.rs"]
mod imp;

use self::imp::KeyStoreServiceImpl;

use log::debug;
use std::sync::{Arc, Mutex};

use onekeepass_core::db_service::KeyStoreOperation;

pub fn init_key_main_store() {
  let kss = Arc::new(Mutex::new(KeyStoreServiceImpl::default()));
  // In case, we need to hold any reference at this module, then we need to Arc::clone
  // and use it
  KeyStoreOperation::init(kss);
  debug!("key_secure - key_main_store is initialized in init_key_main_store ");
}

/*
#[cfg(all(target_os = "macos", not(feature = "onekeepass-dev")))]
mod macos;

use log::debug;
use secstr::SecVec;
use std::{
  collections::HashMap,
  sync::{Arc, Mutex},
};

use onekeepass_core::{
  db_service as kp_service,
  db_service::{KeyStoreOperation, KeyStoreService},
};


pub fn init_key_main_store() {
  let kss = Arc::new(Mutex::new(KeyStoreServiceImpl::default()));
  // In case, we need to hold any reference at this module, then we need to Arc::clone
  // and use it
  KeyStoreOperation::init(kss);
  debug!("key_secure - key_main_store is initialized in init_key_main_store ");
}

#[derive(Default)]
struct KeyStoreServiceImpl {
  store: HashMap<String, SecVec<u8>>,
}

impl KeyStoreService for KeyStoreServiceImpl {
  fn store_key(&mut self, db_key: &str, data: Vec<u8>) -> kp_service::Result<()> {
    // On successful loading of database, the keys are encrypted with Aes GCM cipher
    // and the encryption key for keys is stored in the KeyChain for macOS.
    // For now in case of Windows and Linux, we keep it locally

    debug!("store_key is called and data size {}", data.len());
    #[cfg(all(target_os = "macos", not(feature = "onekeepass-dev")))]
    {
      macos::store_key(db_key, data)
    }

    #[cfg(any(target_os = "windows", target_os = "linux", feature = "onekeepass-dev"))]
    {
      self.store.insert(db_key.into(), SecVec::new(data));
      debug!("Encrypted key is stored for other cfg");
      Ok(())
    }
  }

  fn get_key(&self, db_key: &str) -> Option<Vec<u8>> {
    #[cfg(all(target_os = "macos", not(feature = "onekeepass-dev")))]
    {
      macos::get_key(db_key)
    }

    #[cfg(any(target_os = "windows", target_os = "linux", feature = "onekeepass-dev"))]
    {
      self.store.get(db_key).map(|v| Vec::from(v.unsecure()))
    }
  }

  fn delete_key(&mut self, db_key: &str) -> kp_service::Result<()> {
    #[cfg(all(target_os = "macos", not(feature = "onekeepass-dev")))]
    {
      macos::delete_key(db_key)
    }

    #[cfg(any(target_os = "windows", target_os = "linux", feature = "onekeepass-dev"))]
    {
      self.store.remove(db_key);
      debug!("Keys are deleted..");
      Ok(())
    }
  }

  fn copy_key(&mut self, source_db_key: &str, target_db_key: &str) -> kp_service::Result<()> {
    debug!(
      "Going to copy enc key from {} to {}",
      source_db_key, target_db_key
    );
    if let Some(source_db_key) = self.store.get(source_db_key).cloned() {
      self.store.insert(target_db_key.into(), source_db_key);
      debug!("Keys are copied...");
    }

    Ok(())
  }
}

*/

//use once_cell::sync::Lazy;
// fn key_main_store() -> &'static KeyStoreServiceType {
//   static KEY_MAIN_STORE: Lazy<KeyStoreServiceType> =
//     Lazy::new(|| Arc::new(Mutex::new(KeyStoreServiceImpl::default())));
//   &KEY_MAIN_STORE
// }

/*
cfg_if::cfg_if! {
      if #[cfg(all (target_os = "macos", not(feature = "onekeepass-dev")))] {
        macos::store_key();

      } else if #[cfg(any(target_os = "windows", target_os="unix",feature = "onekeepass-dev"))] {

        self.store.insert(db_key.into(), SecVec::new(data));
        debug!("encryption key data is inserted");
      }
    }



    cfg_if::cfg_if! {
  if #[cfg(all (target_os = "macos", not(feature = "onekeepass-dev")))] {
    mod macos;
    use macos::KeyStoreServiceImpl;
  } else if #[cfg(any(target_os = "windows", target_os="unix",feature = "onekeepass-dev"))] {
    mod other;
    use crate::other::KeyStoreServiceImpl
  }
}
 */
