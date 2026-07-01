// Windows Pageant transport for the SSH agent.
//
// PuTTY-family clients (plink, WinSCP, TortoiseGit, FileZilla) talk to an agent
// not over a socket but by:
//   1. finding a message-only window whose class and title are both `Pageant`,
//   2. creating a named shared file-mapping holding `[u32 BE len][agent request]`,
//   3. sending that window `WM_COPYDATA` with `dwData == 0x804E50BA` and `lpData`
//      pointing at the mapping's name,
//   4. reading the framed response back out of the same mapping once the
//      `SendMessage` returns.
//
// There is no ready-made Rust *server* crate for this (the `pageant` crate is a
// client), so we implement it directly over Win32 via `windows-sys`: register the
// window class, run a dedicated message loop on its own thread, and in the window
// procedure decode the request, run it through the *same* `AgentSession::handle`
// the socket / named-pipe transports use, and write the response back.
//
// Security: before reading a client's mapping we verify the mapping object's
// owner SID matches the current user's SID, exactly as PuTTY's own agent does, so
// another user on the same machine cannot coax us into signing.
//
// `WM_COPYDATA` is synchronous — the client's `SendMessage` blocks until the
// window procedure returns — so the response must be written before we return.
// We therefore run the (possibly confirmation-gated) request to completion inside
// the procedure with `block_on`. Because the message loop lives on its own
// dedicated thread, a slow request only serialises further Pageant requests; it
// never blocks the app's UI thread.

use std::ffi::c_void;
use std::ptr::{null, null_mut};
use std::sync::{Arc, OnceLock, RwLock};

use ssh_agent_lib::agent::Session;
use ssh_agent_lib::proto::{Request, Response};
use ssh_encoding::{Decode, Encode};

use windows_sys::Win32::Foundation::{
    CloseHandle, GetLastError, LocalFree, ERROR_SUCCESS, HANDLE, HWND, LPARAM, LRESULT, PSID,
    WPARAM,
};
use windows_sys::Win32::Security::Authorization::{
    ConvertSidToStringSidW, GetSecurityInfo, SE_KERNEL_OBJECT,
};
use windows_sys::Win32::Security::{
    EqualSid, GetTokenInformation, TokenUser, OWNER_SECURITY_INFORMATION, PSECURITY_DESCRIPTOR,
    TOKEN_QUERY, TOKEN_USER,
};
use windows_sys::Win32::System::DataExchange::COPYDATASTRUCT;
use windows_sys::Win32::System::LibraryLoader::GetModuleHandleW;
use windows_sys::Win32::System::Memory::{
    MapViewOfFile, OpenFileMappingA, UnmapViewOfFile, FILE_MAP_WRITE,
};
use windows_sys::Win32::System::Threading::{GetCurrentProcess, OpenProcessToken};
use windows_sys::Win32::UI::WindowsAndMessaging::{
    CreateWindowExW, DefWindowProcW, DestroyWindow, DispatchMessageW, FindWindowW, GetMessageW,
    PostMessageW, PostQuitMessage, RegisterClassW, TranslateMessage, HWND_MESSAGE, MSG, WM_CLOSE,
    WM_COPYDATA, WM_DESTROY, WNDCLASSW,
};

use super::session::AgentSession;
use super::store::SshAgentStore;

// The magic `dwData` value every Pageant client uses on its WM_COPYDATA.
const AGENT_COPYDATA_ID: usize = 0x804E_50BA;

// Pageant's hard cap on a single request/response. The shared mapping is this
// size; we refuse anything that claims to be larger.
const AGENT_MAX_MSGLEN: usize = 8192;

// Standard Windows object access right needed for GetSecurityInfo(owner). The
// mapping is still mapped with FILE_MAP_WRITE below; this is only for querying
// the security descriptor on the mapping handle.
const READ_CONTROL_ACCESS: u32 = 0x0002_0000;

// The class and window title every Pageant client searches for.
const PAGEANT_NAME: &str = "Pageant";

// The store the window procedure signs from. Set on start, cleared on stop. The
// window procedure is a bare `extern "system"` fn and cannot capture state, so a
// process-global is the natural channel.
static PAGEANT_STORE: OnceLock<RwLock<Option<Arc<RwLock<SshAgentStore>>>>> = OnceLock::new();

