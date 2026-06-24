// Thin `ssh-agent-lib` adapter over the transport-neutral `SshAgentStore`.
//
// `ssh-agent-lib` clones the agent object for each accepted connection (a
// `Session + Clone` automatically implements `Agent<UnixListener>`), so this
// holds only a shared handle to the store. All real logic lives in `store.rs`.

use std::sync::{Arc, RwLock};

use ssh_agent_lib::agent::Session;
use ssh_agent_lib::error::AgentError;
use ssh_agent_lib::proto::{Identity, SignRequest};
use ssh_agent_lib::ssh_key::Signature;

use super::store::SshAgentStore;

#[derive(Clone)]
pub(crate) struct AgentSession {
    store: Arc<RwLock<SshAgentStore>>,
}

impl AgentSession {
    pub(crate) fn new(store: Arc<RwLock<SshAgentStore>>) -> Self {
        Self { store }
    }
}

#[ssh_agent_lib::async_trait]
impl Session for AgentSession {
    async fn request_identities(&mut self) -> Result<Vec<Identity>, AgentError> {
        Ok(self.store.read().unwrap().identities())
    }

    async fn sign(&mut self, request: SignRequest) -> Result<Signature, AgentError> {
        let requested = request.credential.key_data().clone();

        // Look up the matched identity's metadata under the lock, then release it
        // before any await (we must not hold a std RwLock guard across .await).
        let info = {
            let store = self.store.read().unwrap();
            store.confirmation_info(&requested)
        };
        let Some(info) = info else {
            // Key not (or no longer) served.
            log::warn!("SSH agent: sign request for a key that is not served");
            return Err(AgentError::Failure);
        };

        log::debug!(
            "SSH agent: sign request for '{}' ({}), require_confirmation={}",
            info.comment,
            info.fingerprint,
            info.require_confirmation
        );

        // Honor "Require Confirmation": prompt the user and bail on deny/timeout.
        if info.require_confirmation {
            let allowed = super::request_confirmation(info.comment.clone(), info.fingerprint).await;
            if !allowed {
                log::info!("SSH agent: sign request denied for '{}'", info.comment);
                return Err(AgentError::Failure);
            }
        }

        // Re-acquire the lock and sign. Re-finding the key also correctly fails
        // if the database was locked/closed while the dialog was open.
        let store = self.store.read().unwrap();
        store.sign(&requested, &request.data, request.flags)
    }
}
