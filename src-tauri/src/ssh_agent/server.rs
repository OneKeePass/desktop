// Unix-socket transport for the SSH agent (macOS / Linux).
//
// Resolves the socket path, cleans up a stale socket safely, binds, and spawns
// the `ssh-agent-lib` accept loop on the Tauri async runtime. The loop runs
// until the shared `Notify` is signalled (agent stop / app quit), at which point
// the socket file is removed.

use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::sync::{Arc, RwLock};

use tokio::sync::Notify;

use crate::sandbox;

use super::session::AgentSession;
use super::store::SshAgentStore;

const SOCKET_DIR_NAME: &str = "ssh-agent";
const SOCKET_FILE_NAME: &str = "agent.sock";

// Where the agent's unix socket lives.
//
// Under the Mac App Store sandbox the per-process container is unreachable by an
// external `ssh`/`git`, so the socket must sit in the App Group container. On
// every other build it lives in a private 0700 directory under the app home.
// Both paths are kept short to stay within the `sun_path` 104-byte limit.
pub(crate) fn socket_path() -> PathBuf {
    match sandbox::group_container_path() {
        Some(parent) => parent.join(SOCKET_FILE_NAME),
        None => crate::app_paths::app_home_dir()
            .join(SOCKET_DIR_NAME)
            .join(SOCKET_FILE_NAME),
    }
}

// Ensures the socket's parent directory exists with private (0700) permissions.
fn ensure_parent_dir(path: &Path) -> Result<(), String> {
    let Some(parent) = path.parent() else {
        return Err("socket path has no parent directory".into());
    };
    std::fs::create_dir_all(parent)
        .map_err(|e| format!("failed to create socket dir {:?}: {e}", parent))?;
    // Best-effort: tighten the directory to the owner only. The App Group
    // container's permissions are managed by the OS, so a failure here is not
    // fatal.
    let _ = std::fs::set_permissions(parent, std::fs::Permissions::from_mode(0o700));
    Ok(())
}

// Removes a left-over socket from a prior run, but only if it is genuinely a
// dead socket we own — never follows a symlink and never unlinks a regular file.
fn cleanup_stale_socket(path: &Path) {
    let meta = match std::fs::symlink_metadata(path) {
        Ok(m) => m,
        Err(_) => return, // nothing there
    };

    if meta.file_type().is_symlink() {
        log::warn!(
            "SSH agent: refusing to remove {:?} - it is a symlink, not our socket",
            path
        );
        return;
    }

    use std::os::unix::fs::FileTypeExt;
    if !meta.file_type().is_socket() {
        log::warn!(
            "SSH agent: refusing to remove {:?} - it is not a socket",
            path
        );
        return;
    }

    // It's a socket. If a live agent is already listening, a connect succeeds
    // and we must not clobber it. If the connect is refused, it's stale.
    match std::os::unix::net::UnixStream::connect(path) {
        Ok(_) => {
            log::warn!(
                "SSH agent: a socket at {:?} is already live; bind will fail",
                path
            );
        }
        Err(_) => {
            let _ = std::fs::remove_file(path);
        }
    }
}

// Binds the socket synchronously (so bind errors are reported immediately) and
// spawns the accept loop. The loop owns the listener; dropping it on shutdown
// closes the socket.
pub(crate) fn bind_and_spawn(
    path: &Path,
    store: Arc<RwLock<SshAgentStore>>,
    shutdown: Arc<Notify>,
) -> Result<(), String> {
    ensure_parent_dir(path)?;
    cleanup_stale_socket(path);

    let std_listener = std::os::unix::net::UnixListener::bind(path)
        .map_err(|e| format!("failed to bind {:?}: {e}", path))?;
    std_listener
        .set_nonblocking(true)
        .map_err(|e| format!("set_nonblocking failed: {e}"))?;

    // Only the owner may talk to the agent.
    let _ = std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600));

    let path_owned = path.to_path_buf();

    tauri::async_runtime::spawn(async move {
        let listener = match tokio::net::UnixListener::from_std(std_listener) {
            Ok(l) => l,
            Err(e) => {
                log::error!("SSH agent: from_std failed: {e}");
                return;
            }
        };

        let session = AgentSession::new(store);

        log::info!("SSH agent listening at {:?}", path_owned);

        tokio::select! {
            res = ssh_agent_lib::agent::listen(listener, session) => {
                if let Err(e) = res {
                    log::error!("SSH agent listener ended with error: {:?}", e);
                }
            }
            _ = shutdown.notified() => {
                log::info!("SSH agent listener received shutdown signal");
            }
        }

        // Drop the listener (implicit) and remove the socket file.
        let _ = std::fs::remove_file(&path_owned);
    });

    Ok(())
}
