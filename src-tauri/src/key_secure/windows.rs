#![allow(dead_code, non_snake_case, non_upper_case_globals)]

// Windows Credential Manager backed key store - the parallel of key_secure/macos.rs
// (macOS Keychain). It holds the per-session AES enc key used to encrypt the
// composite key in RAM, moving that enc key out of the app's own heap and into
// the OS credential store.
//
// Entries use CRED_PERSIST_SESSION so they are cleared on logoff and never left
// at rest: the enc key is per-session and never reused. An in-memory HashMap is
// kept as a fallback (same as the macOS impl) for the unlikely case a Credential
// Manager call fails.

use log::{debug, error};
use secstr::SecVec;
use std::collections::HashMap;

use onekeepass_core::db_service as kp_service;

use windows::core::{PCWSTR, PWSTR};
use windows::Win32::Foundation::FILETIME;
use windows::Win32::Security::Credentials::{
    CredDeleteW, CredFree, CredReadW, CredWriteW, CREDENTIALW, CRED_FLAGS, CRED_PERSIST_SESSION,
    CRED_TYPE_GENERIC,
};

#[derive(Default)]
pub struct KeyStoreServiceImpl {
    // In-memory fallback, mirroring the macOS impl. Used only if a Credential
    // Manager call fails.
    store: HashMap<String, SecVec<u8>>,
}

#[inline]
fn formatted_key(db_key: &str) -> String {
    format!(
        "OKP-{}",
        kp_service::service_util::string_to_simple_hash(db_key)
    )
}

// Null-terminated UTF-16 for the Win32 wide-string args.
fn to_wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

// Stores `data` under `target` as a generic credential. The blob is hex-encoded,
// mirroring the macOS Keychain path.
fn cred_write(target: &str, data: &[u8]) -> windows::core::Result<()> {
    let blob = hex::encode(data).into_bytes();
    let mut target_w = to_wide(target);

    let credential = CREDENTIALW {
        Flags: CRED_FLAGS(0),
        Type: CRED_TYPE_GENERIC,
        TargetName: PWSTR(target_w.as_mut_ptr()),
        Comment: PWSTR::null(),
        LastWritten: FILETIME::default(),
        CredentialBlobSize: blob.len() as u32,
        CredentialBlob: blob.as_ptr() as *mut u8,
        // SESSION: cleared on logoff. Keeps session-only semantics and avoids
        // leaving the per-session enc key at rest (not LOCAL_MACHINE/ENTERPRISE).
        Persist: CRED_PERSIST_SESSION,
        AttributeCount: 0,
        Attributes: std::ptr::null_mut(),
        TargetAlias: PWSTR::null(),
        UserName: PWSTR::null(),
    };

    // Safe: `blob` and `target_w` outlive this call; the struct is only read.
    unsafe { CredWriteW(&credential, 0) }
}

// Reads the hex-encoded blob stored under `target`.
fn cred_read(target: &str) -> windows::core::Result<Vec<u8>> {
    let target_w = to_wide(target);
    let mut pcred: *mut CREDENTIALW = std::ptr::null_mut();

    unsafe {
        CredReadW(PCWSTR(target_w.as_ptr()), CRED_TYPE_GENERIC, 0, &mut pcred)?;
    }

    // Copy the blob out, then free the OS-allocated credential.
    let result = unsafe {
        let cred = &*pcred;
        std::slice::from_raw_parts(cred.CredentialBlob, cred.CredentialBlobSize as usize).to_vec()
    };
    unsafe { CredFree(pcred as *const _) };

    Ok(result)
}

fn cred_delete(target: &str) -> windows::core::Result<()> {
    let target_w = to_wide(target);
    unsafe { CredDeleteW(PCWSTR(target_w.as_ptr()), CRED_TYPE_GENERIC, 0) }
}

impl kp_service::KeyStoreService for KeyStoreServiceImpl {
    fn store_key(&mut self, db_key: &str, data: Vec<u8>) -> kp_service::Result<()> {
        let target = formatted_key(db_key);
        match cred_write(&target, &data) {
            Ok(()) => {
                debug!("Enc key stored in Windows Credential Manager");
                Ok(())
            }
            Err(e) => {
                // Fallback: keep the enc key locally, same as the macOS impl.
                error!(
                    "CredWriteW failed ({:?}); falling back to in-memory store",
                    e
                );
                self.store.insert(db_key.into(), SecVec::new(data));
                Ok(())
            }
        }
    }

    fn get_key(&self, db_key: &str) -> Option<Vec<u8>> {
        // Check the fallback store first (used only if a prior write fell back).
        if let Some(v) = self.store.get(db_key) {
            return Some(Vec::from(v.unsecure()));
        }

        let target = formatted_key(db_key);
        match cred_read(&target) {
            Ok(hex_bytes) => match hex::decode(&hex_bytes) {
                Ok(v) => Some(v),
                Err(e) => {
                    error!("Hex decoding of credential blob failed: {}", e);
                    None
                }
            },
            Err(e) => {
                // Not found is expected before a db is opened.
                debug!("CredReadW returned no credential: {:?}", e);
                None
            }
        }
    }

    fn delete_key(&mut self, db_key: &str) -> kp_service::Result<()> {
        self.store.remove(db_key);

        let target = formatted_key(db_key);
        if let Err(e) = cred_delete(&target) {
            // Deleting a non-existent credential is not an error we surface.
            debug!("CredDeleteW failed (may not exist): {:?}", e);
        }
        Ok(())
    }

    fn copy_key(&mut self, source_db_key: &str, target_db_key: &str) -> kp_service::Result<()> {
        // Check the fallback store first.
        if let Some(v) = self.store.get(source_db_key).cloned() {
            self.store.insert(target_db_key.into(), v);
            debug!("Keys are copied from local store...");
            return Ok(());
        }

        let source = formatted_key(source_db_key);
        let target = formatted_key(target_db_key);

        let hex_bytes = cred_read(&source).map_err(|e| {
            onekeepass_core::error::Error::UnexpectedError(format!(
                "Copying key failed reading source credential: {:?}",
                e
            ))
        })?;

        // cred_read returns the hex-encoded blob; decode before cred_write re-encodes.
        let raw = hex::decode(&hex_bytes).map_err(|e| {
            onekeepass_core::error::Error::UnexpectedError(format!(
                "Copying key failed decoding source credential: {}",
                e
            ))
        })?;

        cred_write(&target, &raw).map_err(|e| {
            onekeepass_core::error::Error::UnexpectedError(format!(
                "Copying key failed writing target credential: {:?}",
                e
            ))
        })?;

        Ok(())
    }
}