fn store_slot() -> &'static RwLock<Option<Arc<RwLock<SshAgentStore>>>> {
    PAGEANT_STORE.get_or_init(|| RwLock::new(None))
}

// Handle returned to the agent runtime so it can tear the Pageant window down on
// stop. `hwnd` is kept as an `isize` (it is one, under windows-sys 0.52) so the
// handle is `Send` and can be parked in the runtime struct.
pub(crate) struct PageantHandle {
    hwnd: isize,
    join: Option<std::thread::JoinHandle<()>>,
}

impl PageantHandle {
    // Closes the Pageant window (which ends its message loop) and joins the
    // thread, then drops the global store reference.
    pub(crate) fn stop(mut self) {
        // Safe: PostMessageW is documented as callable from any thread.
        unsafe {
            PostMessageW(self.hwnd as HWND, WM_CLOSE, 0, 0);
        }
        if let Some(join) = self.join.take() {
            let _ = join.join();
        }
        *store_slot().write().unwrap() = None;
        log::info!("SSH agent: Pageant window stopped");
    }
}

// Starts the Pageant transport: publishes the store, then spawns the dedicated
// message-loop thread and waits for it to report the created window handle.
//
// Refuses to start if another `Pageant`-class window already exists (a real
// Pageant, or a second OneKeePass) — two agents answering for the same window
// class would race for clients.
pub(crate) fn start(store: Arc<RwLock<SshAgentStore>>) -> Result<PageantHandle, String> {
    // Conflict check before we publish anything.
    let existing = unsafe { FindWindowW(wide(PAGEANT_NAME).as_ptr(), wide(PAGEANT_NAME).as_ptr()) };
    if existing != 0 {
        return Err(
            "another Pageant-compatible agent is already running (a Pageant-class \
             window exists); close it to let OneKeePass serve PuTTY clients"
                .into(),
        );
    }

    *store_slot().write().unwrap() = Some(store);

    let (tx, rx) = std::sync::mpsc::channel::<Result<isize, String>>();

    let join = std::thread::Builder::new()
        .name("okp-pageant".into())
        .spawn(move || thread_main(tx))
        .map_err(|e| {
            *store_slot().write().unwrap() = None;
            format!("failed to spawn Pageant thread: {e}")
        })?;

    match rx.recv() {
        Ok(Ok(hwnd)) => {
            log::info!("SSH agent: Pageant window created");
            Ok(PageantHandle {
                hwnd,
                join: Some(join),
            })
        }
        Ok(Err(e)) => {
            let _ = join.join();
            *store_slot().write().unwrap() = None;
            Err(e)
        }
        Err(e) => {
            let _ = join.join();
            *store_slot().write().unwrap() = None;
            Err(format!("Pageant thread did not initialise: {e}"))
        }
    }
}

// Registers the window class, creates the message-only window, reports the result
// back through `tx`, then pumps messages until the window is destroyed.
fn thread_main(tx: std::sync::mpsc::Sender<Result<isize, String>>) {
    unsafe {
        let hinstance = GetModuleHandleW(null());
        let class_name = wide(PAGEANT_NAME);

        let mut wc: WNDCLASSW = std::mem::zeroed();
        wc.lpfnWndProc = Some(wnd_proc);
        wc.hInstance = hinstance;
        wc.lpszClassName = class_name.as_ptr();
        // A class atom of 0 with "class already exists" is fine (we may have
        // registered it on a previous start); any other failure surfaces when
        // CreateWindowExW fails below.
        let _ = RegisterClassW(&wc);

        let title = wide(PAGEANT_NAME);
        let hwnd = CreateWindowExW(
            0,
            class_name.as_ptr(),
            title.as_ptr(),
            0,
            0,
            0,
            0,
            0,
            HWND_MESSAGE,
            0, // hMenu
            hinstance,
            null(),
        );

        if hwnd == 0 {
            let _ = tx.send(Err("failed to create the Pageant window".into()));
            return;
        }

        if tx.send(Ok(hwnd)).is_err() {
            // Receiver gone; nothing will use this window.
            DestroyWindow(hwnd);
            return;
        }

        // Standard Win32 message loop. GetMessageW returns 0 on WM_QUIT (posted
        // by PostQuitMessage in WM_DESTROY), ending the loop.
        let mut msg: MSG = std::mem::zeroed();
        while GetMessageW(&mut msg, 0, 0, 0) > 0 {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }
}

// The window procedure. Only WM_COPYDATA carries agent traffic; WM_CLOSE /
// WM_DESTROY drive an orderly shutdown.
unsafe extern "system" fn wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    match msg {
        WM_COPYDATA => handle_copydata(lparam),
        WM_CLOSE => {
            DestroyWindow(hwnd);
            0
        }
        WM_DESTROY => {
            PostQuitMessage(0);
            0
        }
        _ => DefWindowProcW(hwnd, msg, wparam, lparam),
    }
}

