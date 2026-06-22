use std::collections::HashSet;
use std::sync::Mutex;

use log::{debug, error, info, warn};
use once_cell::sync::Lazy;
use uuid::Uuid;

use onekeepass_core::db_service as kp_service;
use russh_keys::agent::client::AgentClient;
use russh_keys::{decode_secret_key, ssh_key::PrivateKey};


/// In-memory set of currently-loaded keys, keyed as "{db_key}::{uuid}".
static LOADED_KEYS: Lazy<Mutex<HashSet<String>>> = Lazy::new(|| Mutex::new(HashSet::new()));

fn loaded_key_token(db_key: &str, entry_uuid: &Uuid) -> String {
    format!("{}::{}", db_key, entry_uuid)
}

fn mark_loaded(db_key: &str, entry_uuid: &Uuid) {
    if let Ok(mut set) = LOADED_KEYS.lock() {
        set.insert(loaded_key_token(db_key, entry_uuid));
    }
}

fn mark_removed(db_key: &str, entry_uuid: &Uuid) {
    if let Ok(mut set) = LOADED_KEYS.lock() {
        set.remove(&loaded_key_token(db_key, entry_uuid));
    }
}

pub fn is_key_loaded(db_key: &str, entry_uuid: &Uuid) -> bool {
    LOADED_KEYS
        .lock()
        .map(|s| s.contains(&loaded_key_token(db_key, entry_uuid)))
        .unwrap_or(false)
}

pub fn loaded_entry_uuids(db_key: &str) -> Vec<String> {
    let prefix = format!("{}::", db_key);
    LOADED_KEYS
        .lock()
        .map(|s| {
            s.iter()
                .filter_map(|token| token.strip_prefix(&prefix).map(|u| u.to_string()))
                .collect()
        })
        .unwrap_or_default()
}

pub fn db_hex_hash(db_key: &str) -> crate::Result<String> {
    let bytes = kp_service::db_checksum_hash(db_key).map_err(|e| e.to_string())?;
    Ok(hex::encode(&bytes))
}

/// Returns all attachment names for an entry.
fn attachment_names(db_key: &str, entry_uuid: &Uuid) -> Vec<String> {
    let Ok(form_data) = kp_service::get_entry_form_data_by_id(db_key, entry_uuid) else {
        return Vec::new();
    };
    let Ok(json) = serde_json::to_value(&form_data) else {
        return Vec::new();
    };
    json.get("binary_key_values")
        .and_then(|v| v.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|bkv| bkv.get("key").and_then(|k| k.as_str()).map(|s| s.to_string()))
                .collect()
        })
        .unwrap_or_default()
}

/// Returns raw bytes for a named attachment.
fn attachment_bytes(db_key: &str, entry_uuid: &Uuid, attachment_name: &str) -> crate::Result<Vec<u8>> {
    let form_data = kp_service::get_entry_form_data_by_id(db_key, entry_uuid)
        .map_err(|e| e.to_string())?;
    let json = serde_json::to_value(&form_data).map_err(|e| e.to_string())?;
    let binary_kvs = json
        .get("binary_key_values")
        .and_then(|v| v.as_array())
        .ok_or_else(|| "Entry has no attachments".to_string())?;
    let data_hash_str = binary_kvs
        .iter()
        .find(|bkv| bkv.get("key").and_then(|k| k.as_str()) == Some(attachment_name))
        .and_then(|bkv| bkv.get("data_hash").and_then(|h| h.as_str()))
        .ok_or_else(|| format!("Attachment '{}' not found", attachment_name))?
        .to_owned();
    let data_hash = kp_service::service_util::parse_attachment_hash(&data_hash_str)
        .map_err(|e| e.to_string())?;
    let mut buf: Vec<u8> = Vec::new();
    kp_service::save_attachment_to_writter(db_key, &data_hash, &mut buf)
        .map_err(|e| e.to_string())?;
    Ok(buf)
}

fn entry_password(db_key: &str, entry_uuid: &Uuid) -> Option<String> {
    kp_service::entry_key_value_fields(db_key, entry_uuid)
        .ok()
        .and_then(|fields| fields.get("Password").cloned())
        .filter(|p| !p.is_empty())
}

/// Public alias for diagnostics — same logic as the internal `try_parse_key`.
pub fn parse_key_from_bytes(bytes: &[u8], passphrase: Option<&str>) -> Option<PrivateKey> {
    try_parse_key(bytes, passphrase)
}

