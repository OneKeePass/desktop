//
//  Created  on 6/14/23.
//

import Foundation
import SwiftRs

private let ks = KeychainService()

@_cdecl("save_key_in_key_chain")
func storeInKeychain(dbKey: SRString, encKey: SRString) -> Int {
    ks.storeKey(dbKey, encKey)
}

@_cdecl("delete_key_in_key_chain")
func deleteKeyInKeychain(dbKey: SRString) -> Int32 {
    ks.deleteKey(dbKey:dbKey)
}

@_cdecl("get_key_from_key_chain")
func getKeyFromKeyChain(dbKey: SRString) -> SRString {
    ks.getKey(dbKey: dbKey)
}


class KeychainActionResult: NSObject {
    var errorCode: Int
    init(_ errorCode: Int) {
        logger.info("Error \(errorCode) is to be sent")
        self.errorCode = errorCode
    }
}

class KeychainService {
    
    func storeKey(_ dbKey: SRString, _ encKey: SRString) -> Int {
        logger.info("Received args \(dbKey.toString()), \(encKey.toString())")
        
        // The following query parameters need to be used with SecAccessControl
        // kSecUseAuthenticationUI as String: kSecUseAuthenticationUIAllow,
        // kSecAttrAccessControl as String: access as AnyObject,
        // var access: SecAccessControl?
        // var error: Unmanaged<CFError>? //[.biometryCurrentSet, .or, .watch],
        // access = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenUnlockedThisDeviceOnly,.biometryCurrentSet,&error)
        
        // We are not using 'SecAccessControl' based key chain access
        // We are using key chain to store session enc key only
        
        let query: [String: AnyObject] = [
            // kSecAttrService,  kSecAttrAccount, and kSecClass
            // uniquely identify the item to save in Keychain
            // kSecAttrService as String: "OKP-Service' as AnyObject,
            kSecAttrAccount as String: dbKey.toString() as AnyObject,
            kSecClass as String: kSecClassGenericPassword,
            
            kSecAttrSynchronizable as String: kCFBooleanFalse,
            
            // kSecValueData is the item value to save
            kSecValueData as String: encKey.toString() as AnyObject
        ]
        
        let status: OSStatus = SecItemAdd(query as CFDictionary, nil)
        
        // OSStatus is Int32 whereas NSError's code is Int
        // See the extension added to OSStatus to convert OSStatus to NSError
        if let error = status.error {
            logger.error("SecItemAdd error is  \(error)")
            /*
             // SecItemUpdate call did not work though query and attr are created and passed to SecItemUpdate
             // call as per all vaiable documents. The 'SecItemDelete' call followed by 'SecItemAdd' again worked
             // This delete and add again is done now in rust side
             // Leaving the code here for future refernce
             if error.code == errSecDuplicateItem {
                let query: [String: AnyObject] = [kSecAttrService as String: "Jey test service" as AnyObject,
                                                  kSecAttrAccount as String: dbKey.toString() as AnyObject,
                                                  kSecClass as String: kSecClassGenericPassword]
                
                //let attr: [String: AnyObject] = [kSecValueData as String: encKey.toString() as AnyObject]
                //let status: OSStatus = SecItemUpdate(query as CFDictionary, attr as CFDictionary)
                
                let status: OSStatus = SecItemDelete(query as CFDictionary)
                
                
                if let error = status.error {
                    logger.error("SecItemUpdate error is  \(error)")
                    return error.code // KeychainActionResult(error.code)
                } else {
                    logger.info("Store update key is success and returning 0")
                    return 0
                }
            }
             */
            
            return error.code
        }
        
        logger.info("Store key is success and returning nil")
        return 0
    }
        
    
    func getKey(dbKey: SRString) -> SRString {
        let query: [String: AnyObject] = [
                // kSecAttrService,  kSecAttrAccount, and kSecClass
                // uniquely identify the item to read in Keychain
                
                kSecAttrAccount as String: dbKey.toString() as AnyObject,
                kSecClass as String: kSecClassGenericPassword,
                
                // kSecMatchLimitOne indicates keychain should read
                // only the most recent item matching this query
                kSecMatchLimit as String: kSecMatchLimitOne,

                // kSecReturnData is set to kCFBooleanTrue in order
                // to retrieve the data for the item
                // the search returns a CFData instance that holds the actual data in 'itemCopy'.
                kSecReturnData as String: kCFBooleanTrue
            ]

            // SecItemCopyMatching will attempt to copy the item
            // identified by query to the reference itemCopy
            var itemCopy: AnyObject?
            let status = SecItemCopyMatching(
                query as CFDictionary,
                &itemCopy
            )
        
        //IMPORATNT, the caller expects "Error:" prefix in case of error happened here
        
        if let error = status.error {
            logger.error("Get key error is \(error)")
            return SRString("Error:\(error.code)")
        } else {
            // itemCopy is "CFTypeRef  _Nullable *result" in Objective-C or "UnsafeMutablePointer<CFTypeRef?>?" in Swift
            // See https://developer.apple.com/documentation/security/keychain_services/keychain_items/item_return_result_keys
            
            // See https://stackoverflow.com/questions/37539997/save-and-load-from-keychain-swift
            
            // This also works
            // let str = String(decoding: itemCopy as! Data, as: UTF8.self)
            
            // cast the copied value from key chain as NSData using 'if let'
            // If this is successful, retrievedData will be of 'NSData' object and we can get the String from that
            // using NSUTF8StringEncoding
            if let retrievedData = itemCopy as?
                NSData {
                let contentsOfKeychain = NSString(data: retrievedData as Data, encoding: NSUTF8StringEncoding)
                // logger.info("contentsOfKeychain  \(String(describing: contentsOfKeychain))")
                return SRString(contentsOfKeychain as? String ??  "Error:StringConversion error" )
            } else {
                return SRString("Error:Data Retrieval failed")
            }
        }
    }
    
    func deleteKey(dbKey: SRString) -> Int32 {
        let query: [String: AnyObject] = [
                                         kSecAttrAccount as String: dbKey.toString() as AnyObject,
                                          kSecClass as String: kSecClassGenericPassword]
        
        let status: OSStatus = SecItemDelete(query as CFDictionary)
        return status
    }
    
    func copyKey(sourceDbKey: SRString, targetDbKey: SRString) {}
}

extension OSStatus {
    var error: NSError? {
        guard self != errSecSuccess else { return nil }

        let message = SecCopyErrorMessageString(self, nil) as String? ?? "Unknown error"

        return NSError(domain: NSOSStatusErrorDomain, code: Int(self), userInfo: [
            NSLocalizedDescriptionKey: message
        ])
    }
    
    func getErrorCodeDecription() -> String {
        // error code type is Int32
        // See
        switch self {
        case errSecDuplicateItem:
            return "errSecDuplicateItem"
        //case errKCDuplicateItem :
        //    return "errSecDuplicateItem"
        default:
            return "Some error"
        }
    }
}

// SecItemAdd error is  Error Domain=NSOSStatusErrorDomain Code=-25299 "errKCDuplicateItem / errSecDuplicateItem:  / The item already exists." UserInfo={NSLocalizedDescription=The specified item already exists in the keychain.}
