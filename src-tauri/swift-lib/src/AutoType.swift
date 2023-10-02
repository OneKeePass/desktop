import Carbon.HIToolbox
import Cocoa
import Foundation
import SwiftRs

@_cdecl("auto_type_activate_window")
public func activateWindow(_ pid: Int32) -> SRString? {
    print("activateWindow is called")
    
    var ret: SRString?
    
    // This helps so that we can wait for the UI thread
    let sem = DispatchSemaphore(value: 0)
    
    // DispatchQueue.main.async ensures that NSRunningApplication.activate is called
    // inside the UI thread. However in both macOS 13 and macOS 10, this call worked
    // without any issue with or without the async block
    DispatchQueue.main.async {
        if let app = NSRunningApplication(processIdentifier: pid) {
            app.activate(options: [.activateIgnoringOtherApps])
        } else {
            logger.error("Could not find the application for the passed pid \(pid)")
            ret = SRString("Could not find the application for the passed pid \(pid)")
        }
        // Need to call to ensure any thread waiting on this semphore can proceed
        sem.signal()
    }
    // The current thead will be waiting here till main async block completes
    sem.wait()
    // Now we can return to the caller with any 'ret' value
    return ret
}

// This one worked without DispatchQueue.main.async use in both macOS 13 and macOS 10

@_cdecl("auto_type_window_titles")
public func windowTitles() -> SRObjectArray {
    // See https://stackoverflow.com/questions/5286274/front-most-window-using-cgwindowlistcopywindowinfo
    // kCGWindowLayer should be 0 for for application but the menubar, the dock, the services menu, the Desktop, etc
    // will have non zero kCGWindowLayer value
    
    // win["kCGWindowName"] is nil as the app needs Screen capturing permission. This means the app needs to be signed
    // See
    // https://stackoverflow.com/questions/59337022/enabling-screen-recording-api-in-catalina-kcgwindowname
    // https://stackoverflow.com/questions/59197213/macos-catalina-screen-recording-permission
    // https://developer.apple.com/forums/thread/126860

    // Need to add manually add Visual Studio Code (during development) to "Prviacy & Security -> Screen Recording" to get non nil values
    // for kCGWindowName
    
    // See https://stackoverflow.com/a/65423834
    // This call ensures that we get the "Screen capturing permission" by showing a dialog
    // to the user to give the required permission
    if #available(macOS 11.0,*) {
        let b = CGRequestScreenCaptureAccess()
    }
    
    let window = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as! [[String: Any]]
    
    var infos: [WindowDetail] = []
    for win in window {
        // Note sysApp is Optional value
        let sysApp = win["kCGWindowLayer"]

        if sysApp! as! Int == 0 {
            // print("Window owner name \(win["kCGWindowOwnerName"]!) ,  Window name \(win["kCGWindowName"]) , pid \(win["kCGWindowOwnerPID"])  ")
            let pid = win["kCGWindowOwnerPID"] as? Int
            infos.append(WindowDetail(win["kCGWindowOwnerName"] as? String,
                                      win["kCGWindowName"] as? String,
                                      pid ?? -1))
        }
    }
    return SRObjectArray(infos)
}

@_cdecl("auto_type_send_char")
func sendChar(_ charUtf16: UInt16) {
    let ar = Array(arrayLiteral: charUtf16)
    
    // In macOS 10, this call without 'DispatchQueue.main.async' reulted in crash.
    // However it worked in macOS 13 without any issue with or without the async block
    DispatchQueue.main.async {
        ar.withUnsafeBufferPointer { (ptr: UnsafeBufferPointer<UInt16>) in
            // ptr is the pointer to the array’s contiguous storage
            // Need to rebound as UnsafeBufferPointer<UniChar>
            ptr.withMemoryRebound(to: UniChar.self) { (pointer: UnsafeBufferPointer<UniChar>) in
                
                // pointer is of type UnsafeBufferPointer<UniChar>
                // we need UnsafePointer<UniChar> to work with keyboardSetUnicodeString. The  "pointer.baseAddress"  gives that
                // see https://stackoverflow.com/questions/66252570/why-are-swifts-unsafepointer-and-unsafebufferpointer-not-interchangeable
                // UnsafeBufferPointer can be viewed as a tuple of (UnsafePointer, Int),
                // i.e., a pointer to a buffer of elements in memory with a known count
                let gevent1 = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true)
                gevent1?.keyboardSetUnicodeString(stringLength: 1, unicodeString: pointer.baseAddress)
                gevent1?.post(tap: .cgSessionEventTap)
                
                let gevent2 = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false)
                gevent2?.keyboardSetUnicodeString(stringLength: 1, unicodeString: pointer.baseAddress)
                gevent2?.post(tap: .cgSessionEventTap)
            }
        }
    }
}

@_cdecl("auto_type_send_key")
func sendKey(_ keyCode: UInt8) {
    // In macOS 10, this call without 'DispatchQueue.main.async' reulted in crash.
    // However it worked in macOS 13 without any issue with or without the async block
    DispatchQueue.main.async {
        var gevent1 = CGEvent(keyboardEventSource: nil, virtualKey: CGKeyCode(keyCode), keyDown: true)
        gevent1?.post(tap: .cgSessionEventTap)
        
        var gevent2 = CGEvent(keyboardEventSource: nil, virtualKey: CGKeyCode(keyCode), keyDown: false)
        gevent2?.post(tap: .cgSessionEventTap)
    }
}

// Used to send the window detail to the caller. There is a corresponding struct in rust side 
// See windowTitles
class WindowDetail: NSObject {
    var name: SRString?
    var owner: SRString?
    var pid: Int

    init(_ owner: String?, _ name: String?, _ pid: Int) {
        if name != nil {
            self.name = SRString(name!)
        }
        
        if owner != nil {
            self.owner = SRString(owner!)
        }
        self.pid = pid
    }
}

/*
 
 @_cdecl("auto_type_delay")
 public func delay(_ timeInMs:UInt32) {
     usleep(timeInMs * 1000)
 }
 
 @_cdecl("auto_type_activate_window1")
 public func activateWindow(_ pid:Int32) -> SRString? {
     print("activateWindow is called")
     //Int32(pid)
     if let app = NSRunningApplication(processIdentifier:pid) {
         app.activate(options:[.activateIgnoringOtherApps])
         return nil
     } else {
         logger.error("Could not find the application for the passed pid \(pid)")
         return SRString("Could not find the application for the passed pid \(pid)")
     }
 }
 
 */
