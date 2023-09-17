import Foundation
import Cocoa
import SwiftRs


@_cdecl("square_number")
public func squareNumber(number: Int) -> Int {
    return 2 * number * number
}

let logger = OkpLogger(tag: "FFI Call")

@_cdecl("auto_type_activate_window")
public func activateWindow(_ pid:Int32) {
    //Int32(pid)
    if let app = NSRunningApplication(processIdentifier:pid ) {
        app.activate(options:[.activateIgnoringOtherApps])
    } else {
        logger.error("Could not find the application for the passed pid \(pid)")
    }
    
}


@_cdecl("auto_type_window_titles")
public func windowTitles() -> SRObjectArray {
    
    // See https://stackoverflow.com/questions/5286274/front-most-window-using-cgwindowlistcopywindowinfo
    // kCGWindowLayer should be 0 for for application but the menubar, the dock, the services menu, the Desktop, etc
    // will have non zero kCGWindowLayer value
    
    //win["kCGWindowName"] is nil as the app needs Screen capturing permission. This means the app needs to be signed
    // See
    // https://stackoverflow.com/questions/59337022/enabling-screen-recording-api-in-catalina-kcgwindowname
    // https://stackoverflow.com/questions/59197213/macos-catalina-screen-recording-permission
    // https://developer.apple.com/forums/thread/126860

    // Need to add manually add Visual Studio Code (during development) to "Prviacy & Security -> Screen Recording" to get non nil values
    // for kCGWindowName
        
    // See https://stackoverflow.com/a/65423834
    // This call ensures that we get the Screen capturing permission - shows a dialog prompting user to give
    let b = CGRequestScreenCaptureAccess()
    
    
    let window  = CGWindowListCopyWindowInfo([.optionOnScreenOnly,.excludeDesktopElements], kCGNullWindowID) as! [[String: Any]]
    
    var infos:[WindowDetail] = []
    for win in window {
        // Note sysApp is Optional value
        let sysApp = win["kCGWindowLayer"]

        if (sysApp! as! Int == 0) {
            print("Window owner name \(win["kCGWindowOwnerName"]!) ,  Window name \(win["kCGWindowName"]) , pid \(win["kCGWindowOwnerPID"])  ")
            let pid = win["kCGWindowOwnerPID"] as? Int
            infos.append(WindowDetail(win["kCGWindowOwnerName"] as? String,
                                      win["kCGWindowName"] as? String,
                                      pid ?? -1
                                     ))

        }
    }
    
    return SRObjectArray(infos)
}

class WindowDetail: NSObject {
    var name: SRString?
    var owner: SRString?
    var pid: Int

    init(_ owner: String?, _ name: String?, _ pid:Int) {
        
        if name != nil {
            self.name = SRString(name! )
        }
        
        if owner != nil {
            self.owner = SRString(owner!)
        }
        self.pid = pid
    }
}


