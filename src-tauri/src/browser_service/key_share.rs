use crypto_box::{
    aead::{Aead, AeadCore, OsRng},
    PublicKey, SalsaBox, SecretKey,
};

use data_encoding::BASE64;
use tokio::sync::mpsc;

use std::{
    collections::HashMap,
    sync::{Arc, OnceLock},
};

use onekeepass_core::error::{Error, Result};

pub(crate) type BrowserServiceTx = mpsc::Sender<String>;
pub(crate) type BrowserServiceRx = mpsc::Receiver<String>;

static SESSION_STORE: OnceLock<SessionStore> = OnceLock::new();

#[derive(Default)]
pub(crate) struct SessionStore {
    // One session for each browser (firefox or chrome or edge...) connection
    sessions: tokio::sync::Mutex<HashMap<String, Session>>,
}

impl SessionStore {
    fn shared() -> &'static SessionStore {
        SESSION_STORE.get_or_init(|| Default::default())
    }

    // Should be called first to create a new  Session
    pub(crate) async fn session_start(
        association_id: &str,
        sender: Arc<BrowserServiceTx>,
    ) -> Result<()> {
        let mut session = Session::default();
        session.set_sender(association_id, sender);
        let mut sessions = Self::shared().sessions.lock().await;
        sessions.insert(association_id.to_string(), session);

        Ok(())
    }

    // Called to send the response to the session specific channel
    pub(crate) async fn send_session_response(association_id: &str, message: &str) {
        let sessions = Self::shared().sessions.lock().await;
        if let Some(tx) = sessions
            .get(association_id)
            .and_then(|session| session.sender.as_ref())
        {
            let _r = tx.send(message.to_string()).await;
            // log::debug!("Sending response result is {:?}", &r);
        } else {
            log::error!("No session is found for the association_id {}",association_id);
        }
    }
 
    // Called to set up the crypto box for the session
    pub(crate) async fn init_session(
        association_id: &str,
        client_session_pub_key: &str,
    ) -> Result<String> {
        let mut sessions = Self::shared().sessions.lock().await;

        sessions.get_mut(association_id).map_or(
            Err(Error::DataError("Session is not available")),
            |session| session.init_session(client_session_pub_key),
        )
    }


    // Session specific message decryption
    #[allow(unused)]
    pub(crate) async fn decrypt(
        association_id: &str,
        encoded_nonce: &str,
        encoded_message: &str,
    ) -> Result<String> {
        let sessions = Self::shared().sessions.lock().await;
        sessions.get(association_id).map_or(
            Err(Error::DataError("Session is not available")),
            |session| session.decrypt(encoded_nonce, encoded_message),
        )
    }

    // Session specific message encryption
    pub(crate) async fn encrypt(association_id: &str, message: &str) -> Result<(String, String)> {
        let sessions = Self::shared().sessions.lock().await;
        sessions.get(association_id).map_or(
            Err(Error::DataError("Session is not available")),
            |session| session.encrypt(message),
        )
    }

    // Gets sender side of a channel for a session identified using association_id
    async fn _session_sender(association_id: &str) -> Result<Arc<BrowserServiceTx>> {
        let sessions = Self::shared().sessions.lock().await;
        sessions
            .get(association_id)
            .and_then(|session| session.sender.as_ref())
            .map_or(Err(Error::DataError("Session is not available")), |tx| {
                Ok(tx.clone())
            })
    }
}

#[derive(Default)]
struct Session {
    app_crypto_box: Option<SalsaBox>,
    association_id: String,
    sender: Option<Arc<BrowserServiceTx>>,
    // app_session_private_key: Option<SecretKey>,
    // app_session_pub_key: String,
    // client_session_pub_key: Option<PublicKey>,
}

impl Session {
    fn set_sender(&mut self, association_id: &str, sender: Arc<BrowserServiceTx>) {
        self.association_id = association_id.to_string();
        self.sender = Some(sender);
    }

    fn init_session(&mut self, client_session_pub_key: &str) -> Result<String> {
        let app_session_private_key = SecretKey::generate(&mut OsRng);
        let pub_key = app_session_private_key.public_key().as_bytes().clone();

        // self.app_session_private_key = Some(app_session_private_key);

        // self.client_session_pub_key = BASE64
        //     .decode(client_session_pub_key.as_bytes())
        //     .map_err(|_| "Base64 decoding of client public key failed ")?;

        // self.app_session_pub_key = BASE64.encode(&pub_key);

        let client_session_pub_key_bytes = BASE64
            .decode(client_session_pub_key.as_bytes())
            .map_err(|_| "Base64 decoding of client public key failed ")?;

        let client_session_pub_key = PublicKey::from_slice(client_session_pub_key_bytes.as_slice())
            .map_err(|_| "Error in creating client pub key")?;

        let app_crypto_box = SalsaBox::new(&client_session_pub_key, &app_session_private_key);

        self.app_crypto_box = Some(app_crypto_box);

        Ok(BASE64.encode(&pub_key))
    }

    fn encrypt(&self, message: &str) -> Result<(String, String)> {
        let Some(app_crypto_box) = self.app_crypto_box.as_ref() else {
            return Err(Error::DataError("App crypto box is not available"));
        };

        // Each encryption uses a nonce
        let nonce = SalsaBox::generate_nonce(&mut OsRng);

        let enc_data = app_crypto_box
            .encrypt(&nonce, message.as_bytes())
            .map_err(|_| "Error in encryption")?;

        Ok((BASE64.encode(&nonce), BASE64.encode(&enc_data)))
    }

    fn decrypt(&self, encoded_nonce: &str, encoded_message: &str) -> Result<String> {
        let Some(app_crypto_box) = self.app_crypto_box.as_ref() else {
            return Err(Error::DataError("App crypto box is not available"));
        };

        // Need to decode the base64 encoded data
        let nonce = BASE64
            .decode(encoded_nonce.as_bytes())
            .map_err(|_| "Base64 decoding of encoded_nonce failed ")?;

        // Need to decode the base64 encoded data
        let message_bytes = BASE64
            .decode(encoded_message.as_bytes())
            .map_err(|_| "Base64 decoding of encoded_message failed ")?;

        let dec_data = app_crypto_box
            .decrypt(nonce.as_slice().into(), message_bytes.as_slice())
            .map_err(|_| "Error in encryption")?;

        let msg = String::from_utf8(dec_data).unwrap();

        Ok(msg)
    }

    // pub(crate) fn app_session_pub_key(&self) -> Option<String> {
    //     self.app_session_private_key.as_ref().map(|sk| {
    //         let pub_key = sk.public_key().as_bytes().clone();
    //         BASE64.encode(&pub_key)
    //     })
    // }
}

#[cfg(test)]
mod tests {}
