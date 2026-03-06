//! Pure cryptographic operations for WebAuthn passkey registration and authentication.
//!
//! All private-key material stays inside this module (and the KDBX database).
//! No sensitive data is passed back to the browser extension in plaintext.

use data_encoding::BASE64URL_NOPAD;
use p256::{
    ecdsa::{signature::Signer, Signature, SigningKey},
    pkcs8::{DecodePrivateKey, EncodePrivateKey, LineEnding},
    EncodedPoint,
};
use rand_core::{OsRng, RngCore};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use onekeepass_core::error::{Error, Result};

// ── Option structs (parsed subsets of the W3C JSON shapes) ──────────────────

#[derive(Debug, Deserialize)]
struct RpInfo {
    id: String,
    name: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct UserInfo {
    id: String, // base64url-encoded bytes
    name: String,
    display_name: Option<String>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CreationOptions {
    rp: RpInfo,
    user: UserInfo,
    challenge: String, // base64url
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AllowCredential {
    id: String, // base64url
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RequestOptions {
    rp_id: Option<String>,
    challenge: String, // base64url
    #[serde(default)]
    allow_credentials: Vec<AllowCredential>,
}

// ── Result structs ────────────────────────────────────────────────────────────

/// Everything the caller needs to (a) return a credential to the website and
/// (b) persist the key material in KDBX.
#[derive(Debug)]
pub struct PasskeyCreationResult {
    /// JSON to return to the browser/site via `completeCreateRequest`.
    pub credential_json: String,

    // ── fields to store in KDBX ──────────────────────────────────────────
    pub credential_id_b64url: String,
    /// PKCS#8 PEM private key — must be stored as a KDBX *protected* string.
    pub private_key_pem: String,
    pub rp_id: String,
    pub rp_name: String,
    pub username: String,
    pub user_handle_b64url: String,
    pub origin: String,
}

/// The signed WebAuthn assertion JSON, ready to resolve the site's Promise.
#[derive(Debug)]
pub struct PasskeyAssertionResult {
    pub credential_json: String,
}

// ── authData builders ─────────────────────────────────────────────────────────

/// Builds the authenticator data blob for a *registration* ceremony.
///
/// Layout (per WebAuthn §6.1):
/// ```text
/// rpIdHash      (32 bytes)
/// flags         (1 byte)   UP=1, UV=1, AT=1 → 0x45
/// signCount     (4 bytes)  big-endian, 0 for software authenticators
/// aaguid        (16 bytes) all zeros (no attestation GUID)
/// credIdLen     (2 bytes)  big-endian
/// credId        (N bytes)
/// credPublicKey (CBOR)     COSE EC2 P-256 key
/// ```
fn build_auth_data_create(
    rp_id: &str,
    credential_id: &[u8],
    cose_public_key: &[u8],
) -> Vec<u8> {
    let mut buf = Vec::with_capacity(32 + 1 + 4 + 16 + 2 + credential_id.len() + cose_public_key.len());

    buf.extend_from_slice(&Sha256::digest(rp_id.as_bytes()));   // rpIdHash
    buf.push(0x45);                                              // flags: UP|UV|AT
    buf.extend_from_slice(&0u32.to_be_bytes());                  // signCount = 0
    buf.extend_from_slice(&[0u8; 16]);                           // aaguid = zeros
    buf.extend_from_slice(&(credential_id.len() as u16).to_be_bytes());
    buf.extend_from_slice(credential_id);
    buf.extend_from_slice(cose_public_key);
    buf
}

/// Builds the authenticator data blob for an *authentication* (assertion) ceremony.
///
/// Layout:
/// ```text
/// rpIdHash  (32 bytes)
/// flags     (1 byte)   UP=1, UV=1 → 0x05
/// signCount (4 bytes)  big-endian
/// ```
fn build_auth_data_get(rp_id: &str, sign_count: u32) -> Vec<u8> {
    let mut buf = Vec::with_capacity(37);
    buf.extend_from_slice(&Sha256::digest(rp_id.as_bytes()));
    buf.push(0x05); // UP|UV
    buf.extend_from_slice(&sign_count.to_be_bytes());
    buf
}

// ── CBOR helpers ──────────────────────────────────────────────────────────────

/// Encodes a P-256 public key as a COSE_Key map (RFC 8152 §13.1.1).
///
/// ```text
/// {
///   1:  2,   // kty: EC2
///   3:  -7,  // alg: ES256
///   -1: 1,   // crv: P-256
///   -2: x,   // x coordinate (32 bytes)
///   -3: y,   // y coordinate (32 bytes)
/// }
/// ```
fn encode_cose_key(verifying_key: &p256::ecdsa::VerifyingKey) -> Result<Vec<u8>> {
    use ciborium::value::Value;

    let point: EncodedPoint = verifying_key.to_encoded_point(false /* uncompressed */);
    let x = point
        .x()
        .ok_or_else(|| Error::UnexpectedError("P-256 x coordinate missing".into()))?
        .to_vec();
    let y = point
        .y()
        .ok_or_else(|| Error::UnexpectedError("P-256 y coordinate missing".into()))?
        .to_vec();

    let cose_key = Value::Map(vec![
        (Value::Integer(1i64.into()), Value::Integer(2i64.into())),   // kty: EC2
        (Value::Integer(3i64.into()), Value::Integer((-7i64).into())), // alg: ES256
        (Value::Integer((-1i64).into()), Value::Integer(1i64.into())), // crv: P-256
        (Value::Integer((-2i64).into()), Value::Bytes(x)),             // x
        (Value::Integer((-3i64).into()), Value::Bytes(y)),             // y
    ]);

    let mut buf = Vec::new();
    ciborium::ser::into_writer(&cose_key, &mut buf)
        .map_err(|e| Error::UnexpectedError(format!("COSE key CBOR encoding failed: {}", e)))?;
    Ok(buf)
}

/// Encodes the attestation object (CBOR map with `fmt`, `attStmt`, `authData`).
fn encode_attestation_object(auth_data: Vec<u8>) -> Result<Vec<u8>> {
    use ciborium::value::Value;

    let att_obj = Value::Map(vec![
        (Value::Text("fmt".into()), Value::Text("none".into())),
        (Value::Text("attStmt".into()), Value::Map(vec![])),
        (Value::Text("authData".into()), Value::Bytes(auth_data)),
    ]);

    let mut buf = Vec::new();
    ciborium::ser::into_writer(&att_obj, &mut buf).map_err(|e| {
        Error::UnexpectedError(format!("attestationObject CBOR encoding failed: {}", e))
    })?;
    Ok(buf)
}

// ── Public API ────────────────────────────────────────────────────────────────

/// Performs a WebAuthn registration ceremony for the given creation options.
///
/// Generates a P-256 key pair, builds all required WebAuthn structures, and
/// returns both the credential JSON (for the site) and the key material (for
/// KDBX storage).
pub fn create_passkey(options_json: &str, origin: &str) -> Result<PasskeyCreationResult> {
    // ── Parse creation options ───────────────────────────────────────────────
    let opts: CreationOptions = serde_json::from_str(options_json)
        .map_err(|e| Error::UnexpectedError(format!("Invalid creation options JSON: {}", e)))?;

    let rp_id = opts.rp.id.clone();
    let rp_name = opts.rp.name.unwrap_or_else(|| rp_id.clone());
    let username = opts.user.name.clone();
    let user_handle_b64url = opts.user.id.clone();
    let challenge_bytes = BASE64URL_NOPAD
        .decode(opts.challenge.as_bytes())
        .map_err(|e| Error::UnexpectedError(format!("Invalid challenge encoding: {}", e)))?;

    // ── Generate key pair and credential ID ──────────────────────────────────
    let signing_key = SigningKey::random(&mut OsRng);

    let mut cred_id_bytes = [0u8; 16];
    OsRng.fill_bytes(&mut cred_id_bytes);
    let credential_id_b64url = BASE64URL_NOPAD.encode(&cred_id_bytes);

    // ── Build COSE public key and authData ───────────────────────────────────
    let cose_key_bytes = encode_cose_key(signing_key.verifying_key())?;
    let auth_data = build_auth_data_create(&rp_id, &cred_id_bytes, &cose_key_bytes);

    // ── Build attestationObject (CBOR, "none" format) ────────────────────────
    let auth_data_b64url = BASE64URL_NOPAD.encode(&auth_data);
    let attestation_object = encode_attestation_object(auth_data)?;

    // ── Build clientDataJSON ─────────────────────────────────────────────────
    let client_data = serde_json::json!({
        "type": "webauthn.create",
        "challenge": BASE64URL_NOPAD.encode(&challenge_bytes),
        "origin": origin,
        "crossOrigin": false,
    });
    let client_data_json = serde_json::to_string(&client_data)
        .map_err(|e| Error::UnexpectedError(format!("clientDataJSON serialization failed: {}", e)))?;

    // ── Encode private key as PKCS#8 PEM ────────────────────────────────────
    let pem = signing_key
        .to_pkcs8_pem(LineEnding::LF)
        .map_err(|e| Error::UnexpectedError(format!("PEM encoding failed: {}", e)))?;
    let private_key_pem = pem.as_str().to_string();

    // ── Encode public key as SPKI DER (base64url) ────────────────────────────
    // Fixed 26-byte DER prefix for P-256 SubjectPublicKeyInfo, followed by the
    // uncompressed EC point (04 || x || y, 65 bytes) = 91 bytes total.
    let encoded_point = signing_key.verifying_key().to_encoded_point(false);
    const P256_SPKI_PREFIX: &[u8] = &[
        0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x02, 0x01,
        0x06, 0x08, 0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
    ];
    let mut public_key_spki = Vec::with_capacity(91);
    public_key_spki.extend_from_slice(P256_SPKI_PREFIX);
    public_key_spki.extend_from_slice(encoded_point.as_bytes());
    let public_key_b64url = BASE64URL_NOPAD.encode(&public_key_spki);

    // ── Build PublicKeyCredential JSON for the site ──────────────────────────
    let credential_json = serde_json::to_string(&serde_json::json!({
        "id":   credential_id_b64url,
        "rawId": credential_id_b64url,
        "type": "public-key",
        "authenticatorAttachment": "platform",
        "clientExtensionResults": {},
        "response": {
            "attestationObject":  BASE64URL_NOPAD.encode(&attestation_object),
            "authenticatorData":  auth_data_b64url,
            "clientDataJSON":     BASE64URL_NOPAD.encode(client_data_json.as_bytes()),
            "publicKey":          public_key_b64url,
            "publicKeyAlgorithm": -7_i64,  // COSE ES256 (P-256)
            "transports":         ["internal"],
        },
    }))
    .map_err(|e| Error::UnexpectedError(format!("Credential JSON serialization failed: {}", e)))?;

    Ok(PasskeyCreationResult {
        credential_json,
        credential_id_b64url,
        private_key_pem,
        rp_id,
        rp_name,
        username,
        user_handle_b64url,
        origin: origin.to_string(),
    })
}

/// Performs a WebAuthn authentication (assertion) ceremony.
///
/// Loads the private key from `private_key_pem`, signs the assertion, and
/// returns the `PublicKeyCredential` JSON the extension passes back to the site.
pub fn sign_assertion(
    credential_id_b64url: &str,
    rp_id: &str,
    user_handle_b64url: &str,
    private_key_pem: &str,
    options_json: &str,
    origin: &str,
) -> Result<PasskeyAssertionResult> {
    // ── Parse request options ────────────────────────────────────────────────
    let opts: RequestOptions = serde_json::from_str(options_json)
        .map_err(|e| Error::UnexpectedError(format!("Invalid request options JSON: {}", e)))?;

    let effective_rp_id = opts.rp_id.as_deref().unwrap_or(rp_id);
    let challenge_bytes = BASE64URL_NOPAD
        .decode(opts.challenge.as_bytes())
        .map_err(|e| Error::UnexpectedError(format!("Invalid challenge encoding: {}", e)))?;

    // ── Build clientDataJSON ─────────────────────────────────────────────────
    let client_data = serde_json::json!({
        "type": "webauthn.get",
        "challenge": BASE64URL_NOPAD.encode(&challenge_bytes),
        "origin": origin,
        "crossOrigin": false,
    });
    let client_data_json = serde_json::to_string(&client_data)
        .map_err(|e| Error::UnexpectedError(format!("clientDataJSON serialization failed: {}", e)))?;

    // ── Build authenticatorData ──────────────────────────────────────────────
    // sign_count = 0: software authenticators that don't track a counter use 0,
    // which is allowed per WebAuthn §6.1.1 (servers SHOULD check but it is optional).
    let auth_data = build_auth_data_get(effective_rp_id, 0u32);

    // ── Sign (ECDSA-P256 over authData || SHA-256(clientDataJSON)) ───────────
    let signing_key = SigningKey::from_pkcs8_pem(private_key_pem)
        .map_err(|e| Error::UnexpectedError(format!("Failed to decode private key PEM: {}", e)))?;

    let client_data_hash = Sha256::digest(client_data_json.as_bytes());
    let mut sig_input = Vec::with_capacity(auth_data.len() + 32);
    sig_input.extend_from_slice(&auth_data);
    sig_input.extend_from_slice(&client_data_hash);

    // `Signer::sign` hashes the message with SHA-256 internally (ES256).
    let signature: Signature = signing_key.sign(&sig_input);
    // WebAuthn requires DER-encoded signature.
    let sig_der = signature.to_der();

    // ── Build PublicKeyCredential JSON for the site ──────────────────────────
    let credential_json = serde_json::to_string(&serde_json::json!({
        "id":   credential_id_b64url,
        "rawId": credential_id_b64url,
        "type": "public-key",
        "authenticatorAttachment": "platform",
        "clientExtensionResults": {},
        "response": {
            "authenticatorData": BASE64URL_NOPAD.encode(&auth_data),
            "clientDataJSON":    BASE64URL_NOPAD.encode(client_data_json.as_bytes()),
            "signature":         BASE64URL_NOPAD.encode(sig_der.as_bytes()),
            "userHandle":        user_handle_b64url,
        },
    }))
    .map_err(|e| Error::UnexpectedError(format!("Credential JSON serialization failed: {}", e)))?;

    Ok(PasskeyAssertionResult { credential_json })
}

// ── Unit tests ────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_creation_options(rp_id: &str) -> String {
        use data_encoding::BASE64URL_NOPAD;
        let challenge = BASE64URL_NOPAD.encode(b"test-challenge-1234");
        let user_id = BASE64URL_NOPAD.encode(b"user-id-bytes");
        serde_json::json!({
            "rp": { "id": rp_id, "name": "Test Site" },
            "user": {
                "id": user_id,
                "name": "alice@example.com",
                "displayName": "Alice"
            },
            "challenge": challenge,
            "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
        })
        .to_string()
    }

    fn sample_request_options(rp_id: &str, cred_id: &str) -> String {
        use data_encoding::BASE64URL_NOPAD;
        let challenge = BASE64URL_NOPAD.encode(b"auth-challenge-5678");
        serde_json::json!({
            "rpId": rp_id,
            "challenge": challenge,
            "allowCredentials": [{ "type": "public-key", "id": cred_id }],
            "userVerification": "preferred",
        })
        .to_string()
    }

    #[test]
    fn create_and_sign_roundtrip() {
        let rp_id = "example.com";
        let origin = "https://example.com";

        let creation_opts = sample_creation_options(rp_id);
        let result = create_passkey(&creation_opts, origin)
            .expect("passkey creation should succeed");

        assert!(!result.credential_json.is_empty());
        assert!(!result.private_key_pem.is_empty());
        assert_eq!(result.rp_id, rp_id);
        assert_eq!(result.username, "alice@example.com");

        // The credential JSON must be valid JSON
        let cred: serde_json::Value = serde_json::from_str(&result.credential_json)
            .expect("credential_json must be valid JSON");
        assert_eq!(cred["type"], "public-key");
        assert!(cred["response"]["attestationObject"].is_string());
        assert!(cred["response"]["clientDataJSON"].is_string());

        // Now sign an assertion using the key we just created
        let request_opts = sample_request_options(rp_id, &result.credential_id_b64url);
        let assertion = sign_assertion(
            &result.credential_id_b64url,
            rp_id,
            &result.user_handle_b64url,
            &result.private_key_pem,
            &request_opts,
            origin,
        )
        .expect("assertion signing should succeed");

        let asr: serde_json::Value = serde_json::from_str(&assertion.credential_json)
            .expect("assertion credential_json must be valid JSON");
        assert_eq!(asr["type"], "public-key");
        assert!(asr["response"]["signature"].is_string());
        assert!(asr["response"]["authenticatorData"].is_string());
    }

    #[test]
    fn create_passkey_produces_unique_credential_ids() {
        let opts = sample_creation_options("test.com");
        let r1 = create_passkey(&opts, "https://test.com").unwrap();
        let r2 = create_passkey(&opts, "https://test.com").unwrap();
        assert_ne!(r1.credential_id_b64url, r2.credential_id_b64url);
        assert_ne!(r1.private_key_pem, r2.private_key_pem);
    }

    #[test]
    fn invalid_options_json_returns_error() {
        let result = create_passkey("not valid json", "https://example.com");
        assert!(result.is_err());
    }

    #[test]
    fn sign_assertion_with_wrong_pem_returns_error() {
        let request_opts = sample_request_options("example.com", "some-cred-id");
        let result = sign_assertion(
            "some-cred-id",
            "example.com",
            "dXNlcg",
            "NOT A VALID PEM",
            &request_opts,
            "https://example.com",
        );
        assert!(result.is_err(), "invalid PEM should return an error");
    }

    #[test]
    fn sign_assertion_without_rp_id_in_options_falls_back_to_arg() {
        // options_json has no rpId field — the function should fall back to the
        // `rp_id` parameter passed directly.
        let creation_opts = sample_creation_options("fallback.com");
        let creation = create_passkey(&creation_opts, "https://fallback.com").unwrap();

        let opts_without_rp_id = {
            use data_encoding::BASE64URL_NOPAD;
            let challenge = BASE64URL_NOPAD.encode(b"challenge-no-rpid");
            serde_json::json!({
                // note: no "rpId" key
                "challenge": challenge,
                "allowCredentials": [],
            })
            .to_string()
        };

        let assertion = sign_assertion(
            &creation.credential_id_b64url,
            "fallback.com",
            &creation.user_handle_b64url,
            &creation.private_key_pem,
            &opts_without_rp_id,
            "https://fallback.com",
        );
        assert!(
            assertion.is_ok(),
            "should succeed using fallback rp_id: {:?}",
            assertion
        );
    }

    #[test]
    fn auth_data_create_has_correct_length_and_flags() {
        use sha2::{Digest, Sha256};

        let rp_id = "len-test.com";
        let cred_id = b"test-credential-id-16b";
        // minimal COSE key placeholder
        let cose_key = b"COSE_KEY_PLACEHOLDER";

        let auth_data = build_auth_data_create(rp_id, cred_id, cose_key);

        // rpIdHash(32) + flags(1) + signCount(4) + aaguid(16) + credIdLen(2) + credId + coseKey
        let expected_len = 32 + 1 + 4 + 16 + 2 + cred_id.len() + cose_key.len();
        assert_eq!(auth_data.len(), expected_len);

        // rpIdHash matches SHA-256 of rp_id
        let expected_hash: Vec<u8> = Sha256::digest(rp_id.as_bytes()).to_vec();
        assert_eq!(&auth_data[..32], expected_hash.as_slice());

        // flags byte = 0x45 (UP|UV|AT)
        assert_eq!(auth_data[32], 0x45);

        // signCount = 0 (4 bytes big-endian at offset 33)
        assert_eq!(&auth_data[33..37], &[0u8; 4]);
    }

    #[test]
    fn auth_data_get_has_correct_length_and_flags() {
        use sha2::{Digest, Sha256};

        let rp_id = "get-test.com";
        let sign_count = 42u32;
        let auth_data = build_auth_data_get(rp_id, sign_count);

        // rpIdHash(32) + flags(1) + signCount(4)
        assert_eq!(auth_data.len(), 37);

        let expected_hash: Vec<u8> = Sha256::digest(rp_id.as_bytes()).to_vec();
        assert_eq!(&auth_data[..32], expected_hash.as_slice());

        // flags byte = 0x05 (UP|UV, no AT)
        assert_eq!(auth_data[32], 0x05);

        // signCount encoded big-endian
        let count_bytes = sign_count.to_be_bytes();
        assert_eq!(&auth_data[33..37], &count_bytes);
    }
}
