import Foundation
import SwiftRs

// Security-scoped bookmark FFI for the macOS App Sandbox.
//
// The Rust side stores opaque Int64 handles returned by `bookmark_resolve_and_start`
// and is responsible for calling `bookmark_release` when access is no longer needed
// (typically when the corresponding database is closed). The handle map is the
// only owner of the resolved URL, so failing to release leaks the scoped grant
// for the lifetime of the process.

private let bookmarksLogger = OkpLogger(tag: "Bookmarks")

private var openAccess: [Int64: URL] = [:]
private var nextHandle: Int64 = 0
private let openAccessLock = NSLock()

// Creates a base64-encoded security-scoped bookmark for a path the app currently
// has access to (typically just after the user picked the file/folder via an
// NSOpenPanel-backed Tauri dialog). Returns an empty SRString on failure.
@_cdecl("bookmark_create")
func bookmarkCreate(path: SRString) -> SRString {
    let pathStr = path.toString()
    let url = URL(fileURLWithPath: pathStr)
    do {
        let data = try url.bookmarkData(
            options: .withSecurityScope,
            includingResourceValuesForKeys: nil,
            relativeTo: nil
        )
        return SRString(data.base64EncodedString())
    } catch {
        bookmarksLogger.error("bookmark_create failed for \(pathStr): \(error)")
        return SRString("")
    }
}

// Resolves a base64-encoded bookmark and starts security-scoped access on the
// resulting URL. Returns a small JSON document the Rust wrapper parses:
//   {"handle": <Int64>, "refreshed_b64": <String|null>}
// Empty SRString on failure. If the OS flagged the bookmark as stale, a fresh
// bookmark is created from the resolved URL and returned in `refreshed_b64`
// for the caller to persist; this avoids permanent staleness over time.
@_cdecl("bookmark_resolve_and_start")
func bookmarkResolveAndStart(b64: SRString) -> SRString {
    let b64Str = b64.toString()
    guard let data = Data(base64Encoded: b64Str) else {
        bookmarksLogger.error("bookmark_resolve_and_start: invalid base64 input")
        return SRString("")
    }
    var isStale: Bool = false
    do {
        let url = try URL(
            resolvingBookmarkData: data,
            options: .withSecurityScope,
            relativeTo: nil,
            bookmarkDataIsStale: &isStale
        )
        let started = url.startAccessingSecurityScopedResource()
        guard started else {
            bookmarksLogger.error("startAccessingSecurityScopedResource returned false for \(url.path)")
            return SRString("")
        }

        var refreshedB64: String? = nil
        if isStale {
            do {
                let refreshed = try url.bookmarkData(
                    options: .withSecurityScope,
                    includingResourceValuesForKeys: nil,
                    relativeTo: nil
                )
                refreshedB64 = refreshed.base64EncodedString()
                bookmarksLogger.info("Stale bookmark refreshed for \(url.path)")
            } catch {
                bookmarksLogger.error("Stale bookmark refresh failed: \(error). Continuing with original.")
            }
        }

        let handle: Int64 = {
            openAccessLock.lock()
            defer { openAccessLock.unlock() }
            nextHandle += 1
            openAccess[nextHandle] = url
            return nextHandle
        }()

        // base64 alphabet plus '=' is JSON-safe — no escaping needed.
        let refreshedJsonValue = refreshedB64.map { "\"\($0)\"" } ?? "null"
        let json = "{\"handle\":\(handle),\"refreshed_b64\":\(refreshedJsonValue)}"
        return SRString(json)
    } catch {
        bookmarksLogger.error("bookmark_resolve_and_start failed: \(error)")
        return SRString("")
    }
}

// Releases the scoped access associated with a previously-issued handle.
// No-op (with an error log) if the handle is unknown — releasing twice is a bug.
@_cdecl("bookmark_release")
func bookmarkRelease(handle: Int64) {
    let url: URL? = {
        openAccessLock.lock()
        defer { openAccessLock.unlock() }
        return openAccess.removeValue(forKey: handle)
    }()
    if let url = url {
        url.stopAccessingSecurityScopedResource()
    } else {
        bookmarksLogger.error("bookmark_release: unknown handle \(handle)")
    }
}