// Handles one Pageant request. Returns 1 (TRUE) on success, 0 on any rejection;
// the client treats a non-TRUE result, or an unchanged mapping, as failure.
unsafe fn handle_copydata(lparam: LPARAM) -> LRESULT {
    let cds = lparam as *const COPYDATASTRUCT;
    if cds.is_null() {
        return 0;
    }
    let cds = &*cds;

    // Reject anything that is not a Pageant agent message.
    if cds.dwData != AGENT_COPYDATA_ID || cds.lpData.is_null() {
        return 0;
    }

    // cds.lpData is a NUL-terminated ASCII string: the name of the file mapping
    // the client created. Open it for read/write (we read the request, write the
    // response into the same mapping).
    let mapping = OpenFileMappingA(
        FILE_MAP_WRITE | READ_CONTROL_ACCESS,
        0,
        cds.lpData as *const u8,
    );
    if mapping == 0 {
        return 0;
    }

    let result = serve_mapping(mapping);
    CloseHandle(mapping);
    result
}

// With the mapping open: verify ownership, map it, run the framed request, and
// write the framed response back. Split out so the mapping handle is always
// closed by the caller.
unsafe fn serve_mapping(mapping: HANDLE) -> LRESULT {
    // Refuse to read a mapping owned by a different user.
    if !mapping_owner_is_current_user(mapping) {
        log::warn!("SSH agent: rejecting Pageant request from a foreign-owned mapping");
        return 0;
    }

    // MapViewOfFile returns a MEMORY_MAPPED_VIEW_ADDRESS wrapping the base
    // pointer; UnmapViewOfFile takes the same wrapper back.
    let view = MapViewOfFile(mapping, FILE_MAP_WRITE, 0, 0, 0);
    if view.Value.is_null() {
        return 0;
    }

    let result = serve_view(view.Value as *mut u8);
    UnmapViewOfFile(view);
    result
}

// `view` points at the start of the shared buffer: `[u32 BE len][request bytes]`,
// at most AGENT_MAX_MSGLEN total. Decode, handle, and overwrite the buffer with
// the framed response.
unsafe fn serve_view(view: *mut u8) -> LRESULT {
    // Read the request length (big-endian) and bounds-check it.
    let len_bytes = std::slice::from_raw_parts(view, 4);
    let req_len = u32::from_be_bytes([len_bytes[0], len_bytes[1], len_bytes[2], len_bytes[3]]) as usize;
    if req_len == 0 || req_len > AGENT_MAX_MSGLEN - 4 {
        return 0;
    }

    let body = std::slice::from_raw_parts(view.add(4), req_len);

    let Some(store) = store_slot().read().unwrap().clone() else {
        // Agent stopped between window creation and this request.
        return 0;
    };

    let Some(response) = process_request(store, body) else {
        return 0;
    };

    // Frame as `[u32 BE resp_len][resp bytes]` and write back, refusing to
    // overflow the client's buffer.
    let total = 4 + response.len();
    if total > AGENT_MAX_MSGLEN {
        log::warn!("SSH agent: Pageant response too large ({total} bytes); dropping");
        return 0;
    }
    let dst = std::slice::from_raw_parts_mut(view, total);
    dst[..4].copy_from_slice(&(response.len() as u32).to_be_bytes());
    dst[4..].copy_from_slice(&response);
    1
}

