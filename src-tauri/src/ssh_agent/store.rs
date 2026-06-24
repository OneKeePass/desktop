// In-memory store of decrypted SSH keys that the agent advertises and signs
// with. Each `StoredIdentity` holds a decoded `ssh_key::PrivateKey` (the secret
// material). ssh-key zeroizes that material when the value is dropped, so
// removing a db's slice or clearing the store wipes the keys from memory.
//
// This module is transport-neutral: the unix-socket `Session` adapter (and, in
// later phases, the Windows named-pipe / Pageant adapters) all call the same
// `identities()` / `sign()` entry points here.

use std::collections::HashMap;

use ssh_agent_lib::error::AgentError;
use ssh_agent_lib::proto::{Identity, PublicCredential};

// ssh-key 0.6.7, the same version ssh-agent-lib re-exports (see Cargo.toml).
use ssh_key::public::KeyData;
use ssh_key::{Algorithm, HashAlg, PrivateKey, PublicKey, Signature};

use onekeepass_core::db_service::ssh_agent::SshAgentKeySource;

// SIGN_REQUEST flag bits from the agent protocol (draft-miller-ssh-agent).
const SSH_AGENT_RSA_SHA2_256: u32 = 0x02;
const SSH_AGENT_RSA_SHA2_512: u32 = 0x04;

// One agent identity: a decoded private key plus the metadata the agent needs.
// Deliberately not `Debug`/`Serialize` — it carries secret key material.
struct StoredIdentity {
    // Owning database; used to remove this identity when its db is locked/closed.
    db_key: String,
    // The agent "comment" advertised to clients (the key entry title).
    comment: String,
    // Decoded + decrypted private key. Zeroized on drop by ssh-key.
    key: PrivateKey,
    // Public half, derived from `key`. The advertised identity and the value a
    // sign request is matched against. Never the entry's stored "Public Key".
    public_key_data: KeyData,
    // When true, each sign request for this key must be user-confirmed.
    require_confirmation: bool,
}

// Metadata the Session needs to decide whether (and how) to prompt before
// signing. Cloned out from under the store lock so the async confirmation wait
// holds no lock.
pub(crate) struct ConfirmInfo {
    pub require_confirmation: bool,
    pub comment: String,
    pub fingerprint: String,
}

impl StoredIdentity {
    // Decodes one key source into a signable identity. Returns a human-readable
    // error (logged by the caller) when the key can't be parsed/decrypted or
    // when the stored public key contradicts the private key.
    fn from_source(src: &SshAgentKeySource) -> Result<Self, String> {
        let pem = normalize_openssh_pem(&src.private_key_pem);
        let parsed = PrivateKey::from_openssh(pem.trim())
            .map_err(|e| format!("private key parse failed: {e}"))?;

        let key = if parsed.is_encrypted() {
            let pass = src
                .passphrase
                .as_deref()
                .filter(|p| !p.is_empty())
                .ok_or_else(|| "key is encrypted but no passphrase is set".to_string())?;
            parsed
                .decrypt(pass)
                .map_err(|e| format!("private key decrypt failed: {e}"))?
        } else {
            parsed
        };

        let public_key_data = key.public_key().key_data().clone();

        // If the entry also stores a "Public Key", it must match the key we
        // derived. We never advertise one public key while signing with a
        // different private key.
        if let Some(stored_pub) = src.public_key.as_deref().map(str::trim) {
            if !stored_pub.is_empty() {
                if let Ok(parsed_pub) = PublicKey::from_openssh(stored_pub) {
                    if parsed_pub.key_data() != &public_key_data {
                        return Err(
                            "stored Public Key does not match the Private Key; skipping".into(),
                        );
                    }
                }
            }
        }

        // Prefer the entry title as the comment; fall back to any comment baked
        // into the OpenSSH key.
        let comment = if src.title.trim().is_empty() {
            key.comment().to_string()
        } else {
            src.title.clone()
        };

        Ok(Self {
            db_key: src.db_key.clone(),
            comment,
            key,
            public_key_data,
            require_confirmation: src.require_confirmation,
        })
    }

