// Windows OpenSSH named-pipe transport for the SSH agent.
//
// Binds the well-known `\\.\pipe\openssh-ssh-agent` pipe that stock Win32-OpenSSH
// and Git-for-Windows clients connect to with no extra configuration, and serves
// them through the same `AgentSession` / `SshAgentStore` the unix socket uses.
// `ssh-agent-lib` already provides a `NamedPipeListener` and the same `listen`
// accept loop, so this transport is a thin mirror of `server.rs`.

use std::sync::{Arc, RwLock};
use std::time::Duration;

use tokio::sync::Notify;

use ssh_agent_lib::agent::NamedPipeListener;

use super::session::AgentSession;
use super::store::SshAgentStore;

// The pipe stock OpenSSH clients look for. Matching it means `ssh`, `ssh-add`,
// and Git-for-Windows find the agent without any IdentityAgent / SSH_AUTH_SOCK
// configuration.
pub(crate) const PIPE_PATH: &str = r"\\.\pipe\openssh-ssh-agent";

// Binds the named pipe and spawns the accept loop on the Tauri async runtime.
// The loop runs until `shutdown` is signalled (agent stop / app quit).
//
// `NamedPipeListener::bind` uses `first_pipe_instance(true)`, so the bind fails
// if another process already owns the pipe — typically the built-in Windows
// `ssh-agent` service. That error is surfaced through the returned `Result` so
// the settings UI can explain it rather than failing silently.
pub(crate) fn bind_and_spawn(
    store: Arc<RwLock<SshAgentStore>>,
    shutdown: Arc<Notify>,
) -> Result<(), String> {
    let (bind_tx, bind_rx) = std::sync::mpsc::channel::<Result<(), String>>();

    tauri::async_runtime::spawn(async move {
        // Tokio's Windows named-pipe creation requires a running reactor, so the
        // bind must happen inside this async task rather than in the synchronous
        // caller during app startup.
        let listener = match NamedPipeListener::bind(PIPE_PATH) {
            Ok(listener) => {
                let _ = bind_tx.send(Ok(()));
                listener
            }
            Err(e) => {
                let msg = format!(
                    "failed to bind {PIPE_PATH}: {e}. The Windows OpenSSH 'ssh-agent' \
                     service may already own this pipe — stop and disable it in \
                     services.msc, then re-enable the OneKeePass agent"
                );
                let _ = bind_tx.send(Err(msg));
                return;
            }
        };

        let session = AgentSession::new(store);
        log::info!("SSH agent listening on {}", PIPE_PATH);

        tokio::select! {
            res = ssh_agent_lib::agent::listen(listener, session) => {
                if let Err(e) = res {
                    log::error!("SSH agent named-pipe listener ended with error: {:?}", e);
                }
            }
            _ = shutdown.notified() => {
                log::info!("SSH agent named-pipe listener received shutdown signal");
            }
        }
    });

    bind_rx
        .recv_timeout(Duration::from_secs(5))
        .map_err(|e| format!("timed out waiting for {PIPE_PATH} bind result: {e}"))?
}
