use std::sync::Arc;

use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::app_state;

use crate::browser_service::{
    db_calls,
    key_share::{BrowserServiceTx, SessionStore},
    native_messaging_config,
    passkey_db,
    verifier, SUPPORTED_BROWSERS,
};

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "action")]
pub enum Request {
    // Called first time when the extension app is about to use the OneKeePass app
    Associate {
        client_id: String,
        // Browser extension ID sent by the extension (e.g. "onekeepass@gmail.com" for Firefox).
        // Validated against the known OneKeePass extension IDs before proceeding.
        #[serde(default)]
        extension_id: Option<String>,
    },

    // Session pub key from client for the shared key encryption/decryption
    InitSessionKey {
        association_id: String,
        client_session_pub_key: String,
    },

    // The extension side request to get all entries that match an url
    EnabledDatabaseMatchedEntryList {
        association_id: String,
        request_id: String,
        form_url: String,
    },

    // This is a request to get an entry details for user selected entry from the previous matched entries
    SelectedEntry {
        association_id: String,
        request_id: String,
        db_key: String,
        entry_uuid: Uuid,
    },

    // ── Passkey: pre-creation queries ────────────────────────────────────────

    // Step A of passkey creation: fetch the list of open, browser-enabled
    // databases so the user can choose where to store the new passkey.
    GetOpenedDatabasesForPasskey {
        association_id: String,
        request_id: String,
    },

    // Step B of passkey creation: fetch user-visible groups in the chosen database.
    GetDbGroupsForPasskey {
        association_id: String,
        request_id: String,
        db_key: String,
    },

    // Step C of passkey creation: fetch entries in the chosen group.
    GetDbGroupEntriesForPasskey {
        association_id: String,
        request_id: String,
        db_key: String,
        group_uuid: String,
    },

    // ── Passkey: registration ─────────────────────────────────────────────────

    // Final passkey creation step. User has selected a database, group, and
    // entry target; the desktop generates the key pair, builds WebAuthn
    // structures, stores the key material in KDBX, and returns the credential JSON.
    CreatePasskey {
        association_id: String,
        request_id: String,
        db_key: String,
        // JSON of `PublicKeyCredentialCreationOptions` from the website.
        options_json: String,
        // Page origin (e.g. `"https://example.com"`).
        origin: String,
        // Full URL of the browser tab that initiated the ceremony.
        // Used to cross-verify that `origin` matches the actual page URL.
        #[serde(default)]
        tab_url: Option<String>,
        // UUID of an existing entry to attach the passkey to (optional).
        existing_entry_uuid: Option<String>,
        // Title for a brand-new entry (used when `existing_entry_uuid` is absent).
        new_entry_name: Option<String>,
        // UUID of an existing group for a new entry. Defaults to root when absent.
        group_uuid: Option<String>,
        // Name of a brand-new group to create (used when `group_uuid` is absent).
        new_group_name: Option<String>,
    },

    // ── Passkey: authentication ───────────────────────────────────────────────

    // Step 1 of authentication: fetch passkeys matching the site's RP ID.
    GetPasskeyList {
        association_id: String,
        request_id: String,
        // JSON of `PublicKeyCredentialRequestOptions` from the website.
        options_json: String,
        origin: String,
        // Full URL of the browser tab that initiated the ceremony.
        #[serde(default)]
        tab_url: Option<String>,
    },

    // Step 2 of authentication: user has chosen a passkey; sign the assertion.
    CompletePasskeyAssertion {
        association_id: String,
        request_id: String,
        db_key: String,
        entry_uuid: Uuid,
        options_json: String,
        origin: String,
        // Full URL of the browser tab that initiated the ceremony.
        #[serde(default)]
        tab_url: Option<String>,
    },
}

// Returns true if `extension_id` is a known OneKeePass extension for the given browser.
// IDs are sourced from the same constants written into the native messaging manifest files.
fn is_known_extension_id(browser_id: &str, extension_id: &str) -> bool {
    match browser_id {
        "Firefox" => extension_id == native_messaging_config::FIREFOX_EXTENSION_ID,
        "Chrome" => native_messaging_config::CHROME_EXTENSION_IDS.contains(&extension_id),
        _ => false,
    }
}

// Validates that `origin` is a syntactically well-formed `https://` origin:
// scheme must be `https`, host must be non-empty, and no path/query/fragment
// may be present.  Rejects `http://` origins outright — WebAuthn credentials
// must only be scoped to HTTPS origins.
fn validate_https_origin(origin: &str) -> onekeepass_core::error::Result<()> {
    let host_part = origin
        .strip_prefix("https://")
        .ok_or_else(|| onekeepass_core::error::Error::UnexpectedError("INVALID_ORIGIN".to_string()))?;

    if host_part.is_empty()
        || host_part.contains('/')
        || host_part.contains('?')
        || host_part.contains('#')
        || host_part.chars().any(|c| c.is_ascii_whitespace() || c.is_control())
    {
        log::warn!("Passkey origin validation failed: '{}'", origin);
        return Err(onekeepass_core::error::Error::UnexpectedError(
            "INVALID_ORIGIN".to_string(),
        ));
    }

    Ok(())
}

