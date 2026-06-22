pub mod loader;

#[cfg(windows)]
mod pageant_compat;

use tauri::command;
use uuid::Uuid;

// ── Tauri commands ────────────────────────────────────────────────────────────

/// Manually load a specific attachment as SSH key into agent right now.
/// attachment_name: name of the file attachment in the entry.
#[command]
pub(crate) async fn ssh_agent_load_key(
    db_key: String,
    entry_uuid: Uuid,
    attachment_name: String,
) -> crate::Result<()> {
    let loaded = loader::auto_load_entry_attachment(&db_key, &entry_uuid, &attachment_name).await;
    if loaded {
        Ok(())
    } else {
        Err(format!(
            "'{}' is not a valid SSH private key or could not be loaded",
            attachment_name
        ))
    }
}

/// Manually remove a specific attachment's SSH key from agent.
#[command]
pub(crate) async fn ssh_agent_remove_key(
    db_key: String,
    entry_uuid: Uuid,
    attachment_name: String,
) -> crate::Result<()> {
    loader::auto_remove_entry_attachment(&db_key, &entry_uuid, &attachment_name).await
}

/// Returns true if any SSH key from this entry is currently loaded in agent.
#[command]
pub(crate) async fn ssh_agent_is_key_loaded(
    db_key: String,
    entry_uuid: Uuid,
) -> crate::Result<bool> {
    Ok(loader::is_key_loaded(&db_key, &entry_uuid))
}

/// Returns UUIDs of all entries whose SSH keys are currently loaded for this database.
#[command]
pub(crate) async fn ssh_agent_loaded_entries(db_key: String) -> crate::Result<Vec<String>> {
    Ok(loader::loaded_entry_uuids(&db_key))
}

/// Debug: returns all distinct CustomData keys found across all entries in the DB.
#[command]
pub(crate) async fn ssh_agent_debug_custom_data_keys(db_key: String) -> crate::Result<Vec<String>> {
    use onekeepass_core::db_service as kp_service;
    kp_service::all_entry_custom_data_keys(&db_key).map_err(|e| e.to_string())
}

/// Diagnostic: load a PPK or OpenSSH key file from disk and attempt to add it to any running agent.
/// Returns a list of status strings describing each step so problems are visible without log files.
/// Call from browser devtools: __TAURI__.core.invoke('ssh_agent_test_load_file', {path:'C:\\...\\test.ppk', passphrase:'1234'})
#[command]
pub(crate) async fn ssh_agent_test_load_file(path: String, passphrase: String) -> Vec<String> {
    let mut log: Vec<String> = Vec::new();

    // ── 1. Read file ──────────────────────────────────────────────────────────
    let bytes = match std::fs::read(&path) {
        Ok(b) => {
            log.push(format!("OK  read {} bytes from {:?}", b.len(), path));
            b
        }
        Err(e) => {
            log.push(format!("ERR read file: {e}"));
            return log;
        }
    };

    // ── 2. Parse key ──────────────────────────────────────────────────────────
    let pass = if passphrase.is_empty() {
        None
    } else {
        Some(passphrase.as_str())
    };
    let _key = match loader::parse_key_from_bytes(&bytes, pass) {
        Some(k) => {
            log.push(format!(
                "OK  parsed key, algorithm={:?} fingerprint={}",
                k.algorithm(),
                k.fingerprint(russh_keys::ssh_key::HashAlg::Sha256)
            ));
            k
        }
        None => {
            log.push("ERR key parse failed (check passphrase and key format)".into());
            return log;
        }
    };

    // ── 3. Agent availability ─────────────────────────────────────────────────
    #[cfg(windows)]
    {
        let openssh_ok = tokio::net::windows::named_pipe::ClientOptions::new()
            .open(r"\\.\pipe\openssh-ssh-agent")
            .is_ok();
        let pageant_ok = pageant_compat::is_pageant_running();
        log.push(format!(
            "INFO openssh-agent running={openssh_ok}  pageant running={pageant_ok}"
        ));

        if !openssh_ok && !pageant_ok {
            log.push("ERR no SSH agent found — start OpenSSH agent or Pageant first".into());
            return log;
        }

        // ── 4. Add to OpenSSH ─────────────────────────────────────────────────
        if openssh_ok {
            match russh_keys::agent::client::AgentClient::connect_named_pipe(
                r"\\.\pipe\openssh-ssh-agent",
            )
            .await
            {
                Ok(mut c) => match c.add_identity(&key, &[]).await {
                    Ok(()) => log.push("OK  key added to OpenSSH agent".into()),
                    Err(e) => log.push(format!("ERR OpenSSH add_identity: {e}")),
                },
                Err(e) => log.push(format!("ERR OpenSSH connect: {e}")),
            }
        }

        // ── 5. Add to Pageant ─────────────────────────────────────────────────
        if pageant_ok {
            let mut c = loader::make_pageant_client_pub();
            match c.add_identity(&key, &[]).await {
                Ok(()) => log.push("OK  key added to Pageant".into()),
                Err(e) => log.push(format!("ERR Pageant add_identity: {e}")),
            }
        }
    }

    #[cfg(not(windows))]
    log.push("INFO this command is windows-only for agent testing".into());

    log
}

/// Returns which SSH agents are currently available on this machine.
/// Response: { "openssh": bool, "pageant": bool }
#[command]
pub(crate) async fn ssh_agent_available_agents() -> crate::Result<serde_json::Value> {
    #[cfg(windows)]
    {
        let openssh = tokio::net::windows::named_pipe::ClientOptions::new()
            .open(r"\\.\pipe\openssh-ssh-agent")
            .is_ok();
        let pageant = pageant_compat::is_pageant_running();
        Ok(serde_json::json!({ "openssh": openssh, "pageant": pageant }))
    }
    #[cfg(unix)]
    {
        let ssh_sock = std::env::var("SSH_AUTH_SOCK").unwrap_or_default();
        let openssh = !ssh_sock.is_empty() && std::path::Path::new(&ssh_sock).exists();
        Ok(serde_json::json!({ "openssh": openssh, "pageant": false }))
    }
    #[cfg(not(any(unix, windows)))]
    Ok(serde_json::json!({ "openssh": false, "pageant": false }))
}
