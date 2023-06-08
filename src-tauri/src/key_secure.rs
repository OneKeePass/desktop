use once_cell::sync::Lazy;
use std::{
  collections::HashMap,
  sync::{Arc, Mutex},
};

type KeyStore = Arc<Mutex<HashMap<String, Vec<u8>>>>;

fn key_store() -> &'static KeyStore {
  static KEY_STORE: Lazy<KeyStore> = Lazy::new(Default::default);
  &KEY_STORE
}

pub fn store_key(db_key: &str) {
    // On successful loading of database, the keys are encrypted with Aes GCM cipher
    // and the encryption key for keys is stored in the KeyChain for macOS. 
    // For now in case of Windows and Linux, we keep it locally

}
