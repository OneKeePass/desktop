use std::collections::HashMap;

use onekeepass_core::db_service::ssh_agent::SshAgentKeySource;
use ssh_agent_lib::agent::Session;
use ssh_agent_lib::proto::{
    AddIdentity, AddIdentityConstrained, KeyConstraint, PrivateCredential, PublicCredential,
    RemoveIdentity,
};

use super::store::{decode_identity, DecodedIdentity};

#[derive(Clone)]
struct TrackedIdentity {
    credential: PublicCredential,
    comment: String,
    fingerprint: String,
}

#[derive(Default)]
pub(crate) struct ClientRuntime {
    added_by_db: HashMap<String, Vec<TrackedIdentity>>,
    transport: Option<String>,
}

impl ClientRuntime {
    pub(crate) fn new() -> Self {
        Self::default()
    }

    pub(crate) fn start(&mut self, sources: Vec<SshAgentKeySource>) -> Result<(), String> {
        self.stop()?;
        let transport = transport_name()?;
        self.transport = Some(transport);
        match self.add_sources(sources) {
            Ok(()) => Ok(()),
            Err(e) => {
                if self.key_count() == 0 {
                    self.transport = None;
                }
                Err(e)
            }
        }
    }

    pub(crate) fn stop(&mut self) -> Result<(), String> {
        let mut first_error = None;
        let db_keys: Vec<String> = self.added_by_db.keys().cloned().collect();
        for db_key in db_keys {
            if let Err(e) = self.remove_db(&db_key) {
                if first_error.is_none() {
                    first_error = Some(e);
                }
            }
        }
        self.added_by_db.clear();
        self.transport = None;

        if let Some(e) = first_error {
            Err(e)
        } else {
            Ok(())
        }
    }

    pub(crate) fn replace_db(
        &mut self,
        db_key: &str,
        sources: Vec<SshAgentKeySource>,
    ) -> Result<(), String> {
        self.remove_db(db_key)?;
        self.add_sources(sources)
    }

    pub(crate) fn remove_db(&mut self, db_key: &str) -> Result<(), String> {
        let Some(identities) = self.added_by_db.remove(db_key) else {
            return Ok(());
        };

        let mut first_error = None;
        for id in identities {
            let credential = id.credential.clone();
            match run_agent_client(async move {
                let mut client = connect_client().await?;
                client
                    .remove_identity(RemoveIdentity { credential })
                    .await
                    .map_err(|e| format!("remove identity failed: {e}"))
            }) {
                Ok(()) => log::debug!(
                    "SSH agent client mode: removed identity '{}' ({})",
                    id.comment,
                    id.fingerprint
                ),
                Err(e) => {
                    log::warn!(
                        "SSH agent client mode: failed to remove identity '{}' ({}): {}",
                        id.comment,
                        id.fingerprint,
                        e
                    );
                    if first_error.is_none() {
                        first_error = Some(e);
                    }
                }
            }
        }

        if let Some(e) = first_error {
            Err(e)
        } else {
            Ok(())
        }
    }

    pub(crate) fn is_running(&self) -> bool {
        self.transport.is_some()
    }

    pub(crate) fn key_count(&self) -> usize {
        self.added_by_db.values().map(Vec::len).sum()
    }

    pub(crate) fn transport(&self) -> Option<String> {
        self.transport.clone()
    }

    fn add_sources(&mut self, sources: Vec<SshAgentKeySource>) -> Result<(), String> {
        let mut first_error = None;

        for src in sources {
            match decode_identity(&src) {
                Ok(identity) => match add_identity(identity) {
                    Ok(tracked) => {
                        self.added_by_db
                            .entry(src.db_key.clone())
                            .or_default()
                            .push(tracked);
                    }
                    Err(e) => {
                        log::warn!(
                            "SSH agent client mode: failed to add SSH Key entry '{}': {}",
                            src.title,
                            e
                        );
                        if first_error.is_none() {
                            first_error = Some(e);
                        }
                    }
                },
                Err(e) => {
                    log::warn!(
                        "SSH agent client mode: skipping SSH Key entry '{}': {}",
                        src.title,
                        e
                    );
                    if first_error.is_none() {
                        first_error = Some(e);
                    }
                }
            }
        }

        if let Some(e) = first_error {
            Err(e)
        } else {
            Ok(())
        }
    }
}