// Cross-references the claimed `origin` against the full `tab_url` supplied by
// the extension.  The tab URL must start with the exact origin string followed
// immediately by `'/'`, `'?'`, `'#'`, or end-of-string, preventing subdomain
// confusion (e.g. `https://evil.com` cannot match `https://evil.com.bank.com/`).
//
// When `tab_url` is absent (e.g. the extension sent an older message format)
// the check is skipped so the call does not break existing connections.
fn validate_origin_matches_tab_url(origin: &str, tab_url: Option<&str>) -> onekeepass_core::error::Result<()> {
    let Some(url) = tab_url else {
        return Ok(()); // tab_url not provided — skip cross-reference
    };
    if url.is_empty() {
        return Ok(()); // empty string treated as absent
    }
    if !url.starts_with(origin) {
        log::warn!(
            "Origin '{}' does not match tab URL '{}' — rejecting",
            origin, url
        );
        return Err(onekeepass_core::error::Error::UnexpectedError(
            "ORIGIN_TAB_MISMATCH".to_string(),
        ));
    }
    // The character immediately after the origin must be '/', '?', '#', or end-of-string.
    // This prevents `https://evil.com` from passing when tab_url is `https://evil.com.bank.com/`.
    let remainder = &url[origin.len()..];
    if !remainder.is_empty()
        && !matches!(remainder.chars().next(), Some('/') | Some('?') | Some('#'))
    {
        log::warn!(
            "Origin '{}' does not cleanly terminate in tab URL '{}' — possible subdomain bypass",
            origin, url
        );
        return Err(onekeepass_core::error::Error::UnexpectedError(
            "ORIGIN_TAB_MISMATCH".to_string(),
        ));
    }
    Ok(())
}

// Rejects oversized string fields before they reach the database or crypto layer.
// Returns a generic error to avoid leaking field details to the caller.
fn check_field_len(field_name: &str, value: &str, max_bytes: usize) -> onekeepass_core::error::Result<()> {
    if value.len() > max_bytes {
        log::warn!(
            "Input field '{}' exceeds max size ({} > {} bytes) — rejecting",
            field_name,
            value.len(),
            max_bytes
        );
        Err(onekeepass_core::error::Error::UnexpectedError(
            "FIELD_TOO_LARGE".to_string(),
        ))
    } else {
        Ok(())
    }
}

impl Request {
    // Called when the app side proxy handler receives a native message json string from browser extension through okp proxy stdio app
    pub(crate) async fn handle_input_message(input_message: String, sender: Arc<BrowserServiceTx>) {
        // log::debug!("In handle_input_message ...");

        // TDDO:
        // Currently there is no request that has any sensitive data. So the extension side no encryption is done
        // In the future we pass any sensitive data as "message_data", then we need to decrypt and then convert that json to rust struct
        match serde_json::from_str(&input_message) {
            Ok(Request::Associate { client_id, extension_id }) => {
                Self::verify(client_id, extension_id, sender).await;
            }

            Ok(Request::InitSessionKey {
                association_id,
                client_session_pub_key,
            }) => {
                Self::init_session(&association_id, &client_session_pub_key).await;
            }

            Ok(Request::EnabledDatabaseMatchedEntryList {
                ref association_id,
                ref request_id,
                ref form_url,
            }) => {
                Self::matched_entries_of_enabled_databases(association_id, form_url, request_id)
                    .await;
            }

            Ok(Request::SelectedEntry {
                ref association_id,
                ref request_id,
                ref db_key,
                ref entry_uuid,
            }) => {
                Self::entry_details_by_id(association_id, db_key, entry_uuid, request_id).await;
            }

            // ── Passkey handlers ─────────────────────────────────────────────

            Ok(Request::GetOpenedDatabasesForPasskey {
                ref association_id,
                ref request_id,
            }) => {
                Self::get_opened_databases_for_passkey(association_id, request_id).await;
            }

            Ok(Request::GetDbGroupsForPasskey {
                ref association_id,
                ref request_id,
                ref db_key,
            }) => {
                Self::get_db_groups_for_passkey(association_id, request_id, db_key).await;
            }

            Ok(Request::GetDbGroupEntriesForPasskey {
                ref association_id,
                ref request_id,
                ref db_key,
                ref group_uuid,
            }) => {
                Self::get_db_group_entries_for_passkey(
                    association_id,
                    request_id,
                    db_key,
                    group_uuid,
                )
                .await;
            }

            Ok(Request::CreatePasskey {
                ref association_id,
                ref request_id,
                ref db_key,
                ref options_json,
                ref origin,
                ref tab_url,
                ref existing_entry_uuid,
                ref new_entry_name,
                ref group_uuid,
                ref new_group_name,
            }) => {
                Self::create_passkey(
                    association_id,
                    request_id,
                    db_key,
                    options_json,
                    origin,
                    tab_url.as_deref(),
                    existing_entry_uuid.clone(),
                    new_entry_name.clone(),
                    group_uuid.clone(),
                    new_group_name.clone(),
                )
                .await;
            }

            Ok(Request::GetPasskeyList {
                ref association_id,
                ref request_id,
                ref options_json,
                ref origin,
                ref tab_url,
            }) => {
                Self::get_passkey_list(association_id, request_id, options_json, origin, tab_url.as_deref()).await;
            }

            Ok(Request::CompletePasskeyAssertion {
                ref association_id,
                ref request_id,
                ref db_key,
                ref entry_uuid,
                ref options_json,
                ref origin,
                ref tab_url,
            }) => {
                Self::complete_passkey_assertion(
                    association_id,
                    request_id,
                    db_key,
                    entry_uuid,
                    options_json,
                    origin,
                    tab_url.as_deref(),
                )
                .await;
            }

            // Ok(x) => {
            //     log::error!("Unhandled request enum variant {:?}", &x);
            //     let resp = ResponseResult::with_error(
            //         ResponseActionName::UnexpectedError,
            //         &format!("Unhandled request enum variant {:?}", &x),
            //     );
            //     // No session is yet available and we send the responde directly
            //     let _r = sender.send(resp.json_str()).await;
            // }
            Err(e) => {
                log::error!(
                    "Error {} in deserializing the json of input_message: {} ",
                    e,
                    &input_message
                );
                let resp = ResponseResult::with_error(
                    ResponseActionName::JsonParseError,
                    &format!("Error {} in deserializing the json", e),
                );
                // No session is yet available and we send the responde directly
                let _r = sender.send(resp.json_str()).await;
            }
        }
    }