/// Tries to parse attachment bytes as an SSH private key (OpenSSH PEM or PuTTY PPK).
/// Returns None silently if the data is not a recognised key format.
fn try_parse_key(bytes: &[u8], passphrase: Option<&str>) -> Option<PrivateKey> {
    let text = String::from_utf8(bytes.to_vec()).ok()?;
    let trimmed = text.trim_start();

    if trimmed.starts_with("PuTTY-User-Key-File-") {
        return try_parse_ppk(&text, passphrase);
    }

    if !trimmed.starts_with("-----BEGIN") {
        return None;
    }
    decode_secret_key(&text, passphrase).ok()
}

/// Parse a PuTTY PPK file (v2 or v3) and return an OpenSSH PrivateKey.
/// Uses the internal forked ssh-key crate (which has PPK support) then bridges
/// via an OpenSSH PEM roundtrip so the returned type matches russh-keys' PrivateKey.
fn try_parse_ppk(text: &str, passphrase: Option<&str>) -> Option<PrivateKey> {
    use ssh_key_ppk::LineEnding;
    use ssh_key_ppk::PrivateKey as PpkKey;

    let ppk_key = match PpkKey::from_ppk(text, passphrase.map(|s| s.to_string())) {
        Ok(k) => k,
        Err(e) => {
            warn!("ssh_agent: PPK parse failed: {:?}", e);
            return None;
        }
    };

    let pem = match ppk_key.to_openssh(LineEnding::LF) {
        Ok(p) => p,
        Err(e) => {
            warn!("ssh_agent: PPK → OpenSSH serialisation failed: {:?}", e);
            return None;
        }
    };

    match decode_secret_key(&pem, None) {
        Ok(k) => {
            info!("ssh_agent: PPK parsed OK, algorithm: {:?}", k.algorithm());
            Some(k)
        }
        Err(e) => {
            warn!("ssh_agent: OpenSSH decode after PPK roundtrip failed: {:?}", e);
            None
        }
    }
}

// ── Platform-specific agent connection helpers ────────────────────────────────

/// Build an AgentClient backed by a properly-framed Pageant bridge.
///
/// pageant::PageantStream has a bug: it calls buf.split() after any read_buf(),
/// which can forward a partial SSH message to query_pageant_direct, causing
/// Pageant to reject it → the background task breaks → AgentClient sees EOF
/// ("early eof"). This replacement reads EXACTLY one complete message
/// (4-byte length prefix + body) before forwarding.
#[cfg(windows)]
fn make_pageant_client() -> AgentClient<impl tokio::io::AsyncRead + tokio::io::AsyncWrite + Send + Unpin + 'static> {
    use std::sync::atomic::{AtomicU64, Ordering};
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    static COOKIE_SEQ: AtomicU64 = AtomicU64::new(1);

    let (client_end, mut pageant_end) = tokio::io::duplex(65536);

    tokio::spawn(async move {
        let mut len_buf = [0u8; 4];
        loop {
            match pageant_end.read_exact(&mut len_buf).await {
                Ok(_) => {}
                Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => break,
                Err(e) => {
                    warn!("ssh_agent: pageant bridge len read error: {}", e);
                    break;
                }
            }
            let body_len = u32::from_be_bytes(len_buf) as usize;
            let mut body = vec![0u8; body_len];
            if let Err(e) = pageant_end.read_exact(&mut body).await {
                warn!("ssh_agent: pageant bridge body read error (want {} bytes): {}", body_len, e);
                break;
            }

            // Full SSH agent wire message: [4-byte length][body]
            let mut msg = Vec::with_capacity(4 + body_len);
            msg.extend_from_slice(&len_buf);
            msg.extend_from_slice(&body);

            let cookie = COOKIE_SEQ.fetch_add(1, Ordering::Relaxed);
            match super::pageant_compat::query(cookie, &msg) {
                Ok(response) => {
                    if let Err(e) = pageant_end.write_all(&response).await {
                        warn!("ssh_agent: pageant bridge write error: {}", e);
                        break;
                    }
                }
                Err(e) => {
                    warn!("ssh_agent: query_pageant_direct failed: {:?}", e);
                    break;
                }
            }
        }
    });

    AgentClient::connect(client_end)
}

