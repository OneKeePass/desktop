use std::{collections::HashMap, sync::Arc};

use keyring_core::{CredentialStore, Entry, Error as KeyringError};
use log::{debug, error, warn};
use onekeepass_core::db_service::{self as kp_service, KeyStoreService};
use secstr::SecVec;
use zbus_secret_service_keyring_store::Store;

const SERVICE_NAME: &str = "OneKeePass";

// Linux enc-key storage backed by the freedesktop Secret Service API.
//
// GNOME Keyring, KWallet, and other Secret Service implementations can
// provide the actual storage. If no service is available, or an operation
// fails, the key is retained in this process-local fallback so opening a
// database is not prevented by the desktop environment configuration.
pub struct KeyStoreServiceImpl {
    secure_store: Option<Arc<CredentialStore>>,
    fallback_store: HashMap<String, SecVec<u8>>,
}

impl Default for KeyStoreServiceImpl {
    fn default() -> Self {
        let secure_store = match Store::new() {
            Ok(store) => {
                debug!("Linux Secret Service enc-key store is available");
                Some(store as Arc<CredentialStore>)
            }
            Err(e) => {
                warn!(
                    "Linux Secret Service enc-key store is unavailable; using the in-memory fallback: {e}"
                );
                None
            }
        };

        Self {
            secure_store,
            fallback_store: HashMap::new(),
        }
    }
}

#[inline]
fn formatted_key(db_key: &str) -> String {
    format!(
        "OKP-{}",
        kp_service::service_util::string_to_simple_hash(db_key)
    )
}

impl KeyStoreServiceImpl {
    fn entry(&self, db_key: &str) -> keyring_core::Result<Entry> {
        let store = self
            .secure_store
            .as_ref()
            .ok_or(KeyringError::NoDefaultStore)?;
        store.build(SERVICE_NAME, &formatted_key(db_key), None)
    }

    fn store_in_secret_service(&self, db_key: &str, data: &[u8]) -> keyring_core::Result<()> {
        // Hex keeps the payload compatible with KWallet implementations that
        // only accept UTF-8 secrets.
        self.entry(db_key)?.set_password(&hex::encode(data))
    }

    fn get_from_secret_service(&self, db_key: &str) -> keyring_core::Result<Vec<u8>> {
        let encoded = self.entry(db_key)?.get_password()?;
        hex::decode(encoded).map_err(|e| KeyringError::BadDataFormat(Vec::new(), Box::new(e)))
    }
}

impl KeyStoreService for KeyStoreServiceImpl {
    fn store_key(&mut self, db_key: &str, data: Vec<u8>) -> kp_service::Result<()> {
        debug!("Storing Linux database enc key ({} bytes)", data.len());

        match self.store_in_secret_service(db_key, &data) {
            Ok(()) => {
                // A previous failed write may have populated the fallback.
                self.fallback_store.remove(db_key);
                Ok(())
            }
            Err(e) => {
                warn!(
                    "Storing the database enc key in Linux Secret Service failed; using the in-memory fallback: {e}"
                );
                self.fallback_store
                    .insert(db_key.to_owned(), SecVec::new(data));
                Ok(())
            }
        }
    }

    fn get_key(&self, db_key: &str) -> Option<Vec<u8>> {
        if let Some(value) = self.fallback_store.get(db_key) {
            return Some(Vec::from(value.unsecure()));
        }

        match self.get_from_secret_service(db_key) {
            Ok(data) => Some(data),
            Err(e) => {
                error!("Getting the database enc key from Linux Secret Service failed: {e}");
                None
            }
        }
    }

    fn delete_key(&mut self, db_key: &str) -> kp_service::Result<()> {
        self.fallback_store.remove(db_key);

        match self
            .entry(db_key)
            .and_then(|entry| entry.delete_credential())
        {
            Ok(()) | Err(KeyringError::NoEntry) => {}
            Err(e) => warn!("Deleting the database enc key from Linux Secret Service failed: {e}"),
        }

        Ok(())
    }

    fn copy_key(&mut self, source_db_key: &str, target_db_key: &str) -> kp_service::Result<()> {
        if let Some(data) = self.get_key(source_db_key) {
            self.store_key(target_db_key, data)?;
        } else {
            warn!("The source database enc key was unavailable while copying it");
        }

        Ok(())
    }
}
