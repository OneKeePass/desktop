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
            log::error!(
                "No session is found for the association_id {}",
                association_id
            );
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

    // Decrypts a request the extension encrypted with the session keys and checks
    // its replay-protection envelope {"seq": n, "msg": "..."}: seq must be greater
    // than the last one accepted in this session. Returns the inner msg.
    pub(crate) async fn decrypt_request(
        association_id: &str,
        encoded_nonce: &str,
        encoded_message: &str,
    ) -> Result<String> {
        let mut sessions = Self::shared().sessions.lock().await;
        sessions.get_mut(association_id).map_or(
            Err(Error::DataError("Session is not available")),
            |session| session.decrypt_request(encoded_nonce, encoded_message),
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
        let mut sessions = Self::shared().sessions.lock().await;
        sessions.get_mut(association_id).map_or(
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

    // Monotonically increasing counter embedded in every encrypted message
    // (inside the plaintext envelope) to prevent replay attacks.
    // Starts at 1 on the first encrypted message sent this session.
    send_counter: u64,

    // Last seq accepted from the extension's encrypted requests this session.
    // A request whose seq is not greater is a replay and is rejected.
    recv_counter: u64,
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
        // The extension starts its send counter again with each new session key
        self.recv_counter = 0;

        Ok(BASE64.encode(&pub_key))
    }

    fn encrypt(&mut self, message: &str) -> Result<(String, String)> {
        let Some(app_crypto_box) = self.app_crypto_box.as_ref() else {
            return Err(Error::DataError("App crypto box is not available"));
        };

        // Increment per-session counter and embed it in the plaintext before
        // encrypting. The extension validates that each received seq is strictly
        // greater than the previous one, preventing replay attacks.
        self.send_counter += 1;
        let envelope = serde_json::json!({
            "seq": self.send_counter,
            "msg": message,
        })
        .to_string();

        // Each encryption uses a fresh random nonce
        let nonce = SalsaBox::generate_nonce(&mut OsRng);

        let enc_data = app_crypto_box
            .encrypt(&nonce, envelope.as_bytes())
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

        let msg = String::from_utf8(dec_data)
            .map_err(|_| Error::DataError("Decrypted message is not valid UTF-8"))?;

        Ok(msg)
    }

    fn decrypt_request(&mut self, encoded_nonce: &str, encoded_message: &str) -> Result<String> {
        let plain = self.decrypt(encoded_nonce, encoded_message)?;
        let envelope: serde_json::Value = serde_json::from_str(&plain)
            .map_err(|_| Error::DataError("Invalid encrypted request envelope"))?;
        let seq = envelope
            .get("seq")
            .and_then(|v| v.as_u64())
            .ok_or(Error::DataError("Invalid encrypted request envelope"))?;
        if seq <= self.recv_counter {
            log::warn!("Encrypted request rejected: seq {} <= last {}", seq, self.recv_counter);
            return Err(Error::DataError("REPLAYED_REQUEST"));
        }
        let msg = envelope
            .get("msg")
            .and_then(|v| v.as_str())
            .ok_or(Error::DataError("Invalid encrypted request envelope"))?
            .to_string();
        self.recv_counter = seq;
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
mod tests {
    use super::*;

    // The extension side of the session: its key pair and a box for the app's key
    fn client_for(session: &mut Session) -> SalsaBox {
        let client_secret = SecretKey::generate(&mut OsRng);
        let client_pub = BASE64.encode(client_secret.public_key().as_bytes());
        let app_pub_b64 = session.init_session(&client_pub).unwrap();
        let app_pub_bytes = BASE64.decode(app_pub_b64.as_bytes()).unwrap();
        let app_pub = PublicKey::from_slice(&app_pub_bytes).unwrap();
        SalsaBox::new(&app_pub, &client_secret)
    }

    // Encrypts the way the extension does (background/crypto.cljs encrypt)
    fn client_encrypt(client: &SalsaBox, seq: u64, msg: &str) -> (String, String) {
        let envelope = serde_json::json!({ "seq": seq, "msg": msg }).to_string();
        let nonce = SalsaBox::generate_nonce(&mut OsRng);
        let enc = client.encrypt(&nonce, envelope.as_bytes()).unwrap();
        (BASE64.encode(&nonce), BASE64.encode(&enc))
    }

    #[test]
    fn decrypts_requests_and_rejects_replays() {
        let mut session = Session::default();
        let client = client_for(&mut session);

        let (n1, m1) = client_encrypt(&client, 1, r#"{"password":"s3cret"}"#);
        assert_eq!(session.decrypt_request(&n1, &m1).unwrap(), r#"{"password":"s3cret"}"#);

        // The same message again is a replay
        assert!(session.decrypt_request(&n1, &m1).is_err());

        // A later message is accepted, an older sequence number is not
        let (n3, m3) = client_encrypt(&client, 3, "three");
        assert_eq!(session.decrypt_request(&n3, &m3).unwrap(), "three");
        let (n2, m2) = client_encrypt(&client, 2, "two");
        assert!(session.decrypt_request(&n2, &m2).is_err());
    }

    #[test]
    fn rejects_tampered_or_foreign_requests() {
        let mut session = Session::default();
        let client = client_for(&mut session);
        let (nonce, msg) = client_encrypt(&client, 1, "hello");

        // Flipped ciphertext byte
        let mut bytes = BASE64.decode(msg.as_bytes()).unwrap();
        bytes[0] ^= 1;
        assert!(session.decrypt_request(&nonce, &BASE64.encode(&bytes)).is_err());

        // Encrypted for another session's key
        let mut other = Session::default();
        let other_client = client_for(&mut other);
        let (n, m) = client_encrypt(&other_client, 1, "hello");
        assert!(session.decrypt_request(&n, &m).is_err());

        // Not an envelope
        let nonce = SalsaBox::generate_nonce(&mut OsRng);
        let enc = client.encrypt(&nonce, b"not json".as_slice()).unwrap();
        assert!(session
            .decrypt_request(&BASE64.encode(&nonce), &BASE64.encode(&enc))
            .is_err());
    }

    // A request encrypted by the extension's tweetnacl `nacl.box` (fixed keys and
    // nonce: client secret = 32 x 7, app secret = 32 x 9, nonce = 24 x 3) opens
    // with this crate's SalsaBox: both are NaCl crypto_box.
    #[test]
    fn opens_a_box_made_by_the_extension_tweetnacl() {
        let app_secret = SecretKey::from_slice(&[9u8; 32]).unwrap();
        let client_pub_bytes = BASE64
            .decode(b"E75P6uryBMf9M1j8nAByGIHRdCeBKCJ+xnTzf3/pe20=")
            .unwrap();
        let client_pub = PublicKey::from_slice(&client_pub_bytes).unwrap();
        let mut session = Session::default();
        session.app_crypto_box = Some(SalsaBox::new(&client_pub, &app_secret));

        let msg = session
            .decrypt_request(
                &BASE64.encode(&[3u8; 24]),
                "n8cM6Q3fMraFHBO75k+w2wVqCmOes3phxBFi5+lXSfvV7UF0Licj4KvQNilKPKl5ecoHVvyO6u2u0XE=",
            )
            .unwrap();
        assert_eq!(msg, r#"{"password":"s3cret"}"#);
    }

    #[test]
    fn new_session_key_restarts_the_sequence() {
        let mut session = Session::default();
        let client = client_for(&mut session);
        let (n, m) = client_encrypt(&client, 5, "five");
        session.decrypt_request(&n, &m).unwrap();

        // The extension reconnects with a new key and starts again at 1
        let client = client_for(&mut session);
        let (n, m) = client_encrypt(&client, 1, "one");
        assert_eq!(session.decrypt_request(&n, &m).unwrap(), "one");
    }
}
