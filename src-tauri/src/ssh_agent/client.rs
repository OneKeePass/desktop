use std::collections::HashMap;

use onekeepass_core::db_service::ssh_agent::SshAgentKeySource;
use ssh_agent_lib::agent::Session;
use ssh_agent_lib::proto::{
    AddIdentity, AddIdentityConstrained, KeyConstraint, PrivateCredential, PublicCredential,
    RemoveIdentity,
};

use crate::app_preference::SshAgentClientTransport;

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
    // Which external agent we target (Windows only; ignored on unix). Fixed for
    // the lifetime of one start/stop cycle so add and remove use the same agent.
    transport_choice: SshAgentClientTransport,
}

impl ClientRuntime {
    pub(crate) fn new() -> Self {
        Self::default()
    }

    pub(crate) fn start(
        &mut self,
        sources: Vec<SshAgentKeySource>,
        transport_choice: SshAgentClientTransport,
    ) -> Result<(), String> {
        self.stop()?;
        self.transport_choice = transport_choice;
        let transport = transport_name(&self.transport_choice)?;
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

        // The external agent dedupes by public key with no refcount, so a key
        // shared by several open databases is a single entry in it. Removing it
        // here when one database closes would yank it out from under the others
        // that still need it. So skip any key still tracked by another open db;
        // the removed map entry above already excludes the closing db itself.
        let still_needed = |fingerprint: &str| {
            self.added_by_db
                .values()
                .any(|ids| ids.iter().any(|t| t.fingerprint == fingerprint))
        };

        let transport = self.transport_choice.clone();
        let mut first_error = None;
        for id in identities {
            if still_needed(&id.fingerprint) {
                log::debug!(
                    "SSH agent client mode: keeping identity '{}' ({}); still used by another open db",
                    id.comment,
                    id.fingerprint
                );
                continue;
            }

            let credential = id.credential.clone();
            let transport = transport.clone();
            match run_agent_client(async move {
                let mut client = connect_client(transport).await?;
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

    // Count of distinct keys handed to the external agent. A key shared by
    // several open databases is one entry in the agent, so it is counted once
    // here (deduped by fingerprint) to match what `ssh-add -l` reports.
    pub(crate) fn key_count(&self) -> usize {
        let mut seen = std::collections::HashSet::new();
        for ids in self.added_by_db.values() {
            for t in ids {
                seen.insert(t.fingerprint.as_str());
            }
        }
        seen.len()
    }

    pub(crate) fn transport(&self) -> Option<String> {
        self.transport.clone()
    }

    fn add_sources(&mut self, sources: Vec<SshAgentKeySource>) -> Result<(), String> {
        let transport = self.transport_choice.clone();
        let mut first_error = None;

        for src in sources {
            match decode_identity(&src) {
                Ok(identity) => match add_identity(identity, transport.clone()) {
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

fn add_identity(
    identity: DecodedIdentity,
    transport: SshAgentClientTransport,
) -> Result<TrackedIdentity, String> {
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
        let mut client = connect_client(transport).await?;
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

// macOS / Linux: the client always targets the user's `$SSH_AUTH_SOCK`; the
// Windows-only transport choice is ignored here.
#[cfg(unix)]
type ClientStream = tokio::net::UnixStream;

#[cfg(unix)]
async fn connect_client(
    _transport: SshAgentClientTransport,
) -> Result<ssh_agent_lib::client::Client<ClientStream>, String> {
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
fn transport_name(_transport: &SshAgentClientTransport) -> Result<String, String> {
    std::env::var("SSH_AUTH_SOCK")
        .map(|path| format!("SSH_AUTH_SOCK={path}"))
        .map_err(|_| "SSH_AUTH_SOCK is not set for this app process".to_string())
}

// Windows: the user picks the target agent (OpenSSH named pipe or Pageant). Both
// stream types are unified behind one enum so a single `Client<ClientStream>`
// serves either.
#[cfg(windows)]
type ClientStream = WinClientStream;

#[cfg(windows)]
enum WinClientStream {
    OpenSsh(tokio::net::windows::named_pipe::NamedPipeClient),
    Pageant(win_pageant_client::PageantClientStream),
}

#[cfg(windows)]
impl std::fmt::Debug for WinClientStream {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::OpenSsh(_) => f.write_str("WinClientStream::OpenSsh"),
            Self::Pageant(_) => f.write_str("WinClientStream::Pageant"),
        }
    }
}

#[cfg(windows)]
mod win_pageant_client {
    use std::ffi::{c_void, CString};
    use std::io::{Error, ErrorKind, IoSlice};
    use std::pin::Pin;
    use std::ptr::null_mut;
    use std::task::{Context, Poll};

    use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt, DuplexStream, ReadBuf};
    use windows_sys::Win32::Foundation::{
        CloseHandle, GetLastError, HANDLE, INVALID_HANDLE_VALUE,
    };
    use windows_sys::Win32::System::DataExchange::COPYDATASTRUCT;
    use windows_sys::Win32::System::Memory::{
        CreateFileMappingA, MapViewOfFile, UnmapViewOfFile, FILE_MAP_WRITE,
        MEMORY_MAPPED_VIEW_ADDRESS, PAGE_READWRITE,
    };
    use windows_sys::Win32::System::Threading::GetCurrentThreadId;
    use windows_sys::Win32::UI::WindowsAndMessaging::{FindWindowA, SendMessageA, WM_COPYDATA};

    const AGENT_COPYDATA_ID: usize = 0x804E_50BA;
    const AGENT_MAX_MSGLEN: usize = 8192;
    const PAGEANT_NAME: &[u8] = b"Pageant\0";

    pub(super) struct PageantClientStream {
        stream: DuplexStream,
    }

    impl PageantClientStream {
        pub(super) fn new() -> Result<Self, String> {
            if !is_pageant_running() {
                return Err("Pageant window was not found".into());
            }

            let (one, mut two) = tokio::io::duplex(AGENT_MAX_MSGLEN * 2);
            tokio::spawn(async move {
                let mut buf = Vec::<u8>::new();
                let mut chunk = [0u8; 4096];

                loop {
                    let n = two.read(&mut chunk).await?;
                    if n == 0 {
                        break;
                    }
                    buf.extend_from_slice(&chunk[..n]);

                    while let Some(frame_len) = next_frame_len(&buf) {
                        if buf.len() < frame_len {
                            break;
                        }
                        let request = buf.drain(..frame_len).collect::<Vec<_>>();
                        match query_pageant(&request) {
                            Ok(response) => two.write_all(&response).await?,
                            Err(e) => {
                                log::warn!("SSH agent client mode: Pageant request failed: {e}");
                                return Err(Error::new(ErrorKind::Other, e));
                            }
                        }
                    }
                }

                Ok::<(), Error>(())
            });

            Ok(Self { stream: one })
        }
    }

    impl AsyncRead for PageantClientStream {
        fn poll_read(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
            buf: &mut ReadBuf<'_>,
        ) -> Poll<Result<(), Error>> {
            Pin::new(&mut self.stream).poll_read(cx, buf)
        }
    }

    impl AsyncWrite for PageantClientStream {
        fn poll_write(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
            buf: &[u8],
        ) -> Poll<Result<usize, Error>> {
            Pin::new(&mut self.stream).poll_write(cx, buf)
        }

        fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), Error>> {
            Pin::new(&mut self.stream).poll_flush(cx)
        }

        fn poll_write_vectored(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
            bufs: &[IoSlice<'_>],
        ) -> Poll<Result<usize, Error>> {
            Pin::new(&mut self.stream).poll_write_vectored(cx, bufs)
        }

        fn is_write_vectored(&self) -> bool {
            self.stream.is_write_vectored()
        }

        fn poll_shutdown(
            mut self: Pin<&mut Self>,
            cx: &mut Context<'_>,
        ) -> Poll<Result<(), Error>> {
            Pin::new(&mut self.stream).poll_shutdown(cx)
        }
    }

    fn next_frame_len(buf: &[u8]) -> Option<usize> {
        if buf.len() < 4 {
            return None;
        }
        let payload_len = u32::from_be_bytes([buf[0], buf[1], buf[2], buf[3]]) as usize;
        Some(4 + payload_len)
    }

    fn is_pageant_running() -> bool {
        unsafe { FindWindowA(PAGEANT_NAME.as_ptr(), PAGEANT_NAME.as_ptr()) != 0 }
    }

    fn query_pageant(request: &[u8]) -> Result<Vec<u8>, String> {
        if request.len() > AGENT_MAX_MSGLEN {
            return Err(format!(
                "request is too large for Pageant ({} bytes)",
                request.len()
            ));
        }

        let hwnd = unsafe { FindWindowA(PAGEANT_NAME.as_ptr(), PAGEANT_NAME.as_ptr()) };
        if hwnd == 0 {
            return Err("Pageant window was not found".into());
        }

        let map_name = unsafe { format!("SSHAgentRequest{:08x}", GetCurrentThreadId()) };
        let map_name_c = CString::new(map_name.clone())
            .map_err(|_| "Pageant mapping name contains an interior NUL".to_string())?;

        let mapping = unsafe {
            CreateFileMappingA(
                INVALID_HANDLE_VALUE,
                null_mut(),
                PAGE_READWRITE,
                0,
                AGENT_MAX_MSGLEN as u32,
                map_name_c.as_ptr() as *const u8,
            )
        };
        if mapping == 0 {
            return Err(format!("CreateFileMappingA failed: {}", last_error()));
        }

        let result = unsafe { query_pageant_with_mapping(hwnd, mapping, &map_name_c, request) };
        unsafe {
            CloseHandle(mapping);
        }
        result
    }

    unsafe fn query_pageant_with_mapping(
        hwnd: isize,
        mapping: HANDLE,
        map_name: &CString,
        request: &[u8],
    ) -> Result<Vec<u8>, String> {
        let view = MapViewOfFile(mapping, FILE_MAP_WRITE, 0, 0, AGENT_MAX_MSGLEN);
        if view.Value.is_null() {
            return Err(format!("MapViewOfFile failed: {}", last_error()));
        }

        std::ptr::copy_nonoverlapping(request.as_ptr(), view.Value as *mut u8, request.len());

        let copy_data = COPYDATASTRUCT {
            dwData: AGENT_COPYDATA_ID,
            cbData: map_name.as_bytes_with_nul().len() as u32,
            lpData: map_name.as_ptr() as *mut c_void,
        };

        let send_result = SendMessageA(
            hwnd,
            WM_COPYDATA,
            0,
            &copy_data as *const _ as isize,
        );

        let response = if send_result == 0 {
            Err("Pageant returned no response".to_string())
        } else {
            read_pageant_response(view)
        };

        UnmapViewOfFile(view);
        response
    }

    unsafe fn read_pageant_response(view: MEMORY_MAPPED_VIEW_ADDRESS) -> Result<Vec<u8>, String> {
        let header = std::slice::from_raw_parts(view.Value as *const u8, 4);
        let payload_len = u32::from_be_bytes([header[0], header[1], header[2], header[3]]) as usize;
        let total_len = 4 + payload_len;
        if total_len > AGENT_MAX_MSGLEN {
            return Err(format!("Pageant response too large ({total_len} bytes)"));
        }

        let response = std::slice::from_raw_parts(view.Value as *const u8, total_len);
        Ok(response.to_vec())
    }

    fn last_error() -> u32 {
        unsafe { GetLastError() }
    }
}

#[cfg(windows)]
impl tokio::io::AsyncRead for WinClientStream {
    fn poll_read(
        self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
        buf: &mut tokio::io::ReadBuf<'_>,
    ) -> std::task::Poll<std::io::Result<()>> {
        match self.get_mut() {
            Self::OpenSsh(s) => tokio::io::AsyncRead::poll_read(std::pin::Pin::new(s), cx, buf),
            Self::Pageant(s) => tokio::io::AsyncRead::poll_read(std::pin::Pin::new(s), cx, buf),
        }
    }
}

#[cfg(windows)]
impl tokio::io::AsyncWrite for WinClientStream {
    fn poll_write(
        self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
        buf: &[u8],
    ) -> std::task::Poll<std::io::Result<usize>> {
        match self.get_mut() {
            Self::OpenSsh(s) => tokio::io::AsyncWrite::poll_write(std::pin::Pin::new(s), cx, buf),
            Self::Pageant(s) => tokio::io::AsyncWrite::poll_write(std::pin::Pin::new(s), cx, buf),
        }
    }

    fn poll_flush(
        self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
    ) -> std::task::Poll<std::io::Result<()>> {
        match self.get_mut() {
            Self::OpenSsh(s) => tokio::io::AsyncWrite::poll_flush(std::pin::Pin::new(s), cx),
            Self::Pageant(s) => tokio::io::AsyncWrite::poll_flush(std::pin::Pin::new(s), cx),
        }
    }

    fn poll_shutdown(
        self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
    ) -> std::task::Poll<std::io::Result<()>> {
        match self.get_mut() {
            Self::OpenSsh(s) => tokio::io::AsyncWrite::poll_shutdown(std::pin::Pin::new(s), cx),
            Self::Pageant(s) => tokio::io::AsyncWrite::poll_shutdown(std::pin::Pin::new(s), cx),
        }
    }
}

#[cfg(windows)]
async fn connect_client(
    transport: SshAgentClientTransport,
) -> Result<ssh_agent_lib::client::Client<ClientStream>, String> {
    let stream = match transport {
        SshAgentClientTransport::OpenSsh => {
            use tokio::net::windows::named_pipe::ClientOptions;
            let pipe = ClientOptions::new().open(super::pipe::PIPE_PATH).map_err(|e| {
                format!(
                    "connect to OpenSSH agent pipe '{}' failed: {e}",
                    super::pipe::PIPE_PATH
                )
            })?;
            WinClientStream::OpenSsh(pipe)
        }
        SshAgentClientTransport::Pageant => {
            let pageant = win_pageant_client::PageantClientStream::new()
                .map_err(|e| format!("connect to Pageant failed: {e}"))?;
            WinClientStream::Pageant(pageant)
        }
    };
    Ok(ssh_agent_lib::client::Client::new(stream))
}

#[cfg(windows)]
fn transport_name(transport: &SshAgentClientTransport) -> Result<String, String> {
    Ok(match transport {
        SshAgentClientTransport::OpenSsh => format!("OpenSSH pipe {}", super::pipe::PIPE_PATH),
        SshAgentClientTransport::Pageant => "Pageant".to_string(),
    })
}

#[cfg(not(any(unix, windows)))]
async fn connect_client(
    _transport: SshAgentClientTransport,
) -> Result<ssh_agent_lib::client::Client<ClientStream>, String> {
    Err("SSH agent client mode is not supported on this platform".into())
}

#[cfg(not(any(unix, windows)))]
fn transport_name(_transport: &SshAgentClientTransport) -> Result<String, String> {
    Err("SSH agent client mode is not supported on this platform".into())
}
