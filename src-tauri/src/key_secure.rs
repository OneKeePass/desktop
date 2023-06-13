use log::debug;
use once_cell::sync::Lazy;
use std::{
  collections::HashMap,
  sync::{Arc, Mutex},
};

/////////////////////////////

use onekeepass_core::db_service::{KeyStoreService, KeyStoreServiceType,set_key_store_service_instance};

pub fn init_key_main_store() {
  let kss = key_main_store();
  debug!("key_secure - key_main_store is initialized ");
  set_key_store_service_instance(kss);
}

fn key_main_store() -> &'static KeyStoreServiceType {
  static KEY_MAIN_STORE: Lazy<KeyStoreServiceType> =
    Lazy::new(|| Arc::new(Mutex::new(KeyStoreServiceImpl::default())));
  //set_instance(&KEY_MAIN_STORE);
  &KEY_MAIN_STORE
}

#[derive(Default)]
struct KeyStoreServiceImpl {
  store: HashMap<String, Vec<u8>>,
}

impl KeyStoreService for KeyStoreServiceImpl {
  fn store_key(&mut self, db_key: &str, data: Vec<u8>) -> kp_service::Result<()> {
    // On successful loading of database, the keys are encrypted with Aes GCM cipher
    // and the encryption key for keys is stored in the KeyChain for macOS.
    // For now in case of Windows and Linux, we keep it locally

    debug!("store_key is called and data size {}", data.len());
    self.store.insert(db_key.into(), data);
    debug!("encryption key data is inserted");
    Ok(())
  }

  fn get_key(&self, db_key: &str) -> Option<Vec<u8>> {
    self.store.get(db_key).cloned()
  }

  fn delete_key(&mut self, db_key: &str) {
    self.store.remove(db_key);
    debug!("Keys are deleted..");
  }

  fn copy_key(&mut self, source_db_key: &str, target_db_key: &str) -> kp_service::Result<()> {
    
    debug!("Going to copy enc key from {} to {}",source_db_key,target_db_key);
    if let Some(source_db_key) = self.store.get(source_db_key).cloned() {
      self.store.insert(target_db_key.into(), source_db_key);
      debug!("Keys are copied...");
    }

    Ok(())
  }
}

//////////////////////////
use onekeepass_core::db_service as kp_service;
use onekeepass_core::db_service::SecureKeyInfo;

//type KeyStore = Arc<Mutex<HashMap<String, Vec<u8>>>>;

type KeyStore = Mutex<HashMap<String, Vec<u8>>>;

fn key_store() -> &'static KeyStore {
  static KEY_STORE: Lazy<KeyStore> = Lazy::new(Default::default);
  &KEY_STORE
}

pub fn store_key(db_key: &str, data: Vec<u8>) -> kp_service::Result<()> {
  // On successful loading of database, the keys are encrypted with Aes GCM cipher
  // and the encryption key for keys is stored in the KeyChain for macOS.
  // For now in case of Windows and Linux, we keep it locally
  let mut m = key_store().lock().unwrap();
  debug!("store_key is called and data size {}", data.len());
  m.insert(db_key.into(), data);
  debug!("encryption key data is inserted");
  Ok(())
}

pub fn get_key(db_key: &str) -> Option<SecureKeyInfo> {
  let m = key_store().lock().unwrap();
  debug!(
    "get_key is called and data size {:?} ",
    m.get(db_key).map(|v| v.len())
  );
  m.get(db_key)
    .map(|v| SecureKeyInfo::from_key_nonce(v.clone()))
}

pub fn delete_key(db_key: &str) {
  let mut m = key_store().lock().unwrap();
  m.remove(db_key);
}

// Need to reassign encryption key when we use 'Save as'
pub fn reassign_key(new_db_key: &str, old_db_key: &str) -> kp_service::Result<()> {
  let mut m = key_store().lock().unwrap();
  let kd = m.remove(old_db_key);
  if let Some(key_data) = kd {
    m.insert(new_db_key.into(), key_data);
  }
  debug!(
    "Encryption key is reassigned from {} to {} ",
    old_db_key, new_db_key
  );
  Ok(())
}

pub fn copy_key(source_db_key: &str, target_db_key: &str) -> kp_service::Result<()> {
  let source_key = {
    let m = key_store().lock().unwrap();
    m.get(source_db_key).cloned()
  };

  let mut m = key_store().lock().unwrap();
  if let Some(k) = source_key {
    m.insert(target_db_key.into(), k);
  }

  Ok(())
}
