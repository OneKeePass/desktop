//
//  SecureAccess.swift
//
//
//  Created  on 5/31/23.
//

import Foundation
import LocalAuthentication

let logger = OkpLogger(tag: "SecureAccess")

class SecureAccess {
    
    func storeKey() {

        logger.info("Calling isBiometricAvailble ....")
        if !self.isBiometricAvailble() {
            logger.info("storeKey - Biometric Authentication is NOT Availble")
            return
        }
        
        
        logger.info("Calling SecAccessControlCreateWithFlags ....")
        
        
        var access: SecAccessControl?
        var error: Unmanaged<CFError>?
        access = SecAccessControlCreateWithFlags(nil,
                                                 kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                                                 //[.biometryCurrentSet, .or, .watch],
                                                 .biometryCurrentSet,
                                                 &error)
        if error != nil {
            logger.error("SecAccessControlCreateWithFlags called and error is \(String(describing: error))")
            return;
        }
        
        logger.info("access control returned is \(access)")
        
        
        
        let query: [String: AnyObject] = [
                // kSecAttrService,  kSecAttrAccount, and kSecClass
                // uniquely identify the item to save in Keychain
                // kSecAttrService as String: "Jey test service" as AnyObject,
                kSecAttrAccount as String: "Jey test account" as AnyObject,
                kSecClass as String: kSecClassGenericPassword,
                
                kSecAttrSynchronizable as String: kCFBooleanFalse,
                kSecUseAuthenticationUI as String: kSecUseAuthenticationUIAllow,
                
                //kSecAttrAccessControl as String: access as AnyObject,
                
                // kSecValueData is the item value to save
                kSecValueData as String: "password" as AnyObject
            ]
        
        let status:OSStatus = SecItemAdd(query as CFDictionary, nil)
        
        if let error = status.error {
            logger.error("SecItemAdd error is  \(error)")
        } else {
            logger.info("SecItemAdd called and status is \(status) and   \(status.description)")
        }
    }
    
    func getKey1() {
        
        let touchPromptMessage = "Please use touch id"
        
        let query: [String: AnyObject] = [
            //kSecAttrService as String: "Jey test service" as AnyObject,
            kSecAttrAccount as String: "Jey test account" as AnyObject,
            kSecClass as String: kSecClassGenericPassword,
            kSecReturnData as String: kCFBooleanTrue,
            kSecUseOperationPrompt as String: touchPromptMessage as AnyObject
        ]
        
        
        var dataTypeRef:CFTypeRef?
        
        let status:OSStatus = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
        
        logger.info("SecItemCopyMatching called and status is \(status)")
        if let error = status.error {
            logger.error("SecItemCopyMatching error is  \(error)")
        } else {
            logger.info("SecItemCopyMatching called and status is \(status) and   \(status.description)")
            logger.info("Got the key data as \(String(describing: dataTypeRef))")
        }
    }
    
    func getKey() {
        
        if !self.isBiometricAvailble() {
            logger.info("getKey - Biometric Authentication is NOT Availble")
            return
        }
        
        let touchPromptMessage = "Please use touch id"
        let localAuthenticationContext = LAContext()
        let reason = "Authentication is required for you to continue"
        
        
        var dataTypeRef:CFTypeRef?
        let sem = DispatchSemaphore(value:0)
        
        localAuthenticationContext.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, evaluationError in
            if success {
                logger.info("Success")
                let query: [String: AnyObject] = [
                    //kSecAttrService as String: "Jey test service" as AnyObject,
                    kSecAttrAccount as String: "Jey test account" as AnyObject,
                    kSecClass as String: kSecClassGenericPassword,
                    kSecReturnData as String: kCFBooleanTrue,
                    kSecUseOperationPrompt as String: touchPromptMessage as AnyObject
                ]
                
                let status:OSStatus = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
                
                logger.info("SecItemCopyMatching called and status is \(status)")
                if let error = status.error {
                    logger.error("SecItemCopyMatching error is  \(error)")
                } else {
                    logger.info("SecItemCopyMatching called and status is \(status) and   \(status.description)")
                    logger.info("Got the key data as \(dataTypeRef as! CFData)")
                   
                    let str = String(decoding: dataTypeRef as! Data, as: UTF8.self)
                    logger.info("Password is \(str)")
                }
                
            } else {
                logger.error("Error \(evaluationError!)")
                if let errorObj = evaluationError {
                    let messageToDisplay = self.getErrorDescription(errorCode: errorObj._code)
                    logger.error(messageToDisplay)
                }
            }
            sem.signal()
        }
        