    fn fingerprint(&self) -> String {
        self.public_key_data.fingerprint(HashAlg::Sha256).to_string()
    }

    fn sign(&self, data: &[u8], flags: u32) -> Result<Signature, String> {
        match self.key.algorithm() {
            // ssh-key 0.6.7's high-level RSA signer is broken (it builds primes
            // as [p, p]); drive the rsa crate directly with the correct primes.
            Algorithm::Rsa { .. } => sign_rsa_with_flags(&self.key, data, flags),
            // Ed25519 / ECDSA: the algorithm is fixed by the key, flags are
            // irrelevant, and the Signer path is correct.
            _ => signature::Signer::try_sign(&self.key, data)
                .map_err(|e| format!("sign failed: {e}")),
        }
    }
}

// Repairs an OpenSSH private key whose PEM line structure was flattened — e.g.
// pasted into a text field that collapsed the newlines, leaving the
// `-----BEGIN/END-----` markers and the base64 body run together with spaces.
// ssh-key's parser requires the markers on their own lines and the body wrapped,
// so we strip all whitespace from the body and re-wrap it canonically. A key
// that is already well-formed round-trips through this unchanged. Inputs without
// the OpenSSH markers (e.g. PKCS#8) are returned untouched and left to fail in
// the parser as before.
fn normalize_openssh_pem(input: &str) -> String {
    const BEGIN: &str = "-----BEGIN OPENSSH PRIVATE KEY-----";
    const END: &str = "-----END OPENSSH PRIVATE KEY-----";

    let s = input.trim();
    let (Some(b), Some(e)) = (s.find(BEGIN), s.find(END)) else {
        return input.to_string();
    };
    if e <= b {
        return input.to_string();
    }

    let body = &s[b + BEGIN.len()..e];
    let b64: String = body.chars().filter(|c| !c.is_whitespace()).collect();

    let mut wrapped = String::with_capacity(b64.len() + b64.len() / 70 + 2);
    let mut i = 0;
    while i < b64.len() {
        let end = (i + 70).min(b64.len());
        wrapped.push_str(&b64[i..end]);
        wrapped.push('\n');
        i = end;
    }

    format!("{BEGIN}\n{wrapped}{END}\n")
}

// Produces an `ssh_key::Signature` honoring the agent's requested RSA hash flag.
// Defaults to SHA-256 when neither flag is set; never emits legacy SHA-1.
fn sign_rsa_with_flags(key: &PrivateKey, msg: &[u8], flags: u32) -> Result<Signature, String> {
    use rsa::pkcs1v15::SigningKey;
    use rsa::signature::{SignatureEncoding, Signer as _};
    use rsa::RsaPrivateKey;

    let keypair = match key.key_data() {
        ssh_key::private::KeypairData::Rsa(kp) => kp,
        _ => return Err("not an RSA key".into()),
    };

    let n = rsa::BigUint::try_from(&keypair.public.n).map_err(|e| format!("n: {e}"))?;
    let e = rsa::BigUint::try_from(&keypair.public.e).map_err(|e| format!("e: {e}"))?;
    let d = rsa::BigUint::try_from(&keypair.private.d).map_err(|e| format!("d: {e}"))?;
    let p = rsa::BigUint::try_from(&keypair.private.p).map_err(|e| format!("p: {e}"))?;
    let q = rsa::BigUint::try_from(&keypair.private.q).map_err(|e| format!("q: {e}"))?;
    let priv_key =
        RsaPrivateKey::from_components(n, e, d, vec![p, q]).map_err(|e| format!("to rsa: {e}"))?;

    let use_sha512 = flags & SSH_AGENT_RSA_SHA2_512 != 0;
    if use_sha512 {
        let sk = SigningKey::<sha2::Sha512>::new(priv_key);
        let sig = sk.sign(msg);
        Signature::new(
            Algorithm::Rsa {
                hash: Some(HashAlg::Sha512),
            },
            sig.to_vec(),
        )
        .map_err(|e| format!("wrap sha512: {e}"))
    } else {
        // SHA-256 flag set, or neither flag set (default).
        let _ = SSH_AGENT_RSA_SHA2_256;
        let sk = SigningKey::<sha2::Sha256>::new(priv_key);
        let sig = sk.sign(msg);
        Signature::new(
            Algorithm::Rsa {
                hash: Some(HashAlg::Sha256),
            },
            sig.to_vec(),
        )
        .map_err(|e| format!("wrap sha256: {e}"))
    }
}