    // Called first time when the extension sends the associate message
    // Need to check that either user has already enabled the browser extension use and if not we need to ask user
    // confirm the extension use
    async fn verify(client_id: String, extension_id: Option<String>, sender: Arc<BrowserServiceTx>) {
        // Incoming client_id is the same as browser_id (e.g Firefox,Chrome)
        // At this time client_id, browser_id and association_id are the same

        let browser_id = client_id.clone();

        // log::debug!("In verify for browser id {}, FIREFOX {}, CHROME {}",&client_id,&client_id != FIREFOX,&client_id != CHROME);

        // Ensure that the browser is supported. This should not happen as we write native messaging config file only for supported browsers
        // but just in case
        if !SUPPORTED_BROWSERS.contains(&client_id.as_str()) {
            log::error!("Browser id {} is not supported ", &client_id);
            let resp = ResponseResult::with_error(
                ResponseActionName::Associate,
                &format!("BROWSER_NOT_SUPPORTED"),
            );
            // No session is yet available and we send the error responde directly
            let _r = sender.send(resp.json_str()).await;
            return;
        }

        // Verify that the connecting extension is a known OneKeePass extension.
        // This guards against a different extension of the same browser obtaining silent
        // auto-allow after the user has approved the legitimate OneKeePass extension once.
        match &extension_id {
            Some(ext_id) if !is_known_extension_id(&browser_id, ext_id) => {
                log::error!(
                    "Unknown extension id '{}' for browser '{}' — rejecting association",
                    ext_id, &browser_id
                );
                let resp = ResponseResult::with_error(
                    ResponseActionName::Associate,
                    "UNKNOWN_EXTENSION_ID",
                );
                let _r = sender.send(resp.json_str()).await;
                return;
            }
            None => {
                // extension_id field was not sent (old client or tampered request) — reject.
                log::error!("No extension_id in Associate request for browser '{}' — rejecting", &browser_id);
                let resp = ResponseResult::with_error(
                    ResponseActionName::Associate,
                    "MISSING_EXTENSION_ID",
                );
                let _r = sender.send(resp.json_str()).await;
                return;
            }
            _ => {} // extension_id is present and recognised — continue
        }

        // First we create a callback that will be called with 'confirmed' value after user confirms
        let verifier = verifier::ConnectionVerifier::new({
            move |confirmed: bool| {
                // async functions generate Futures, which often contain self-referential data ( meaning they contain pointers to data within themselves)

                // To safely interact with these self-referential Futures, especially when they are polled by an executor,
                // they need to be "pinned" to a stable memory location.

                // The Pin type in Rust is a wrapper that guarantees a value
                // will not be moved or dropped in a way that would invalidate its internal pointers.

                // When a Future needs to be kept at a stable memory address, it needs to be "pinned."

                // Box::pin(value) is a common way to achieve this. It allocates value on the heap and then
                // returns a Pin<Box<value>>. This ensures that the value is
                // both heap-allocated (allowing for dynamic sizing) and pinned (preventing movement).

                Box::pin({
                    let client_id = client_id.clone();
                    // Generate a unique association_id per session so multiple browser
                    // connections (different profiles, users) cannot collide or hijack
                    // each other's session. The client_id ("Firefox"/"Chrome") is kept
                    // separately for preference checks but is no longer the session key.
                    let association_id = Uuid::new_v4().to_string();
                    let sender = sender.clone();

                    async move {
                        // TODO: Need to send error response if 'confirmed' is false

                        log::debug!("Confirmed by user {}", &confirmed);

                        if !confirmed {
                            let resp = ResponseResult::with_error(
                                ResponseActionName::Associate,
                                &format!("ASSOCIATION_REJECTED"),
                            );
                            // No session is yet available and we send the responde directly
                            let _r = sender.send(resp.json_str()).await;
                            return;
                        }

                        // User has allowed the browser ext connection
                        let resp = match SessionStore::session_start(&association_id, sender).await
                        {
                            Ok(_) => ResponseResult::with_ok(Response::Associate {
                                client_id,
                                association_id: association_id.clone(),
                                app_version: app_state::AppState::state_instance().app_version(),
                            }),

                            Err(e) => ResponseResult::with_error(
                                ResponseActionName::Associate,
                                &format!("{}", e),
                            ),
                        };
                        // Now send the response back to the extension
                        SessionStore::send_session_response(&association_id, &resp.json_str())
                            .await;
                    }
                })
            }
        });

        // Needs user's previous confirmation  or wait for the user's confirmation from UI side
        verifier.run_verifier(&browser_id).await;
    }

