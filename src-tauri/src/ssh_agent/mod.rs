// Desktop SSH agent service.
//
// Serves SSH keys drawn from unlocked databases over the standard agent
// transport so `ssh`, `git`, etc. can authenticate without the private key ever
// touching disk. This covers the unix-socket transport (macOS / Linux),
// the in-memory key store, and the open/lock/close lifecycle hooks. The
// confirmation flow  and the Windows transports  plug into
// the same `SshAgentStore` / `AgentSession`.
//
// The agent is disabled by default; nothing is bound until the user enables it.

// The unix-socket transport (macOS / Linux) and the two Windows transports
// (OpenSSH named pipe, Pageant) are each gated to their platform; all three feed
// the same `AgentSession` / `SshAgentStore`.
#[cfg(unix)]
mod server;
#[cfg(windows)]
mod pageant;
#[cfg(windows)]
mod pipe;
mod client;
mod session;
mod store;

use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, OnceLock, RwLock};
use std::time::Duration;

use serde::Serialize;
use tauri::Emitter;
use tokio::sync::{oneshot, Notify};

use onekeepass_core::db_service as kp_service;

use crate::app_state::AppState;
use crate::app_preference::{SshAgentClientTransport, SshAgentMode};
use crate::constants::event_names::SSH_AGENT_SIGN_REQUEST_EVENT;

use client::ClientRuntime;
use store::SshAgentStore;

// How long a "Require Confirmation" sign request waits for the user before it
// auto-denies, so a dropped/ignored dialog never wedges the ssh/git client.
const CONFIRM_TIMEOUT_SECS: u64 = 60;

// How often the sweep task prunes keys whose "Agent Lifetime" has elapsed.
// Expiry is enforced exactly on the read paths (identities/sign); this only
// bounds how long an expired key's decrypted bytes linger before being zeroized.
const PRUNE_INTERVAL_SECS: u64 = 15;

// Snapshot of the agent state reported to the UI.
#[derive(Serialize, Clone, Debug, Default)]
pub(crate) struct AgentStatus {
    pub running: bool,
    pub mode: Option<String>,
    pub transport: Option<String>,
    pub socket_path: Option<String>,
    pub key_count: usize,
    // Last bind/start error, surfaced so the settings UI can explain a failure
    // (path in use, permissions, etc.) instead of failing silently.
    pub error: Option<String>,
}

struct AgentRuntime {
    store: Arc<RwLock<SshAgentStore>>,
    // `Some` exactly while the stream listener task (unix socket or Windows
    // OpenSSH named pipe) is alive. Signalling it stops the accept loop and, on
    // unix, removes the socket file.
    shutdown: Option<Arc<Notify>>,
    socket_path: Option<String>,
    last_error: Option<String>,
    mode: Option<SshAgentMode>,
    client: ClientRuntime,
    // Set to true to stop the Agent-Lifetime sweep task. `Some` while the agent
    // is running; the sweep loop exits within one interval after this flips.
    prune_stop: Option<Arc<AtomicBool>>,
    // The Windows Pageant message-window, served on its own thread. `Some` while
    // the Pageant transport is running.
    #[cfg(windows)]
    pageant: Option<pageant::PageantHandle>,
}

static RUNTIME: OnceLock<Mutex<AgentRuntime>> = OnceLock::new();

// In-flight "Require Confirmation" sign requests, keyed by a generated request
// id. The Session::sign task parks on the receiver; the UI's answer command
// removes the sender and delivers the user's allow/deny.
static PENDING_CONFIRMS: OnceLock<Mutex<HashMap<String, oneshot::Sender<bool>>>> = OnceLock::new();

fn pending_confirms() -> &'static Mutex<HashMap<String, oneshot::Sender<bool>>> {
    PENDING_CONFIRMS.get_or_init(|| Mutex::new(HashMap::new()))
}

// Payload sent to the UI to raise the allow/deny dialog. There is no host on a
// key entry, so we show the key title and fingerprint.
#[derive(Serialize, Clone, Debug)]
struct SignRequestPayload {
    request_id: String,
    title: String,
    fingerprint: String,
}