// Decodes the agent request body, runs it through the shared session handler
// (including the confirmation flow for keys that require it), and returns the
// encoded response body (without the length prefix). `None` on any decode/encode
// failure, which the caller turns into a Pageant failure.
fn process_request(store: Arc<RwLock<SshAgentStore>>, body: &[u8]) -> Option<Vec<u8>> {
    let mut reader = body;
    let request = Request::decode(&mut reader).ok()?;

    let mut session = AgentSession::new(store);
    // SendMessage is synchronous, so we must finish here. block_on runs on this
    // dedicated Pageant thread; the confirmation wait inside parks only this
    // thread, never the UI.
    let response = tauri::async_runtime::block_on(session.handle(request)).unwrap_or(Response::Failure);

    let mut out = Vec::new();
    response.encode(&mut out).ok()?;
    Some(out)
}

// True when the file-mapping object's owner SID equals the current user's SID.
unsafe fn mapping_owner_is_current_user(mapping: HANDLE) -> bool {
    let mut owner_sid: PSID = null_mut();
    let mut sd: PSECURITY_DESCRIPTOR = null_mut();

    log::debug!("SSH agent: Pageant SID check starting for mapping handle {mapping}");

    let rc = GetSecurityInfo(
        mapping,
        SE_KERNEL_OBJECT,
        OWNER_SECURITY_INFORMATION,
        &mut owner_sid,
        null_mut(),
        null_mut(),
        null_mut(),
        &mut sd,
    );
    if rc != ERROR_SUCCESS {
        log::warn!("SSH agent: Pageant GetSecurityInfo failed with code {rc}");
        return false;
    }

    let owner_sid_text = sid_to_string(owner_sid).unwrap_or_else(|| "<unavailable>".into());
    let matches = current_user_sid_equals(owner_sid, &owner_sid_text);
    if !sd.is_null() {
        LocalFree(sd as *mut c_void);
    }
    matches
}

// True when `other` equals the SID of the user owning this process.
unsafe fn current_user_sid_equals(other: PSID, other_text: &str) -> bool {
    let mut token: HANDLE = 0;
    if OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token) == 0 {
        log::warn!(
            "SSH agent: Pageant OpenProcessToken failed with Win32 error {}",
            GetLastError()
        );
        return false;
    }

    // First call sizes the buffer; second fills it.
    let mut needed: u32 = 0;
    GetTokenInformation(token, TokenUser, null_mut(), 0, &mut needed);
    if needed == 0 {
        log::warn!(
            "SSH agent: Pageant TokenUser size lookup returned 0, Win32 error {}",
            GetLastError()
        );
        CloseHandle(token);
        return false;
    }

    let mut buf = vec![0u8; needed as usize];
    let ok = GetTokenInformation(
        token,
        TokenUser,
        buf.as_mut_ptr() as *mut c_void,
        needed,
        &mut needed,
    );

    let matches = if ok != 0 && !buf.is_empty() {
        let token_user = &*(buf.as_ptr() as *const TOKEN_USER);
        let current_sid_text =
            sid_to_string(token_user.User.Sid).unwrap_or_else(|| "<unavailable>".into());
        let matches = EqualSid(token_user.User.Sid, other) != 0;
        log::debug!(
            "SSH agent: Pageant SID check mapping_owner={}, current_user={}, matches={}",
            other_text,
            current_sid_text,
            matches
        );
        matches
    } else {
        log::warn!(
            "SSH agent: Pageant GetTokenInformation(TokenUser) failed with Win32 error {}",
            GetLastError()
        );
        false
    };

    CloseHandle(token);
    matches
}

// Converts a SID to the standard S-1-... string form for diagnostics.
unsafe fn sid_to_string(sid: PSID) -> Option<String> {
    if sid.is_null() {
        return None;
    }

    let mut sid_text_ptr: *mut u16 = null_mut();
    if ConvertSidToStringSidW(sid, &mut sid_text_ptr) == 0 || sid_text_ptr.is_null() {
        return None;
    }

    let mut len = 0usize;
    while *sid_text_ptr.add(len) != 0 {
        len += 1;
    }
    let sid_text = String::from_utf16_lossy(std::slice::from_raw_parts(sid_text_ptr, len));
    LocalFree(sid_text_ptr as *mut c_void);
    Some(sid_text)
}

// Builds a NUL-terminated UTF-16 string for the Win32 wide APIs. The returned
// Vec must outlive the pointer handed to Win32.
fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}