    async fn init_session(association_id: &str, client_session_pub_key: &str) {
        // Curve25519 public key is 32 bytes → base64 = 44 chars; 64 gives ample headroom
        if let Err(e) = check_field_len("client_session_pub_key", client_session_pub_key, 64) {
            let resp = ResponseResult::with_error(ResponseActionName::InitSessionKey, &format!("{}", e));
            SessionStore::send_session_response(association_id, &resp.json_str()).await;
            return;
        }
        let resp = match SessionStore::init_session(association_id, client_session_pub_key).await {
            Ok(app_session_pub_key) => {
                let (nonce, enc_msg) = SessionStore::encrypt(
                    association_id,
                    r#"{"message":"Server ENCRYPTED test message"}"#,
                )
                .await
                .unwrap();

                ResponseResult::with_ok(Response::InitSessionKey {
                    app_session_pub_key,
                    nonce: nonce,
                    test_message: enc_msg,
                })
            }
            Err(e) => {
                ResponseResult::with_error(ResponseActionName::InitSessionKey, &format!("{}", e))
            }
        };

        // Send using tx to output writer
        SessionStore::send_session_response(&association_id, &resp.json_str()).await;
    }

    async fn matched_entries_of_enabled_databases(
        association_id: &str,
        input_url: &str,
        request_id: &str,
    ) {
        let matched_entries = check_field_len("form_url", input_url, 2048)
            .and_then(|_| db_calls::find_matching_in_enabled_db_entries(input_url))
            .and_then(|ref s| Ok(serde_json::to_string_pretty(s)?));

        let resp = match matched_entries {
            Ok(ref matched) => match SessionStore::encrypt(association_id, matched).await {
                Ok((nonce, enc_msg)) => {
                    ResponseResult::with_ok(Response::EnabledDatabaseMatchedEntryList {
                        message_content: enc_msg,
                        request_id: request_id.to_string(),
                        nonce: nonce,
                    })
                }
                Err(error) => ResponseActionName::EnabledDatabaseMatchedEntryList.with_error(error),
            },
            Err(e) => ResponseResult::from_error(
                ResponseActionName::EnabledDatabaseMatchedEntryList,
                request_id,
                &format!("{}", e),
            ),
        };

        // Send using tx to output writer
        SessionStore::send_session_response(&association_id, &resp.json_str()).await;
    }

    // ── Passkey handler implementations ──────────────────────────────────────

    async fn get_opened_databases_for_passkey(association_id: &str, request_id: &str) {
        let json_result = db_calls::get_opened_databases_for_passkey()
            .and_then(|ref dbs| Ok(serde_json::to_string_pretty(dbs)?));

        let resp = match json_result {
            Ok(ref json) => match SessionStore::encrypt(association_id, json).await {
                Ok((nonce, enc_msg)) => {
                    ResponseResult::with_ok(Response::OpenedDatabasesForPasskey {
                        message_content: enc_msg,
                        request_id: request_id.to_string(),
                        nonce,
                    })
                }
                Err(error) => {
                    ResponseActionName::GetOpenedDatabasesForPasskey.with_error(error)
                }
            },
            Err(e) => ResponseResult::from_error(
                ResponseActionName::GetOpenedDatabasesForPasskey,
                request_id,
                &format!("{}", e),
            ),
        };

        SessionStore::send_session_response(association_id, &resp.json_str()).await;
    }

    async fn get_db_groups_for_passkey(association_id: &str, request_id: &str, db_key: &str) {
        let json_result = db_calls::validate_db_key(db_key)
            .and_then(|_| db_calls::get_db_groups_for_passkey(db_key))
            .and_then(|ref groups| Ok(serde_json::to_string_pretty(groups)?));

        let resp = match json_result {
            Ok(ref json) => match SessionStore::encrypt(association_id, json).await {
                Ok((nonce, enc_msg)) => ResponseResult::with_ok(Response::DbGroupsForPasskey {
                    message_content: enc_msg,
                    request_id: request_id.to_string(),
                    nonce,
                }),
                Err(error) => ResponseActionName::GetDbGroupsForPasskey.with_error(error),
            },
            Err(e) => ResponseResult::from_error(
                ResponseActionName::GetDbGroupsForPasskey,
                request_id,
                &format!("{}", e),
            ),
        };

        SessionStore::send_session_response(association_id, &resp.json_str()).await;
    }

    async fn get_db_group_entries_for_passkey(
        association_id: &str,
        request_id: &str,
        db_key: &str,
        group_uuid: &str,
    ) {
        let parse_result = uuid::Uuid::parse_str(group_uuid);

        let resp = match parse_result {
            Err(e) => ResponseResult::from_error(
                ResponseActionName::GetDbGroupEntriesForPasskey,
                request_id,
                &format!("Invalid group_uuid: {}", e),
            ),
            Ok(group_uuid_parsed) => {
                let json_result = db_calls::validate_db_key(db_key)
                    .and_then(|_| db_calls::get_group_entries_for_passkey(db_key, &group_uuid_parsed))
                    .and_then(|ref entries| Ok(serde_json::to_string_pretty(entries)?));

                match json_result {
                    Ok(ref json) => match SessionStore::encrypt(association_id, json).await {
                        Ok((nonce, enc_msg)) => {
                            ResponseResult::with_ok(Response::DbGroupEntriesForPasskey {
                                message_content: enc_msg,
                                request_id: request_id.to_string(),
                                nonce,
                            })
                        }
                        Err(error) => {
                            ResponseActionName::GetDbGroupEntriesForPasskey.with_error(error)
                        }
                    },
                    Err(e) => ResponseResult::from_error(
                        ResponseActionName::GetDbGroupEntriesForPasskey,
                        request_id,
                        &format!("{}", e),
                    ),
                }
            }
        };

        SessionStore::send_session_response(association_id, &resp.json_str()).await;
    }