fn runtime() -> &'static Mutex<AgentRuntime> {
    RUNTIME.get_or_init(|| {
        Mutex::new(AgentRuntime {
            store: Arc::new(RwLock::new(SshAgentStore::new())),
            shutdown: None,
            socket_path: None,
            last_error: None,
            mode: None,
            client: ClientRuntime::new(),
            prune_stop: None,
            #[cfg(windows)]
            pageant: None,
        })
    })
}

fn snapshot(rt: &AgentRuntime) -> AgentStatus {
    let running = match rt.mode {
        Some(SshAgentMode::Client) => rt.client.is_running(),
        _ => rt.shutdown.is_some(),
    };
    let key_count = match rt.mode {
        Some(SshAgentMode::Client) => rt.client.key_count(),
        _ => rt.store.read().unwrap().len(),
    };
    let transport = match rt.mode {
        Some(SshAgentMode::Client) => rt.client.transport(),
        _ => rt.socket_path.clone(),
    };
    AgentStatus {
        running,
        mode: rt.mode.as_ref().map(|m| m.as_str().to_string()),
        transport,
        socket_path: rt.socket_path.clone(),
        key_count,
        error: rt.last_error.clone(),
    }
}

fn configured_mode() -> SshAgentMode {
    AppState::state_instance()
        .preference
        .lock()
        .unwrap()
        .ssh_agent_mode()
}

fn configured_client_transport() -> SshAgentClientTransport {
    AppState::state_instance()
        .preference
        .lock()
        .unwrap()
        .ssh_agent_client_transport()
}

// Starts the agent: rebuilds the key store from every open database and binds
// the unix socket. Idempotent — calling it while already running just returns
// the current status.
pub(crate) fn start() -> AgentStatus {
    let mut rt = runtime().lock().unwrap();

    if rt.mode.is_some() {
        return snapshot(&rt);
    }

    rt.last_error = None;
    let mode = configured_mode();

    if mode == SshAgentMode::Client {
        let sources = kp_service::ssh_agent::list_ssh_agent_key_sources();
        let transport = configured_client_transport();
        match rt.client.start(sources, transport) {
            Ok(()) => {
                rt.mode = Some(SshAgentMode::Client);
                rt.socket_path = None;
                log::info!("SSH agent client mode started");
            }
            Err(e) => {
                log::error!("SSH agent client mode failed to start: {}", e);
                rt.last_error = Some(e);
                if rt.client.is_running() {
                    rt.mode = Some(SshAgentMode::Client);
                    rt.socket_path = None;
                    log::warn!(
                        "SSH agent client mode started with {} key(s) despite errors",
                        rt.client.key_count()
                    );
                }
            }
        }
        return snapshot(&rt);
    }

    // Build the store from all currently-open databases before binding.
    {
        let sources = kp_service::ssh_agent::list_ssh_agent_key_sources();
        rt.store.write().unwrap().set_all(sources);
    }

    #[cfg(unix)]
    {
        let path = server::socket_path();
        let shutdown = Arc::new(Notify::new());

        match server::bind_and_spawn(&path, rt.store.clone(), shutdown.clone()) {
            Ok(()) => {
                rt.shutdown = Some(shutdown);
                rt.socket_path = Some(path.to_string_lossy().to_string());
                rt.mode = Some(SshAgentMode::Agent);
                log::info!("SSH agent started");
            }
            Err(e) => {
                log::error!("SSH agent failed to start: {}", e);
                rt.last_error = Some(e);
                // Don't keep a half-built store around when we couldn't bind.
                rt.store.write().unwrap().clear();
            }
        }
    }

    #[cfg(windows)]
    {
        // Primary transport: the OpenSSH named pipe. If it binds we are "running".
        let shutdown = Arc::new(Notify::new());
        match pipe::bind_and_spawn(rt.store.clone(), shutdown.clone()) {
            Ok(()) => {
                rt.shutdown = Some(shutdown);
                rt.socket_path = Some(pipe::PIPE_PATH.to_string());
                rt.mode = Some(SshAgentMode::Agent);
                log::info!("SSH agent started (OpenSSH named pipe)");
            }
            Err(e) => {
                log::error!("SSH agent failed to start: {}", e);
                rt.last_error = Some(e);
                rt.store.write().unwrap().clear();
            }
        }

        // Secondary transport: Pageant for PuTTY-family clients. Best-effort — a
        // failure here (e.g. a real Pageant already running) does not take down
        // the named-pipe transport; it is only noted for the UI.
        if rt.shutdown.is_some() {
            match pageant::start(rt.store.clone()) {
                Ok(handle) => rt.pageant = Some(handle),
                Err(e) => {
                    log::warn!("SSH agent: Pageant transport unavailable: {}", e);
                    rt.last_error = Some(format!("Pageant transport unavailable: {e}"));
                }
            }
        }
    }

    #[cfg(not(any(unix, windows)))]
    {
        let msg = "SSH agent transport is not supported on this platform";
        log::warn!("{}", msg);
        rt.last_error = Some(msg.into());
        rt.store.write().unwrap().clear();
    }

    // If a transport bound, start the Agent-Lifetime sweep that prunes expired
    // keys. Transport-independent, so it is spawned once for any platform.
    if rt.shutdown.is_some() {
        let stop_flag = Arc::new(AtomicBool::new(false));
        spawn_prune_task(rt.store.clone(), stop_flag.clone());
        rt.prune_stop = Some(stop_flag);
    }

    snapshot(&rt)
}

