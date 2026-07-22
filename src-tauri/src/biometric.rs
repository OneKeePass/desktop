#![allow(dead_code, non_snake_case, non_upper_case_globals)]

#[cfg(target_os = "macos")]
pub fn supported_biometric_type() -> String {
    macos::supported_biometric_type()
}
#[cfg(target_os = "macos")]
pub fn authenticate_with_biometric(db_key: &str) -> bool {
    macos::authenticate_with_biometric(db_key)
}

#[cfg(target_os = "windows")]
pub fn supported_biometric_type() -> String {
    windows_hello::supported_biometric_type()
}
#[cfg(target_os = "windows")]
pub fn authenticate_with_biometric(db_key: &str) -> bool {
    windows_hello::authenticate_with_biometric(db_key)
}

// Linux has no standard hardware-backed biometric path, so it stays on
// master-password re-entry (see Windows-Hello-Unlock-Plan.md).
#[cfg(target_os = "linux")]
pub fn supported_biometric_type() -> String {
    "None".into()
}
#[cfg(target_os = "linux")]
pub fn authenticate_with_biometric(_db_key: &str) -> bool {
    false
}

#[cfg(target_os = "macos")]
mod macos {
    use swift_rs::{swift, Bool, Int};

    swift!(fn available_biometric_type() -> Int);
    swift!(fn biometric_authentication() -> Bool);

    #[non_exhaustive]
    pub enum BiometryType {
        None,
        TouchID,
        FaceID,
    }

    impl BiometryType {
        fn to_string(&self) -> String {
            let s = match *self {
                Self::None => "None",
                Self::FaceID => "FaceID",
                Self::TouchID => "TouchID",
            };
            s.to_string()
        }
    }

    impl From<i32> for BiometryType {
        fn from(value: i32) -> Self {
            match value {
                0 => Self::None,
                1 => Self::TouchID,
                2 => Self::FaceID,
                _ => Self::None,
            }
        }
    }

    pub fn supported_biometric_type() -> String {
        let t = unsafe { available_biometric_type() };
        //debug!("available_biometric_type returned {}", t);
        BiometryType::from(t as i32).to_string()
    }

    pub fn authenticate_with_biometric(_db_key: &str) -> bool {
        let r = unsafe { biometric_authentication() };
        r
    }
}

// Windows Hello re-unlock. This is the parallel of the macOS `LocalAuthentication`
// gate above: it returns a yes/no and does no key work. The database is already
// fully in memory while locked; a successful verification lets the frontend call
// `unlock_kdbx_on_biometric_authentication` (the reveal), exactly as on macOS.
//
// Uses `UserConsentVerifier` (Windows.Security.Credentials.UI) which accepts any
// enrolled Windows Hello verifier - a Hello PIN counts, not only biometric
// hardware. It requires physical presence, so over Remote Desktop it reports an
// unavailable state; that is treated as a normal "no biometric" case and the app
// falls back to the master-password dialog.
#[cfg(target_os = "windows")]
mod windows_hello {
    use log::error;

    use windows::core::HSTRING;
    use windows::Security::Credentials::UI::{
        UserConsentVerificationResult, UserConsentVerifier, UserConsentVerifierAvailability,
    };

    // Reported to the frontend as a non-"None" biometric label so the unlock
    // button appears (see `WINDOWS_HELLO` in constants.cljs).
    const WINDOWS_HELLO: &str = "WindowsHello";
    const NONE: &str = "None";

    pub fn supported_biometric_type() -> String {
        match check_availability() {
            Ok(true) => WINDOWS_HELLO.to_string(),
            Ok(false) => NONE.to_string(),
            Err(e) => {
                error!("Windows Hello availability check failed: {:?}", e);
                NONE.to_string()
            }
        }
    }

    // `Available` requires an enrolled verifier and physical presence. Anything
    // else (DeviceNotPresent over RDP, DeviceBusy, DisabledByPolicy, NotConfigured)
    // is mapped to "not available".
    fn check_availability() -> windows::core::Result<bool> {
        let availability = UserConsentVerifier::CheckAvailabilityAsync()?.get()?;
        Ok(availability == UserConsentVerifierAvailability::Available)
    }

    pub fn authenticate_with_biometric(_db_key: &str) -> bool {
        match request_verification() {
            Ok(verified) => verified,
            Err(e) => {
                error!("Windows Hello verification failed: {:?}", e);
                false
            }
        }
    }

    fn request_verification() -> windows::core::Result<bool> {
        // Message shown in the Windows Hello consent prompt.
        let message = HSTRING::from("Unlock OneKeePass");
        let result = UserConsentVerifier::RequestVerificationAsync(&message)?.get()?;
        Ok(result == UserConsentVerificationResult::Verified)
    }
}
