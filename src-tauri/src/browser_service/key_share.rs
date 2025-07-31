use crypto_box::{
    aead::{Aead, AeadCore, OsRng},
    PublicKey, SalsaBox, SecretKey,
};

use data_encoding::BASE64;

use std::{
    collections::HashMap, sync::{Arc, Mutex, OnceLock}
};

// static SESSION_STORE: OnceLock<Arc<SessionStore>> = OnceLock::new();

static SESSION_STORE: OnceLock<SessionStore> = OnceLock::new();

#[derive(Default)]
pub struct SessionStore {
    sessions: Mutex<HashMap<String, Session>>,
}

impl SessionStore {
    fn shared() -> &'static SessionStore {
        SESSION_STORE.get_or_init(|| Default::default())
    }
}

#[derive(Default)]
pub(crate) struct Session {
    
    app_crypto_box: Option<SalsaBox>,
    association_id: String,

    // app_session_private_key: Option<SecretKey>,
    // app_session_pub_key: String,
    // client_session_pub_key: Option<PublicKey>,
}

impl Session {
    pub(crate) fn init_session(
        &mut self,
        association_id: &str,
        client_session_pub_key: &str,
    ) -> Result<String, &'static str> {
        let app_session_private_key = SecretKey::generate(&mut OsRng);

        let pub_key = app_session_private_key.public_key().as_bytes().clone();

        // self.app_session_private_key = Some(app_session_private_key);

        self.association_id = association_id.to_string();

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

    pub(crate) fn encrypt(&self, message: &str) -> Result<(String, String), &'static str> {
        let Some(app_crypto_box) = self.app_crypto_box.as_ref() else {
            return Err("App crypto box is not available");
        };

        // Each encryption uses a nonce
        let nonce = SalsaBox::generate_nonce(&mut OsRng);

        let enc_data = app_crypto_box
            .encrypt(&nonce, message.as_bytes())
            .map_err(|_| "Error in encryption")?;

        Ok((BASE64.encode(&nonce),BASE64.encode(&enc_data)))
    }

    pub(crate) fn decrypt(
        &self,
        encoded_nonce: &str,
        encoded_message: &str,
    ) -> Result<String, &'static str> {
        let Some(app_crypto_box) = self.app_crypto_box.as_ref() else {
            return Err("App crypto box is not available");
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

fn test1() {
    //
    // Encryption
    //

    // Generate a random secret key.
    // NOTE: The secret key bytes can be accessed by calling `secret_key.as_bytes()`
    let alice_secret_key = SecretKey::generate(&mut OsRng);

    // Get the public key for the secret key we just generated
    let alice_public_key_bytes = alice_secret_key.public_key().as_bytes().clone();

    // Obtain your recipient's public key.
    let bob_public_key = PublicKey::from([
        0xe8, 0x98, 0xc, 0x86, 0xe0, 0x32, 0xf1, 0xeb, 0x29, 0x75, 0x5, 0x2e, 0x8d, 0x65, 0xbd,
        0xdd, 0x15, 0xc3, 0xb5, 0x96, 0x41, 0x17, 0x4e, 0xc9, 0x67, 0x8a, 0x53, 0x78, 0x9d, 0x92,
        0xc7, 0x54,
    ]);

    // Create a `SalsaBox` by performing Diffie-Hellman key agreement between
    // the two keys.
    let alice_box = SalsaBox::new(&bob_public_key, &alice_secret_key);

    // Get a random nonce to encrypt the message under
    let nonce = SalsaBox::generate_nonce(&mut OsRng);

    // Message to encrypt
    let plaintext = b"Top secret message we're encrypting";

    // Encrypt the message using the box
    let ciphertext = alice_box.encrypt(&nonce, &plaintext[..]).unwrap();

    //
    // Decryption
    //

    // Either side can encrypt or decrypt messages under the Diffie-Hellman key
    // they agree upon. The example below shows Bob's side.
    let bob_secret_key = SecretKey::from([
        0xb5, 0x81, 0xfb, 0x5a, 0xe1, 0x82, 0xa1, 0x6f, 0x60, 0x3f, 0x39, 0x27, 0xd, 0x4e, 0x3b,
        0x95, 0xbc, 0x0, 0x83, 0x10, 0xb7, 0x27, 0xa1, 0x1d, 0xd4, 0xe7, 0x84, 0xa0, 0x4, 0x4d,
        0x46, 0x1b,
    ]);

    // Deserialize Alice's public key from bytes
    let alice_public_key = PublicKey::from(alice_public_key_bytes);

    // Bob can compute the same `SalsaBox` as Alice by performing the
    // key agreement operation.
    let bob_box = SalsaBox::new(&alice_public_key, &bob_secret_key);

    // Decrypt the message, using the same randomly generated nonce
    let decrypted_plaintext = bob_box.decrypt(&nonce, &ciphertext[..]).unwrap();

    assert_eq!(&plaintext[..], &decrypted_plaintext[..]);
}

#[cfg(test)]
mod tests {
    #[test]
    fn verify1() {
        super::test1();
    }
}