// Periodically drops keys whose "Agent Lifetime" has elapsed (and zeroizes them
// via Drop). Exits within one interval after `stop_flag` is set, so each
// start/stop cycle owns exactly one sweep task.
fn spawn_prune_task(store: Arc<RwLock<SshAgentStore>>, stop_flag: Arc<AtomicBool>) {
    tauri::async_runtime::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_secs(PRUNE_INTERVAL_SECS)).await;
            if stop_flag.load(Ordering::Relaxed) {
                break;
            }
            let removed = store.write().unwrap().prune_expired();
            if removed > 0 {
                log::debug!("SSH agent: pruned {removed} expired key(s) (Agent Lifetime)");
            }
        }
    });
}

// Stops the agent, removes the socket, and wipes the key store from memory.
pub(crate) fn stop() -> AgentStatus {
    let mut rt = runtime().lock().unwrap();

    if rt.mode == Some(SshAgentMode::Client) {
        if let Err(e) = rt.client.stop() {
            log::warn!("SSH agent client mode stopped with cleanup errors: {}", e);
            rt.last_error = Some(e);
        } else {
            rt.last_error = None;
        }
        rt.mode = None;
        log::info!("SSH agent client mode stopped");
        return snapshot(&rt);
    }

    if let Some(shutdown) = rt.shutdown.take() {
        shutdown.notify_waiters();
    }
    // Signal the Agent-Lifetime sweep task to exit.
    if let Some(stop_flag) = rt.prune_stop.take() {
        stop_flag.store(true, Ordering::Relaxed);
    }

    // On unix the socket_path is a real file to unlink; on Windows it is the
    // named-pipe name (nothing to remove) and the Pageant window is torn down
    // separately.
    #[cfg(unix)]
    if let Some(path) = rt.socket_path.take() {
        let _ = std::fs::remove_file(&path);
    }
    #[cfg(not(unix))]
    {
        rt.socket_path = None;
    }
    #[cfg(windows)]
    if let Some(handle) = rt.pageant.take() {
        handle.stop();
    }

    rt.store.write().unwrap().clear();
    rt.last_error = None;
    rt.mode = None;

    log::info!("SSH agent stopped");
    snapshot(&rt)
}

pub(crate) fn status() -> AgentStatus {
    snapshot(&runtime().lock().unwrap())
}

fn is_running(rt: &AgentRuntime) -> bool {
    rt.mode.is_some()
}