/// Public alias so mod.rs diagnostic command can use the same bridge.
#[cfg(windows)]
pub fn make_pageant_client_pub() -> AgentClient<impl tokio::io::AsyncRead + tokio::io::AsyncWrite + Send + Unpin + 'static> {
    make_pageant_client()
}

#[cfg(unix)]
async fn add_key_to_agent(key: &PrivateKey) -> crate::Result<()> {
    let mut client = AgentClient::connect_env().await.map_err(|e| e.to_string())?;
    client.add_identity(key, &[]).await.map_err(|e| e.to_string())
}

#[cfg(unix)]
async fn remove_key_from_agent(key: &PrivateKey) -> crate::Result<()> {
    let mut client = AgentClient::connect_env().await.map_err(|e| e.to_string())?;
    client.remove_identity(key.public_key()).await.map_err(|e| e.to_string())
}

#[cfg(windows)]
async fn add_key_to_agent(key: &PrivateKey) -> crate::Result<()> {
    let mut any_ok = false;

    // OpenSSH built-in agent (Windows 10+).
    if let Ok(mut client) = AgentClient::connect_named_pipe(r"\\.\pipe\openssh-ssh-agent").await {
        match client.add_identity(key, &[]).await {
            Ok(()) => { any_ok = true; info!("ssh_agent: key added to OpenSSH agent"); }
            Err(e) => warn!("ssh_agent: OpenSSH agent add_identity failed: {}", e),
        }
    }

    // Pageant (PuTTY agent) — only try if the window exists.
    if super::pageant_compat::is_pageant_running() {
        let mut client = make_pageant_client();
        match client.add_identity(key, &[]).await {
            Ok(()) => { any_ok = true; info!("ssh_agent: key added to Pageant"); }
            Err(e) => warn!("ssh_agent: Pageant add_identity failed: {}", e),
        }
    }

    if any_ok {
        Ok(())
    } else {
        Err("No SSH agent available (tried OpenSSH named pipe and Pageant)".to_string())
    }
}

#[cfg(windows)]
async fn remove_key_from_agent(key: &PrivateKey) -> crate::Result<()> {
    let mut any_ok = false;

    if let Ok(mut client) = AgentClient::connect_named_pipe(r"\\.\pipe\openssh-ssh-agent").await {
        match client.remove_identity(key.public_key()).await {
            Ok(()) => { any_ok = true; }
            Err(e) => warn!("ssh_agent: OpenSSH agent remove_identity failed: {}", e),
        }
    }

    if super::pageant_compat::is_pageant_running() {
        let mut client = make_pageant_client();
        match client.remove_identity(key.public_key()).await {
            Ok(()) => { any_ok = true; }
            Err(e) => warn!("ssh_agent: Pageant remove_identity failed: {}", e),
        }
    }

    if any_ok { Ok(()) } else { Ok(()) } // removal best-effort; never block close
}

// ── Shared logic (unix + windows) ────────────────────────────────────────────

/// Scans all attachments of an entry, tries to parse each as SSH key, loads any that succeed.
/// Uses entry Password as passphrase. Returns list of attachment names loaded.
#[cfg(any(unix, windows))]
pub async fn auto_load_entry(db_key: &str, entry_uuid: &Uuid) -> Vec<String> {
    let names = attachment_names(db_key, entry_uuid);
    if names.is_empty() {
        return Vec::new();
    }
    let passphrase = entry_password(db_key, entry_uuid);
    let mut loaded = Vec::new();

    for name in &names {
        let bytes = match attachment_bytes(db_key, entry_uuid, name) {
            Ok(b) => b,
            Err(_) => continue,
        };
        let key = match try_parse_key(&bytes, passphrase.as_deref()) {
            Some(k) => k,
            None => {
                debug!("ssh_agent: '{}' is not a recognised SSH/PPK key, skipping", name);
                continue;
            }
        };
        match add_key_to_agent(&key).await {
            Ok(()) => {
                mark_loaded(db_key, entry_uuid);
                info!("ssh_agent: auto-loaded key '{}' from entry {}", name, entry_uuid);
                loaded.push(name.clone());
            }
            Err(e) => warn!("ssh_agent: failed to add key '{}' to agent: {}", name, e),
        }
    }
    loaded
}