    #[allow(clippy::too_many_arguments)]
    async fn create_passkey(
        association_id: &str,
        request_id: &str,
        db_key: &str,
        options_json: &str,
        origin: &str,
        tab_url: Option<&str>,
        existing_entry_uuid: Option<String>,
        new_entry_name: Option<String>,
        group_uuid: Option<String>,
        new_group_name: Option<String>,
    ) {
        let credential_result = check_field_len("options_json", options_json, 65536)
            .and_then(|_| check_field_len("origin", origin, 512))
            .and_then(|_| validate_https_origin(origin))
            .and_then(|_| validate_origin_matches_tab_url(origin, tab_url))
            .and_then(|_| check_field_len("new_entry_name", new_entry_name.as_deref().unwrap_or(""), 512))
            .and_then(|_| check_field_len("new_group_name", new_group_name.as_deref().unwrap_or(""), 512))
            .and_then(|_| db_calls::validate_db_key(db_key))
            .and_then(|_| {
            passkey_db::create_and_store_passkey(
                db_key,
                options_json,
                origin,
                existing_entry_uuid,
                new_entry_name,
                group_uuid,
                new_group_name,
            )
        });

        let resp = match credential_result {
            Ok(ref credential_json) => {
                match SessionStore::encrypt(association_id, credential_json).await {
                    Ok((nonce, enc_msg)) => ResponseResult::with_ok(Response::PasskeyCreated {
                        message_content: enc_msg,
                        request_id: request_id.to_string(),
                        nonce,
                    }),
                    Err(error) => ResponseActionName::CreatePasskey.with_error(error),
                }
            }
            Err(e) => ResponseResult::from_error(
                ResponseActionName::CreatePasskey,
                request_id,
                &format!("{}", e),
            ),
        };

        SessionStore::send_session_response(association_id, &resp.json_str()).await;
    }

    async fn get_passkey_list(
        association_id: &str,
        request_id: &str,
        options_json: &str,
        origin: &str,
        tab_url: Option<&str>,
    ) {
        // Validate field sizes, origin structure, and tab-url cross-reference before parsing
        if let Err(e) = check_field_len("options_json", options_json, 65536)
            .and_then(|_| check_field_len("origin", origin, 512))
            .and_then(|_| validate_https_origin(origin))
            .and_then(|_| validate_origin_matches_tab_url(origin, tab_url))
        {
            let resp = ResponseResult::from_error(
                ResponseActionName::GetPasskeyList,
                request_id,
                &format!("{}", e),
            );
            SessionStore::send_session_response(association_id, &resp.json_str()).await;
            return;
        }
        // Extract rpId and allowCredentials from the options JSON
        let parse_result: Result<serde_json::Value, _> = serde_json::from_str(options_json);

        let resp = match parse_result {
            Err(e) => ResponseResult::from_error(
                ResponseActionName::GetPasskeyList,
                request_id,
                &format!("Invalid options_json: {}", e),
            ),
            Ok(opts) => {
                let rp_id = opts["rpId"]
                    .as_str()
                    .unwrap_or("")
                    .to_string();

                let allow_ids: Vec<String> = opts["allowCredentials"]
                    .as_array()
                    .map(|arr| {
                        arr.iter()
                            .filter_map(|v| v["id"].as_str().map(String::from))
                            .collect()
                    })
                    .unwrap_or_default();

                let list_result = db_calls::find_matching_passkeys(&rp_id, allow_ids)
                    .and_then(|ref list| Ok(serde_json::to_string_pretty(list)?));

                match list_result {
                    Ok(ref json) => match SessionStore::encrypt(association_id, json).await {
                        Ok((nonce, enc_msg)) => {
                            ResponseResult::with_ok(Response::PasskeyList {
                                message_content: enc_msg,
                                request_id: request_id.to_string(),
                                nonce,
                            })
                        }
                        Err(error) => ResponseActionName::GetPasskeyList.with_error(error),
                    },
                    Err(e) => ResponseResult::from_error(
                        ResponseActionName::GetPasskeyList,
                        request_id,
                        &format!("{}", e),
                    ),
                }
            }
        };

        SessionStore::send_session_response(association_id, &resp.json_str()).await;
    }