// Refreshes the agent's slice of keys for one database (open / unlock / reload).
// No-op when the agent isn't running.
pub(crate) fn reload_keys_for_db(db_key: &str) {
    let mut rt = runtime().lock().unwrap();
    if !is_running(&rt) {
        return;
    }
    let sources = kp_service::ssh_agent::ssh_agent_key_sources_for_db(db_key);
    if rt.mode == Some(SshAgentMode::Client) {
        match rt.client.replace_db(db_key, sources) {
            Ok(()) => {
                rt.last_error = None;
                log::debug!(
                    "SSH agent client mode: now tracking {} key(s)",
                    rt.client.key_count()
                );
            }
            Err(e) => {
                log::warn!("SSH agent client mode: reload failed: {}", e);
                rt.last_error = Some(e);
            }
        }
        return;
    }
    let mut store = rt.store.write().unwrap();
    store.replace_db(db_key, sources);
    log::debug!("SSH agent: now serving {} key(s)", store.len());
}

// Removes (and zeroizes) the agent's keys for one database (lock / close).
// No-op when the agent isn't running.
pub(crate) fn clear_keys_for_db(db_key: &str) {
    let mut rt = runtime().lock().unwrap();
    if !is_running(&rt) {
        return;
    }
    if rt.mode == Some(SshAgentMode::Client) {
        match rt.client.remove_db(db_key) {
            Ok(()) => {
                rt.last_error = None;
                log::debug!(
                    "SSH agent client mode: now tracking {} key(s)",
                    rt.client.key_count()
                );
            }
            Err(e) => {
                log::warn!("SSH agent client mode: remove failed: {}", e);
                rt.last_error = Some(e);
            }
        }
        return;
    }
    let mut store = rt.store.write().unwrap();
    store.remove_db(db_key);
    log::debug!("SSH agent: now serving {} key(s)", store.len());
}

// Clears every key (app quit). Stops the listener too.
pub(crate) fn clear_all_keys() {
    let _ = stop();
    // Deny any sign requests still parked on a confirmation dialog.
    let pending: Vec<_> = pending_confirms().lock().unwrap().drain().collect();
    for (_, tx) in pending {
        let _ = tx.send(false);
    }
}

// Raises the allow/deny dialog for a "Require Confirmation" key and waits for the
// user's answer (auto-denying after a timeout). Called from `Session::sign`
// while no store lock is held. Returns true only on an explicit allow.
pub(super) async fn request_confirmation(title: String, fingerprint: String) -> bool {
    let request_id = uuid::Uuid::new_v4().to_string();
    let (tx, rx) = oneshot::channel::<bool>();

    pending_confirms()
        .lock()
        .unwrap()
        .insert(request_id.clone(), tx);

    let payload = SignRequestPayload {
        request_id: request_id.clone(),
        title,
        fingerprint,
    };

    log::info!(
        "SSH agent: emitting sign-confirm request {} to the UI",
        request_id
    );

    // Emit to the UI. If emit fails there is no dialog to answer, so clean up
    // and deny.
    if let Err(e) = AppState::global_app_handle().emit(SSH_AGENT_SIGN_REQUEST_EVENT, payload) {
        log::error!("SSH agent: failed to emit sign-confirm event: {}", e);
        pending_confirms().lock().unwrap().remove(&request_id);
        return false;
    }

    let outcome = tokio::time::timeout(Duration::from_secs(CONFIRM_TIMEOUT_SECS), rx).await;

    // Always drop the sender from the registry (covers the timeout path, where
    // the answer command never removed it).
    pending_confirms().lock().unwrap().remove(&request_id);

    match outcome {
        Ok(Ok(allow)) => allow,
        // Timed out, or the sender was dropped without an answer -> deny.
        _ => {
            log::info!("SSH agent: sign confirmation timed out or was abandoned; denying");
            false
        }
    }
}

// Delivers the user's allow/deny answer to the parked sign request. Called from
// the `ssh_agent_sign_confirm_result` command.
pub(crate) fn submit_confirmation(request_id: &str, allow: bool) {
    if let Some(tx) = pending_confirms().lock().unwrap().remove(request_id) {
        let _ = tx.send(allow);
    } else {
        log::warn!(
            "SSH agent: confirmation answer for unknown/expired request {}",
            request_id
        );
    }
}
