import AppKit
import Foundation
import SwiftRs

// Hands focus to the app whose window is directly behind OneKeePass, so that
// window comes to the front and OneKeePass stays visible behind it (instead of
// being minimized). Returns false when OneKeePass is not the active app or no
// other app window is on screen; nothing is changed in that case.
//
// Only the owner pid, layer, alpha and bounds of each window are read. Window
// names are not, so unlike auto_type_window_titles this needs no Screen
// Recording permission.
@_cdecl("window_send_to_background")
public func windowSendToBackground() -> Bool {
    var activated = false

    let work = {
        guard NSApp.isActive else {
            return
        }

        let ownPid = ProcessInfo.processInfo.processIdentifier

        // Front-to-back order. Layer 0 is the normal application window layer;
        // the menu bar, Dock, Desktop etc use other layers.
        guard let windows = CGWindowListCopyWindowInfo(
            [.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID
        ) as? [[String: Any]] else {
            return
        }

        for win in windows {
            guard let layer = win[kCGWindowLayer as String] as? Int, layer == 0,
                  let pid = win[kCGWindowOwnerPID as String] as? Int32, pid != ownPid
            else {
                continue
            }

            if let alpha = win[kCGWindowAlpha as String] as? Double, alpha <= 0 {
                continue
            }

            // Skips tiny helper windows some apps keep on screen
            if let boundsDict = win[kCGWindowBounds as String] as? NSDictionary,
               let bounds = CGRect(dictionaryRepresentation: boundsDict),
               bounds.width < 50 || bounds.height < 50 {
                continue
            }

            guard let app = NSRunningApplication(processIdentifier: pid),
                  app.activationPolicy == .regular
            else {
                continue
            }

            if #available(macOS 14.0, *) {
                // From macOS 14 activation is cooperative: the active app yields
                // to the target first, otherwise the request may be ignored
                NSApp.yieldActivation(to: app)
                activated = app.activate()
            } else {
                activated = app.activate(options: [.activateIgnoringOtherApps])
            }
            return
        }
    }

    // AppKit calls must be made on the main thread. The Rust command calling this
    // is async (worker thread), but guard against a main-thread caller so the
    // sync dispatch cannot deadlock.
    if Thread.isMainThread {
        work()
    } else {
        DispatchQueue.main.sync(execute: work)
    }

    if !activated {
        logger.debug("No window found to hand focus to; window left as is")
    }
    return activated
}