    async fn complete_passkey_assertion(
        association_id: &str,
        request_id: &str,
        db_key: &str,
        entry_uuid: &Uuid,
        options_json: &str,
        origin: &str,
        tab_url: Option<&str>,
    ) {
        // sign_passkey_assertion already returns a JSON string — no re-serialization needed.
        let result = check_field_len("options_json", options_json, 65536)
            .and_then(|_| check_field_len("origin", origin, 512))
            .and_then(|_| validate_https_origin(origin))
            .and_then(|_| validate_origin_matches_tab_url(origin, tab_url))
            .and_then(|_| db_calls::validate_db_key(db_key))
            .and_then(|_| db_calls::sign_passkey_assertion(db_key, entry_uuid, options_json, origin));

        let resp = match result {
            Ok(ref json) => match SessionStore::encrypt(association_id, json).await {
                Ok((nonce, enc_msg)) => {
                    ResponseResult::with_ok(Response::PasskeyAssertionComplete {
                        message_content: enc_msg,
                        request_id: request_id.to_string(),
                        nonce,
                    })
                }
                Err(error) => ResponseActionName::CompletePasskeyAssertion.with_error(error),
            },
            Err(e) => ResponseResult::from_error(
                ResponseActionName::CompletePasskeyAssertion,
                request_id,
                &format!("{}", e),
            ),
        };

        SessionStore::send_session_response(association_id, &resp.json_str()).await;
    }

    // Gets the entry detail data for a given db_key and entry uuid
    async fn entry_details_by_id(
        association_id: &str,
        db_key: &str,
        entry_uuid: &Uuid,
        request_id: &str,
    ) {
        let json_converted_result = db_calls::validate_db_key(db_key)
            .and_then(|_| db_calls::entry_details_by_id(db_key, entry_uuid))
            .and_then(|ref s| Ok(serde_json::to_string_pretty(s)?));

        let resp = match json_converted_result {
            Ok(ref json_converted) => {
                match SessionStore::encrypt(association_id, json_converted).await {
                    Ok((nonce, enc_msg)) => ResponseResult::with_ok(Response::SelectedEntry {
                        message_content: enc_msg,
                        request_id: request_id.to_string(),
                        nonce: nonce,
                    }),
                    Err(error) => ResponseActionName::SelectedEntry.from_error(error, request_id),
                }
            }
            Err(e) => ResponseResult::from_error(
                ResponseActionName::SelectedEntry,
                request_id,
                &format!("{}", e),
            ),
        };

        // Send using tx to output writer
        SessionStore::send_session_response(&association_id, &resp.json_str()).await;
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "action")]
pub enum Response {
    // Responds with association_id from app
    Associate {
        client_id: String,
        association_id: String,
        // Introduced in OKP 0.18.0 and extension 
        app_version: String,
    },

    // Provides the app side pub key for the shared key encryption/decryption for the 'InitSessionKey' request
    InitSessionKey {
        app_session_pub_key: String,
        nonce: String,
        test_message: String,
    },

    // Sends the encrypted the serialized json object as message
    EnabledDatabaseMatchedEntryList {
        message_content: String,
        request_id: String,
        nonce: String,
    },

    SelectedEntry {
        message_content: String,
        request_id: String,
        nonce: String,
    },

    // ── Passkey responses ─────────────────────────────────────────────────────

    // Response to `GetOpenedDatabasesForPasskey`: encrypted JSON array of
    // `{ db_key, db_name }` objects.
    OpenedDatabasesForPasskey {
        message_content: String,
        request_id: String,
        nonce: String,
    },

    // Response to `GetDbGroupsForPasskey`: encrypted JSON array of `GroupInfo`.
    DbGroupsForPasskey {
        message_content: String,
        request_id: String,
        nonce: String,
    },

    // Response to `GetDbGroupEntriesForPasskey`: encrypted JSON array of `EntryBasicInfo`.
    DbGroupEntriesForPasskey {
        message_content: String,
        request_id: String,
        nonce: String,
    },

    // Registration complete: encrypted `PublicKeyCredential` JSON ready to
    // be returned by the extension to the website.
    PasskeyCreated {
        message_content: String,
        request_id: String,
        nonce: String,
    },

    // Authentication: encrypted JSON array of `PasskeySummary` objects
    // matching the site's RP ID.
    PasskeyList {
        message_content: String,
        request_id: String,
        nonce: String,
    },

    // Authentication signed: encrypted `PublicKeyCredential` assertion JSON.
    PasskeyAssertionComplete {
        message_content: String,
        request_id: String,
        nonce: String,
    },
}

impl Response {

    pub(crate) fn disconnect(browser_id: &'static str) {
        log::info!("Disonnecting from the browser extension as it is disabled");

        // browser_id is association_id
        // Async call
        tauri::async_runtime::spawn(async move {
            // This special DISCONNECT string is sent to the proxy instead of Json string
            SessionStore::send_session_response(browser_id, "DISCONNECT").await;
        });
    }
}

enum ResponseActionName {
    Associate,
    InitSessionKey,
    EnabledDatabaseMatchedEntryList,
    SelectedEntry,
    JsonParseError,
    // Passkey
    GetOpenedDatabasesForPasskey,
    GetDbGroupsForPasskey,
    GetDbGroupEntriesForPasskey,
    CreatePasskey,
    GetPasskeyList,
    CompletePasskeyAssertion,
    // UnexpectedError,
}

impl ResponseActionName {
    fn name(&self) -> &str {
        use ResponseActionName::*;
        match self {
            Associate => "Associate",
            InitSessionKey => "InitSessionKey",
            EnabledDatabaseMatchedEntryList => "EnabledDatabaseMatchedEntryList",
            SelectedEntry => "SelectedEntry",
            JsonParseError => "JsonParseError",
            // Passkey
            GetOpenedDatabasesForPasskey => "GetOpenedDatabasesForPasskey",
            GetDbGroupsForPasskey => "GetDbGroupsForPasskey",
            GetDbGroupEntriesForPasskey => "GetDbGroupEntriesForPasskey",
            CreatePasskey => "CreatePasskey",
            GetPasskeyList => "GetPasskeyList",
            CompletePasskeyAssertion => "CompletePasskeyAssertion",
            // UnexpectedError => "UnexpectedError",
        }
    }

