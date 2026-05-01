// Security-scoped bookmark wrapper around the Swift FFI in
// swift-lib/src/Bookmarks.swift. Used to persist file/folder access across
// app launches under the macOS App Sandbox. Calls are no-ops / Err on
// non-macOS so call sites do not need additional cfg gating.

// Transparent handle around the Int64 returned by Swift. Each successful
// `resolve_and_start` must be paired with exactly one `release` call.
// Releasing twice or releasing an unknown handle is harmless but logged on
// the Swift side.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct BookmarkHandle(i64);

// Creates a base64-encoded security-scoped bookmark for `path`. Caller must
// already have access to the path (typically just after a Tauri file/folder
// dialog returned it). Returns None if the OS refuses to create the bookmark.
pub(crate) fn create(path: &str) -> Option<String> {
    imp::create(path)
}

// Resolves a previously-created bookmark and starts security-scoped access on
// the resulting URL, returning a handle the caller must later pass to `release`.
// If the OS flagged the bookmark as stale, a refreshed base64 blob is returned
// in the second tuple element so the caller can persist it back into storage.
pub(crate) fn resolve_and_start(
    b64: &str,
) -> Result<(BookmarkHandle, Option<String>), &'static str> {
    imp::resolve_and_start(b64)
}

// Releases the scoped access associated with `handle`. After this call the
// handle value is no longer valid.
pub(crate) fn release(handle: BookmarkHandle) {
    imp::release(handle);
}

#[cfg(target_os = "macos")]
mod imp {
    use super::BookmarkHandle;
    use serde::Deserialize;
    use swift_rs::{swift, SRString};

    swift!(fn bookmark_create(path: &SRString) -> SRString);
    swift!(fn bookmark_resolve_and_start(b64: &SRString) -> SRString);
    swift!(fn bookmark_release(handle: i64));

    #[derive(Deserialize)]
    struct ResolveResult {
        handle: i64,
        refreshed_b64: Option<String>,
    }

    pub(super) fn create(path: &str) -> Option<String> {
        let s: SRString = path.into();
        let result = unsafe { bookmark_create(&s) };
        let b64 = result.to_string();
        if b64.is_empty() {
            None
        } else {
            Some(b64)
        }
    }

    pub(super) fn resolve_and_start(
        b64: &str,
    ) -> Result<(BookmarkHandle, Option<String>), &'static str> {
        let s: SRString = b64.into();
        let result = unsafe { bookmark_resolve_and_start(&s) };
        let json = result.to_string();
        if json.is_empty() {
            return Err("bookmark resolve failed (see Console.app for the underlying NSError)");
        }
        let parsed: ResolveResult = serde_json::from_str(&json)
            .map_err(|_| "bookmark resolve returned malformed JSON")?;
        Ok((BookmarkHandle(parsed.handle), parsed.refreshed_b64))
    }

    pub(super) fn release(handle: BookmarkHandle) {
        unsafe { bookmark_release(handle.0) };
    }
}

#[cfg(not(target_os = "macos"))]
mod imp {
    use super::BookmarkHandle;

    pub(super) fn create(_path: &str) -> Option<String> {
        None
    }

    pub(super) fn resolve_and_start(
        _b64: &str,
    ) -> Result<(BookmarkHandle, Option<String>), &'static str> {
        Err("security-scoped bookmarks are macOS-only")
    }

    pub(super) fn release(_handle: BookmarkHandle) {}
}