fn add_identity(identity: DecodedIdentity) -> Result<TrackedIdentity, String> {
    let db_key = identity.db_key.clone();
    let comment = identity.comment.clone();
    let fingerprint = identity.fingerprint();
    let public_key_data = identity.public_key_data.clone();
    let add_identity = AddIdentity {
        credential: PrivateCredential::Key {
            privkey: identity.key.key_data().clone(),
            comment: comment.clone(),
        },
    };

    let mut constraints = Vec::new();
    if identity.require_confirmation {
        log::debug!(
            "SSH agent client mode: ignoring Require Confirmation for '{}'; confirmation is only supported in Agent Mode",
            comment
        );
    }
    if let Some(lifetime) = identity.lifetime {
        let secs = lifetime.as_secs().min(u32::MAX as u64) as u32;
        if secs > 0 {
            constraints.push(KeyConstraint::Lifetime(secs));
        }
    }

    run_agent_client(async move {
        let mut client = connect_client().await?;
        if constraints.is_empty() {
            client
                .add_identity(add_identity)
                .await
                .map_err(|e| format!("add identity failed: {e}"))
        } else {
            client
                .add_identity_constrained(AddIdentityConstrained {
                    identity: add_identity,
                    constraints,
                })
                .await
                .map_err(|e| format!("add constrained identity failed: {e}"))
        }
    })?;

    log::debug!(
        "SSH agent client mode: added identity '{}' ({}) for db {}",
        comment,
        fingerprint,
        db_key
    );

    Ok(TrackedIdentity {
        credential: PublicCredential::Key(public_key_data),
        comment,
        fingerprint,
    })
}

fn run_agent_client<F, T>(future: F) -> Result<T, String>
where
    F: std::future::Future<Output = Result<T, String>> + Send + 'static,
    T: Send + 'static,
{
    std::thread::spawn(move || {
        let runtime = tokio::runtime::Builder::new_current_thread()
            .enable_io()
            .enable_time()
            .build()
            .map_err(|e| format!("create runtime failed: {e}"))?;
        runtime.block_on(future)
    })
    .join()
    .map_err(|_| "SSH agent client worker thread panicked".to_string())?
}

#[cfg(unix)]
type ClientStream = tokio::net::UnixStream;

#[cfg(windows)]
type ClientStream = tokio::net::windows::named_pipe::NamedPipeClient;

#[cfg(unix)]
async fn connect_client() -> Result<ssh_agent_lib::client::Client<ClientStream>, String> {
    let path = std::env::var("SSH_AUTH_SOCK")
        .map_err(|_| "SSH_AUTH_SOCK is not set for this app process".to_string())?;
    if path.trim().is_empty() {
        return Err("SSH_AUTH_SOCK is empty for this app process".into());
    }
    let stream = tokio::net::UnixStream::connect(&path)
        .await
        .map_err(|e| format!("connect to SSH_AUTH_SOCK '{path}' failed: {e}"))?;
    Ok(ssh_agent_lib::client::Client::new(stream))
}

#[cfg(unix)]
fn transport_name() -> Result<String, String> {
    std::env::var("SSH_AUTH_SOCK")
        .map(|path| format!("SSH_AUTH_SOCK={path}"))
        .map_err(|_| "SSH_AUTH_SOCK is not set for this app process".to_string())
}

#[cfg(windows)]
async fn connect_client() -> Result<ssh_agent_lib::client::Client<ClientStream>, String> {
    use tokio::net::windows::named_pipe::ClientOptions;

    let stream = ClientOptions::new()
        .open(super::pipe::PIPE_PATH)
        .map_err(|e| format!("connect to OpenSSH agent pipe '{}' failed: {e}", super::pipe::PIPE_PATH))?;
    Ok(ssh_agent_lib::client::Client::new(stream))
}

#[cfg(windows)]
fn transport_name() -> Result<String, String> {
    Ok(format!("OpenSSH pipe {}", super::pipe::PIPE_PATH))
}

#[cfg(not(any(unix, windows)))]
async fn connect_client() -> Result<ssh_agent_lib::client::Client<ClientStream>, String> {
    Err("SSH agent client mode is not supported on this platform".into())
}

#[cfg(not(any(unix, windows)))]
fn transport_name() -> Result<String, String> {
    Err("SSH agent client mode is not supported on this platform".into())
}
