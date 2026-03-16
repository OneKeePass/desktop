// Passkey crypto implementation lives in onekeepass-core so it can be shared
// across desktop, iOS, and Android.  This module re-exports everything the
// existing desktop callers (passkey_db.rs, etc.) reference.
pub use onekeepass_core::passkey_crypto::{
    create_passkey, sign_assertion, PasskeyCreationResult,
};