        sem.wait()
        logger.info("End dataTypeRef returned is \(dataTypeRef.debugDescription)")
        
        
    }
    
    func isBiometricAvailble() -> Bool {
        let localAuthenticationContext = LAContext()
        localAuthenticationContext.localizedFallbackTitle = "Please use your Passcode"

        var authorizationError: NSError?
        
        var supported:Bool

        if localAuthenticationContext.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authorizationError) {
            
            switch localAuthenticationContext.biometryType {
            case .faceID:
                logger.info("Supported Biometric type is: faceID")
            case .touchID:
                logger.info("Supported Biometric type is: touchID")
            case .none:
                logger.info("No biometeric")
            @unknown default:
                logger.info("@unknown biometeric")
            }
        
            
            let biometricType =   localAuthenticationContext.biometryType == LABiometryType.faceID ? "Face ID" : "Touch ID"
            logger.info("Supported Biometric type is: \(biometricType)")
            
            supported = true
              
        } else {
            logger.info("User has not enrolled into using Biometrics")
            supported = false
        }
        
        if authorizationError != nil {
            logger.error("authorizationError is \(String(describing: authorizationError))")
            return false
        } else {
            return supported
        }
    }
    
    func test() {
        let localAuthenticationContext = LAContext()
        localAuthenticationContext.localizedFallbackTitle = "Please use your Passcode"

        var authorizationError: NSError?
        let reason = "Authentication is required for you to continue"

        if localAuthenticationContext.canEvaluatePolicy(LAPolicy.deviceOwnerAuthentication, error: &authorizationError) {
            let biometricType = localAuthenticationContext.biometryType == LABiometryType.faceID ? "Face ID" : "Touch ID"
            print("Supported Biometric type is: \(biometricType)")
            
            localAuthenticationContext.evaluatePolicy(LAPolicy.deviceOwnerAuthentication, localizedReason: reason) { success, evaluationError in
                if success {
                    print("Success")
                } else {
                    print("Error \(evaluationError!)")
                    if let errorObj = evaluationError {
                        let messageToDisplay = self.getErrorDescription(errorCode: errorObj._code)
                        print(messageToDisplay)
                    }
                }
            }
              
        } else {
            print("User has not enrolled into using Biometrics")
        }
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
}


extension OSStatus {

    var error: NSError? {
        guard self != errSecSuccess else { return nil }

        let message = SecCopyErrorMessageString(self, nil) as String? ?? "Unknown error"

        return NSError(domain: NSOSStatusErrorDomain, code: Int(self), userInfo: [
            NSLocalizedDescriptionKey: message])
    }
}

//    func authenticateWithBiometric() -> Bool {
//        let localAuthenticationContext = LAContext()
//        var authorizationError: NSError?
//        let reason = "Authentication is required for you to continue"
//
//        if localAuthenticationContext.canEvaluatePolicy(LAPolicy.deviceOwnerAuthentication, error: &authorizationError) {
//            let biometricType = localAuthenticationContext.biometryType == LABiometryType.faceID ? "Face ID" : "Touch ID"
//            print("Supported Biometric type is: \(biometricType)")
//
//
//
//            localAuthenticationContext.evaluatePolicy(LAPolicy.deviceOwnerAuthentication, localizedReason: reason) { success, evaluationError in
//                if success {
//                    logger.info("Success")
//                    return true
//                } else {
//                    print("Error \(evaluationError!)")
//                    if let errorObj = evaluationError {
//                        let messageToDisplay = self.getErrorDescription(errorCode: errorObj._code)
//                        print(messageToDisplay)
//                    }
//                }
//            }
//
//        } else {
//            logger.info("User has not enrolled into using Biometrics")
//            return false
//        }
//
//    }
//

//        let query: [String: AnyObject] = [kSecClass as String: kSecClassGenericPassword,
//                                    kSecAttrAccount as String: "dbKey" as AnyObject,
//                                    //kSecAttrSynchronizable as String: kCFBooleanFalse!,
//                                    kSecUseAuthenticationUI as String: kSecUseAuthenticationUIAllow,
//                                    kSecValueData as String: "password" as AnyObject,
//                     kSecAttrAccessControl as String: access as AnyObject]
        

//        autoreleasepool {
//            let query = [kSecClass as String: kSecClassGenericPassword,
//                                        kSecAttrAccount as String: "dbKey",
//                                        kSecReturnData as String: kCFBooleanTrue!,
//                         kSecUseOperationPrompt as String: touchPromptMessage] as [String : Any] as  CFDictionary
//
//            var dataTypeRef:CFTypeRef?
//
//            let status = SecItemCopyMatching(query, &dataTypeRef)
//
//            logger.info("SecItemCopyMatching called and status is \(status)")
//
//            logger.info("Got the key data as \(String(describing: dataTypeRef))")
//        }