/// Removes all SSH keys from an entry's attachments from the agent.
#[cfg(any(unix, windows))]
pub async fn auto_remove_entry(db_key: &str, entry_uuid: &Uuid) {
    let names = attachment_names(db_key, entry_uuid);
    let passphrase = entry_password(db_key, entry_uuid);
    for name in &names {
        let bytes = match attachment_bytes(db_key, entry_uuid, name) {
            Ok(b) => b,
            Err(_) => continue,
        };
        if let Some(key) = try_parse_key(&bytes, passphrase.as_deref()) {
            if let Err(e) = remove_key_from_agent(&key).await {
                warn!("ssh_agent: failed to remove key '{}': {}", name, e);
            }
        }
    }
    mark_removed(db_key, entry_uuid);
}

/// Loads a single named attachment as SSH key. Returns true if successful.
#[cfg(any(unix, windows))]
pub async fn auto_load_entry_attachment(db_key: &str, entry_uuid: &Uuid, attachment_name: &str) -> bool {
    let passphrase = entry_password(db_key, entry_uuid);
    let bytes = match attachment_bytes(db_key, entry_uuid, attachment_name) {
        Ok(b) => b,
        Err(_) => return false,
    };
    let key = match try_parse_key(&bytes, passphrase.as_deref()) {
        Some(k) => k,
        None => return false,
    };
    match add_key_to_agent(&key).await {
        Ok(()) => { mark_loaded(db_key, entry_uuid); true }
        Err(e) => { warn!("ssh_agent: failed to add '{}': {}", attachment_name, e); false }
    }
}

/// Removes a single named attachment's SSH key from agent.
#[cfg(any(unix, windows))]
pub async fn auto_remove_entry_attachment(db_key: &str, entry_uuid: &Uuid, attachment_name: &str) -> crate::Result<()> {
    let passphrase = entry_password(db_key, entry_uuid);
    let bytes = attachment_bytes(db_key, entry_uuid, attachment_name)?;
    let key = try_parse_key(&bytes, passphrase.as_deref())
        .ok_or_else(|| format!("'{}' is not a valid SSH private key", attachment_name))?;
    remove_key_from_agent(&key).await?;
    mark_removed(db_key, entry_uuid);
    Ok(())
}

/// On DB open: scan every entry, auto-load any SSH keys found in attachments.
#[cfg(any(unix, windows))]
pub async fn load_all_on_open(db_key: &str) -> Vec<Uuid> {
    let uuids = match kp_service::all_entry_uuids(db_key) {
        Ok(u) => u,
        Err(e) => { error!("ssh_agent: failed to list entries: {}", e); return Vec::new(); }
    };

    debug!("ssh_agent: scanning {} entries for SSH keys", uuids.len());
    let mut loaded = Vec::new();
    for uuid in uuids {
        let keys = auto_load_entry(db_key, &uuid).await;
        if !keys.is_empty() {
            loaded.push(uuid);
        }
    }
    info!("ssh_agent: loaded keys from {} entries on open", loaded.len());
    loaded
}

/// On DB close: remove all keys that were loaded this session.
#[cfg(any(unix, windows))]
pub async fn remove_all_on_close(db_key: &str) {
    let uuids: Vec<Uuid> = loaded_entry_uuids(db_key)
        .iter()
        .filter_map(|s| s.parse().ok())
        .collect();

    for uuid in uuids {
        auto_remove_entry(db_key, &uuid).await;
    }
}

// ── Stubs for unsupported platforms ──────────────────────────────────────────

#[cfg(not(any(unix, windows)))]
pub async fn auto_load_entry(_: &str, _: &Uuid) -> Vec<String> { Vec::new() }
#[cfg(not(any(unix, windows)))]
pub async fn auto_remove_entry(_: &str, _: &Uuid) {}
#[cfg(not(any(unix, windows)))]
pub async fn auto_load_entry_attachment(_: &str, _: &Uuid, _: &str) -> bool { false }
#[cfg(not(any(unix, windows)))]
pub async fn auto_remove_entry_attachment(_: &str, _: &Uuid, _: &str) -> crate::Result<()> {
    Err("SSH agent not supported on this platform".into())
}
#[cfg(not(any(unix, windows)))]
pub async fn load_all_on_open(_: &str) -> Vec<Uuid> { Vec::new() }
#[cfg(not(any(unix, windows)))]
pub async fn remove_all_on_close(_: &str) {}