// The set of identities currently served by the agent, ordered for stable
// listing and de-duplicated by public-key fingerprint when advertised.
pub(crate) struct SshAgentStore {
    identities: Vec<StoredIdentity>,
}

impl SshAgentStore {
    pub(crate) fn new() -> Self {
        Self {
            identities: Vec::new(),
        }
    }

    pub(crate) fn len(&self) -> usize {
        self.identities.len()
    }

    pub(crate) fn clear(&mut self) {
        // Dropping each StoredIdentity zeroizes its private key.
        self.identities.clear();
    }

    // Replaces every identity drawn from `db_key` with a freshly decoded slice.
    // Sources that fail to decode are logged and skipped.
    pub(crate) fn replace_db(&mut self, db_key: &str, sources: Vec<SshAgentKeySource>) {
        self.identities.retain(|id| id.db_key != db_key);
        self.decode_into(sources);
    }

    pub(crate) fn remove_db(&mut self, db_key: &str) {
        self.identities.retain(|id| id.db_key != db_key);
    }

    // Clears the store and rebuilds it from sources across all open databases.
    pub(crate) fn set_all(&mut self, sources: Vec<SshAgentKeySource>) {
        self.identities.clear();
        self.decode_into(sources);
    }

    fn decode_into(&mut self, sources: Vec<SshAgentKeySource>) {
        for src in sources {
            match StoredIdentity::from_source(&src) {
                Ok(id) => {
                    log::debug!(
                        "SSH agent: loaded identity '{}' ({})",
                        id.comment,
                        id.fingerprint()
                    );
                    self.identities.push(id);
                }
                Err(e) => {
                    log::warn!(
                        "SSH agent: skipping SSH Key entry '{}': {}",
                        src.title,
                        e
                    );
                }
            }
        }
    }

    // The identities to advertise to clients, de-duplicated by fingerprint so a
    // key present in two open databases is listed once.
    pub(crate) fn identities(&self) -> Vec<Identity> {
        let mut seen: HashMap<String, ()> = HashMap::new();
        let mut out = Vec::new();
        for id in &self.identities {
            if seen.insert(id.fingerprint(), ()).is_none() {
                out.push(Identity {
                    credential: PublicCredential::Key(id.public_key_data.clone()),
                    comment: id.comment.clone(),
                });
            }
        }
        out
    }

    // Returns the confirmation metadata for the identity matching `requested`,
    // or None if no such key is currently served.
    pub(crate) fn confirmation_info(&self, requested: &KeyData) -> Option<ConfirmInfo> {
        self.identities
            .iter()
            .find(|id| &id.public_key_data == requested)
            .map(|id| ConfirmInfo {
                require_confirmation: id.require_confirmation,
                comment: id.comment.clone(),
                fingerprint: id.fingerprint(),
            })
    }

    // Finds the identity matching the requested public key and signs `data`.
    // The first matching identity wins when the same key is in multiple dbs.
    pub(crate) fn sign(
        &self,
        requested: &KeyData,
        data: &[u8],
        flags: u32,
    ) -> Result<Signature, AgentError> {
        let id = self
            .identities
            .iter()
            .find(|id| &id.public_key_data == requested)
            .ok_or(AgentError::Failure)?;

        id.sign(data, flags).map_err(|e| {
            log::error!("SSH agent: signing failed for '{}': {}", id.comment, e);
            AgentError::Failure
        })
    }
}
