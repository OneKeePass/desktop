import SwiftRs

import LocalAuthentication


let logger = OkpLogger(tag: "FFI Call")

@_cdecl("available_biometric_type")
func availableBiometricType() -> Int {
    let localAuthenticationContext = LAContext()
    var authorizationError: NSError?
    var supportedType:Int?
    
    if localAuthenticationContext.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authorizationError) {
        switch localAuthenticationContext.biometryType {
        case .faceID:
            logger.info("Supported Biometric type is: faceID")
            supportedType = 2
        case .touchID:
            logger.info("Supported Biometric type is: touchID")
            supportedType = 1
        case .none:
            logger.info("No biometeric")
            supportedType = 0
        @unknown default:
            logger.info("@unknown biometeric")
            supportedType = 0
        }
    }
    
    if authorizationError != nil {
        logger.error("authorizationError is \(String(describing: authorizationError))")
        return 0
    }
    
    return supportedType ?? 0
}

@_cdecl("biometric_authentication")
func biometricAuthentication() -> Bool {
    let localAuthenticationContext = LAContext()
    let reason = "unlock the database"  // Prefix "OneKeePass is trying to " is added by os this text
    
    let sem = DispatchSemaphore(value:0)
    var authenticated:Bool?
    
    localAuthenticationContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, evaluationError in
        if success {
            authenticated = success
        } else {
            logger.error("Error \(evaluationError!)")
            if let errorObj = evaluationError {
                let messageToDisplay = getErrorDescription(errorCode: errorObj._code)
                logger.error(messageToDisplay)
            }
            authenticated = false
        }
        sem.signal()
    }
    
    sem.wait()
    return authenticated ?? false
}

func getErrorDescription(errorCode: Int) -> String {
    switch errorCode {
    case LAError.authenticationFailed.rawValue:
        return "Authentication was not successful, because user failed to provide valid credentials."
        
    case LAError.appCancel.rawValue:
        return "Authentication was canceled by application (e.g. invalidate was called while authentication was in progress)."
        
    case LAError.invalidContext.rawValue:
        return "LAContext passed to this call has been previously invalidated."
        
    case LAError.notInteractive.rawValue:
        return "Authentication failed, because it would require showing UI which has been forbidden by using interactionNotAllowed property."
        
    case LAError.passcodeNotSet.rawValue:
        return "Authentication could not start, because passcode is not set on the device."
        
    case LAError.systemCancel.rawValue:
        return "Authentication was canceled by system (e.g. another application went to foreground)."
        
    case LAError.userCancel.rawValue:
        return "Authentication was canceled by user (e.g. tapped Cancel button)."
        
    case LAError.userFallback.rawValue:
        return "Authentication was canceled, because the user tapped the fallback button (Enter Password)."
        
    default:
        return "Error code \(errorCode) not found"
    }
}

// TODO: Remove
// --------------------------------------------------------

/*
//import AppKit
@_cdecl("get_file_thumbnail_base64")
func getFileThumbnailBase64(path: SRString) -> SRString {
    let path = path.toString();
    
    let image = NSWorkspace.shared.icon(forFile: path)
    let bitmap = NSBitmapImageRep(data: image.tiffRepresentation!)!.representation(using: .png, properties: [:])!
    
    return SRString(bitmap.base64EncodedString())
}

class Volume: NSObject {
    var name: SRString
    var path: SRString
    var total_capacity: Int
    var available_capacity: Int
    var is_removable: Bool
    var is_ejectable: Bool
    var is_root_filesystem: Bool
    
    public init(name: String, path: String, total_capacity: Int, available_capacity: Int, is_removable: Bool, is_ejectable: Bool, is_root_filesystem: Bool) {
        self.name = SRString(name);
        self.path = SRString(path);
        self.total_capacity = total_capacity
        self.available_capacity = available_capacity
        self.is_removable = is_removable
        self.is_ejectable = is_ejectable
        self.is_root_filesystem = is_root_filesystem
    }
}

@_cdecl("get_mounts")
func getMounts() -> SRObjectArray {
    let keys: [URLResourceKey] = [
        .volumeNameKey,
        .volumeIsRemovableKey,
        .volumeIsEjectableKey,
        .volumeTotalCapacityKey,
        .volumeAvailableCapacityKey,
        .volumeIsRootFileSystemKey,
    ]
    
    let paths = autoreleasepool {
        FileManager().mountedVolumeURLs(includingResourceValuesForKeys: keys, options: [])
    }
    
    var validMounts: [Volume] = []
        
    if let urls = paths {
        autoreleasepool {
            for url in urls {
                let components = url.pathComponents
                if components.count == 1 || components.count > 1
                   && components[1] == "Volumes"
                {
                    let metadata = try? url.promisedItemResourceValues(forKeys: Set(keys))
                    
                    let volume = Volume(
                        name: metadata?.volumeName ?? "",
                        path: url.path,
                        total_capacity: metadata?.volumeTotalCapacity ?? 0,
                        available_capacity: metadata?.volumeAvailableCapacity ?? 0,
                        is_removable: metadata?.volumeIsRemovable ?? false,
                        is_ejectable: metadata?.volumeIsEjectable ?? false,
                        is_root_filesystem: metadata?.volumeIsRootFileSystem ?? false
                    )
                    
                    
                    validMounts.append(volume)
                }
            }
        }
    }
    
    return SRObjectArray(validMounts)
}

class Test: NSObject {
    var null: Bool
    
    public init(_ null: Bool)
    {
        self.null = null;
    }
}

@_cdecl("return_nullable")
func returnNullable(null: Bool) -> Test? {
    if (null == true) { return nil }
    
    return Test(null)
}

private let sa  = SecureAccess2()

@_cdecl("save_key_in_secure_store")
func storeKey() {
    sa.storeKey()
}

@_cdecl("read_key_from_secure_store")
func getKey() {
    sa.getKey()
}
*/
