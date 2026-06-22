// Correct Pageant protocol implementation.
//
// pageant 0.0.3 bug: it copies TOKEN_USER out of a local Vec<u8> buffer, then the buffer
// is dropped before SetSecurityDescriptorOwner is called — making user.User.Sid a dangling
// pointer → ERROR_NOACCESS (0x800703E6).
//
// Fix: pass NULL security attributes to CreateFileMappingW. Both our app and Pageant run
// in the same user session, so the default security descriptor is sufficient.

use std::mem::size_of;

use windows::Win32::Foundation::{CloseHandle, HWND, INVALID_HANDLE_VALUE, LPARAM, WPARAM};
use windows::Win32::System::DataExchange::COPYDATASTRUCT;
use windows::Win32::System::Memory::{
    CreateFileMappingW, MapViewOfFile, UnmapViewOfFile, FILE_MAP_WRITE, PAGE_READWRITE,
};
use windows::Win32::UI::WindowsAndMessaging::{FindWindowW, SendMessageA, WM_COPYDATA};

const AGENT_COPYDATA_ID: usize = 0x804E50BA;
pub(super) const AGENT_MAX_MSGLEN: usize = 8192;

fn find_pageant_window() -> Option<HWND> {
    use windows::core::HSTRING;
    let class = HSTRING::from("Pageant");
    match unsafe { FindWindowW(&class, &class) } {
        Ok(hwnd) if !hwnd.is_invalid() => Some(hwnd),
        _ => None,
    }
}

pub fn is_pageant_running() -> bool {
    find_pageant_window().is_some()
}

/// Send one complete SSH agent message to Pageant, return the response.
/// `msg` must include the 4-byte big-endian length prefix (i.e. the full wire frame).
pub fn query(cookie: u64, msg: &[u8]) -> Result<Vec<u8>, String> {
    use windows::core::HSTRING;

    if msg.len() > AGENT_MAX_MSGLEN {
        return Err(format!("msg too large ({} > {})", msg.len(), AGENT_MAX_MSGLEN));
    }

    let hwnd = find_pageant_window().ok_or_else(|| "Pageant window not found".to_string())?;

    let map_name = format!("PageantRequest{}", cookie);

    let filemap = unsafe {
        CreateFileMappingW(
            INVALID_HANDLE_VALUE,
            None, // NULL = default SD for current user; avoids dangling-SID bug in pageant crate
            PAGE_READWRITE,
            0,
            AGENT_MAX_MSGLEN as u32,
            &HSTRING::from(map_name.as_str()),
        )
    }
    .map_err(|e| format!("CreateFileMappingW failed: {:?}", e))?;

    if filemap.is_invalid() {
        return Err("CreateFileMappingW returned invalid handle".to_string());
    }

    let view = unsafe { MapViewOfFile(filemap, FILE_MAP_WRITE, 0, 0, 0) };
    if view.Value.is_null() {
        unsafe { let _ = CloseHandle(filemap); }
        return Err("MapViewOfFile failed (null view)".to_string());
    }

    // Write SSH agent message into shared memory
    unsafe {
        std::ptr::copy_nonoverlapping(msg.as_ptr(), view.Value as *mut u8, msg.len());
    }

    // Notify Pageant via WM_COPYDATA; lpData = ANSI map name
    let mut name_bytes = map_name.into_bytes();
    name_bytes.push(0);
    let cds = COPYDATASTRUCT {
        dwData: AGENT_COPYDATA_ID,
        cbData: name_bytes.len() as u32,
        lpData: name_bytes.as_ptr() as *mut _,
    };
    let send_result = unsafe {
        SendMessageA(
            hwnd,
            WM_COPYDATA,
            WPARAM(size_of::<COPYDATASTRUCT>()),
            LPARAM(&cds as *const _ as isize),
        )
    };

    if send_result.0 == 0 {
        unsafe {
            let _ = UnmapViewOfFile(view);
            let _ = CloseHandle(filemap);
        }
        return Err("Pageant did not handle WM_COPYDATA (returned 0)".to_string());
    }

    // Read response: 4-byte big-endian length followed by body
    let len_bytes = unsafe {
        let p = view.Value as *const u8;
        [*p, *p.add(1), *p.add(2), *p.add(3)]
    };
    let body_len = u32::from_be_bytes(len_bytes) as usize;

    let mut result = Vec::with_capacity(4 + body_len);
    result.extend_from_slice(&len_bytes);

    if body_len > 0 && 4 + body_len <= AGENT_MAX_MSGLEN {
        let body = unsafe {
            std::slice::from_raw_parts((view.Value as *const u8).add(4), body_len)
        };
        result.extend_from_slice(body);
    }

    unsafe {
        let _ = UnmapViewOfFile(view);
        let _ = CloseHandle(filemap);
    }

    Ok(result)
}