    fn with_error(self, error: onekeepass_core::error::Error) -> ResponseResult {
        ResponseResult::with_error(self, &format!("{}", error))
    }

    fn from_error(self, error: onekeepass_core::error::Error, request_id: &str) -> ResponseResult {
        ResponseResult::from_error(self, &format!("{}", error,), request_id)
    }
}

#[derive(Serialize)]
struct ErrorInfo {
    action: String,
    request_id: Option<String>,
    error_message: String,
}

#[derive(Serialize)]
struct ResponseResult {
    ok: Option<Response>,
    error: Option<ErrorInfo>,
}

impl ResponseResult {
    fn with_ok(val: Response) -> Self {
        ResponseResult {
            ok: Some(val),
            error: None,
        }
    }

    // Creates a error part which can be converted to an error response json string
    fn with_error(action: ResponseActionName, error: &str) -> Self {
        ResponseResult {
            ok: None,
            error: Some(ErrorInfo {
                action: action.name().to_string(),
                request_id: None,
                error_message: error.to_string(),
            }),
        }
    }

    fn from_error(action: ResponseActionName, error: &str, request_id: &str) -> Self {
        ResponseResult {
            ok: None,
            error: Some(ErrorInfo {
                action: action.name().to_string(),
                request_id: Some(request_id.to_string()),
                error_message: error.to_string(),
            }),
        }
    }

    // Converts Err to json string with "error" key
    fn json_str(&self) -> String {
        let json_str = match serde_json::to_string_pretty(self) {
            Ok(s) => s,
            Err(e) => {
                log::error!("InvokeResult conversion failed with error {}", &e);
                r#"{"error" : "InvokeResult conversion failed"}"#.into()
            }
        };
        json_str
    }
}

/*

Error trailing characters at line 1 column 146 in deserializing to json of received_message_str:
{"action":"EnabledDatabaseMatchedEntryList","form_url":"https://www.expedia.com/login?&uurl=e3id%3Dredr%26rurl%3D%2F","association_id":"Firefox"}
{"action":"EnabledDatabaseMatchedEntryList","form_url":"https://www.expedia.com/login?&uurl=e3id%3Dredr%26rurl%3D%2F","association_id":"Firefox"}
*/

#[cfg(test)]
mod tests {
    use crate::browser_service::message::Request;

    #[test]
    fn verify() {
        println!("Some test here");

        let input_message = r#"{"action":"EnabledDatabaseMatchedEntryList","form_url":"https://www.expedia.com/login?&uurl=e3id%3Dredr%26rurl%3D%2F","association_id":"Firefox"}"#;

        match serde_json::from_str(&input_message) {
            Ok(Request::EnabledDatabaseMatchedEntryList {
                ref association_id,
                #[allow(unused)]
                ref request_id,
                ref form_url,
            }) => {
                println!("Parsed correctly {}, {}", association_id, form_url);
            }

            Ok(x) => {
                println!("Unhandled request enum variant {:?}", &x);
            }
            Err(e) => {
                println!(
                    "Error {} in deserializing to json of received_message_str: {} ",
                    e, &input_message
                );
            }
        }
    }

    // ── Passkey Request deserialization ───────────────────────────────────────

    #[test]
    fn parse_get_opened_databases_for_passkey_request() {
        let json = r#"{"action":"GetOpenedDatabasesForPasskey","association_id":"Firefox","request_id":"req-1"}"#;
        match serde_json::from_str(json).unwrap() {
            Request::GetOpenedDatabasesForPasskey {
                association_id,
                request_id,
            } => {
                assert_eq!(association_id, "Firefox");
                assert_eq!(request_id, "req-1");
            }
            other => panic!("Unexpected variant: {:?}", other),
        }
    }

    #[test]
    fn parse_create_passkey_request_with_optional_fields() {
        // With entry_uuid, new_entry_name, group_uuid present
        let json = r#"{
            "action": "CreatePasskey",
            "association_id": "Chrome",
            "request_id": "req-2",
            "db_key": "/path/to/db.kdbx",
            "options_json": "{}",
            "origin": "https://example.com",
            "existing_entry_uuid": "11111111-1111-1111-1111-111111111111",
            "new_entry_name": "My Login",
            "group_uuid": "22222222-2222-2222-2222-222222222222"
        }"#;
        match serde_json::from_str(json).unwrap() {
            Request::CreatePasskey {
                association_id,
                db_key,
                origin,
                existing_entry_uuid,
                new_entry_name,
                group_uuid,
                ..
            } => {
                assert_eq!(association_id, "Chrome");
                assert_eq!(db_key, "/path/to/db.kdbx");
                assert_eq!(origin, "https://example.com");
                assert_eq!(
                    existing_entry_uuid.as_deref(),
                    Some("11111111-1111-1111-1111-111111111111")
                );
                assert_eq!(new_entry_name.as_deref(), Some("My Login"));
                assert_eq!(group_uuid.as_deref(), Some("22222222-2222-2222-2222-222222222222"));
            }
            other => panic!("Unexpected variant: {:?}", other),
        }
    }

    #[test]
    fn parse_create_passkey_request_without_optional_fields() {
        let json = r#"{
            "action": "CreatePasskey",
            "association_id": "Chrome",
            "request_id": "req-3",
            "db_key": "/path/db.kdbx",
            "options_json": "{}",
            "origin": "https://example.com"
        }"#;
        match serde_json::from_str(json).unwrap() {
            Request::CreatePasskey {
                existing_entry_uuid,
                new_entry_name,
                group_uuid,
                new_group_name,
                ..
            } => {
                assert!(existing_entry_uuid.is_none());
                assert!(new_entry_name.is_none());
                assert!(group_uuid.is_none());
                assert!(new_group_name.is_none());
            }
            other => panic!("Unexpected variant: {:?}", other),
        }
    }

    #[test]
    fn parse_get_passkey_list_request() {
        let opts = serde_json::json!({
            "rpId": "example.com",
            "challenge": "abc",
            "allowCredentials": [{"type":"public-key","id":"cred-1"},{"type":"public-key","id":"cred-2"}]
        });
        let json = serde_json::json!({
            "action": "GetPasskeyList",
            "association_id": "Firefox",
            "request_id": "req-4",
            "options_json": opts.to_string(),
            "origin": "https://example.com"
        })
        .to_string();

        match serde_json::from_str(&json).unwrap() {
            Request::GetPasskeyList {
                association_id,
                options_json,
                origin,
                ..
            } => {
                assert_eq!(association_id, "Firefox");
                assert_eq!(origin, "https://example.com");

                // Verify the options_json parses as expected by the handler
                let parsed: serde_json::Value = serde_json::from_str(&options_json).unwrap();
                assert_eq!(parsed["rpId"].as_str().unwrap(), "example.com");
                let ids: Vec<&str> = parsed["allowCredentials"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .filter_map(|v| v["id"].as_str())
                    .collect();
                assert_eq!(ids, vec!["cred-1", "cred-2"]);
            }
            other => panic!("Unexpected variant: {:?}", other),
        }
    }

    #[test]
    fn parse_complete_passkey_assertion_request() {
        let json = r#"{
            "action": "CompletePasskeyAssertion",
            "association_id": "Firefox",
            "request_id": "req-5",
            "db_key": "/db.kdbx",
            "entry_uuid": "33333333-3333-3333-3333-333333333333",
            "options_json": "{}",
            "origin": "https://example.com"
        }"#;
        match serde_json::from_str(json).unwrap() {
            Request::CompletePasskeyAssertion {
                association_id,
                db_key,
                entry_uuid,
                origin,
                ..
            } => {
                assert_eq!(association_id, "Firefox");
                assert_eq!(db_key, "/db.kdbx");
                assert_eq!(
                    entry_uuid.to_string(),
                    "33333333-3333-3333-3333-333333333333"
                );
                assert_eq!(origin, "https://example.com");
            }
            other => panic!("Unexpected variant: {:?}", other),
        }
    }

    // ── get_passkey_list options parsing logic ────────────────────────────────
    //
    // This mirrors the extraction logic inside `Request::get_passkey_list` so
    // that edge cases can be verified without spinning up the full async handler.

    fn extract_rp_id_and_creds(options_json: &str) -> (String, Vec<String>) {
        let opts: serde_json::Value = serde_json::from_str(options_json).unwrap();
        let rp_id = opts["rpId"].as_str().unwrap_or("").to_string();
        let allow_ids: Vec<String> = opts["allowCredentials"]
            .as_array()
            .map(|arr| {
                arr.iter()
                    .filter_map(|v| v["id"].as_str().map(String::from))
                    .collect()
            })
            .unwrap_or_default();
        (rp_id, allow_ids)
    }

    #[test]
    fn options_parsing_with_allow_credentials() {
        let opts = serde_json::json!({
            "rpId": "mysite.com",
            "challenge": "AAAA",
            "allowCredentials": [
                {"type": "public-key", "id": "cred-aaa"},
                {"type": "public-key", "id": "cred-bbb"}
            ]
        })
        .to_string();

        let (rp_id, allow_ids) = extract_rp_id_and_creds(&opts);
        assert_eq!(rp_id, "mysite.com");
        assert_eq!(allow_ids, vec!["cred-aaa", "cred-bbb"]);
    }

    #[test]
    fn options_parsing_without_allow_credentials() {
        let opts = serde_json::json!({
            "rpId": "opensite.com",
            "challenge": "BBBB"
        })
        .to_string();

        let (rp_id, allow_ids) = extract_rp_id_and_creds(&opts);
        assert_eq!(rp_id, "opensite.com");
        assert!(allow_ids.is_empty(), "no allowCredentials means empty list");
    }

    #[test]
    fn options_parsing_missing_rp_id_falls_back_to_empty_string() {
        let opts = serde_json::json!({ "challenge": "CCCC" }).to_string();
        let (rp_id, _) = extract_rp_id_and_creds(&opts);
        assert_eq!(rp_id, "", "missing rpId should produce empty string");
    }
}

// if !supported_browsers().contains(&client_id.as_str()) {
//     log::error!("Browser id {} is not supported ", &client_id);
//     let resp = ResponseResult::with_error(
//         ResponseActionName::Associate,
//         &format!("BROWSER_NOT_SUPPORTED"),
//     );
//     // No session is yet available and we send the responde directly
//     let _r = sender.send(resp.json_str()).await;
//     return;
// }
