use std::sync::Arc;

use serde::{Deserialize, Serialize};

use crate::browser_service::{
    db_calls,
    key_share::{BrowserServiceTx, SessionStore},
    verifier,
};

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "action")]
pub enum Request {
    CheckAppAvailability,

    // Called first time when the extension app is about to use the OneKeePass app
    Associate {
        client_id: String,
    },

    // Session pub key from client for the shared key encryption/decryption
    InitSessionKey {
        association_id: String,
        client_session_pub_key: String,
    },

    // The extension side request to get all entries that match an url
    EnabledDatabaseMatchedEntryList {
        association_id: String,
        form_url: String,
    },
}

impl Request {
    // Called when the app side proxy handler receives a native message json string from browser extension through okp proxy stdio app
    pub(crate) async fn handle_input_message(input_message: String, sender: Arc<BrowserServiceTx>) {
        // log::debug!("In handle_input_message ...");

        match serde_json::from_str(&input_message) {
            Ok(Request::Associate { client_id }) => {
                Self::verify(client_id, sender).await;
            }
            Ok(Request::InitSessionKey {
                association_id,
                client_session_pub_key,
            }) => {
                Self::init_session(&association_id, &client_session_pub_key).await;
            }
            Ok(Request::EnabledDatabaseMatchedEntryList {
                ref association_id,
                ref form_url,
            }) => {
                Self::matched_entries_of_enabled_databases(association_id, form_url).await;
            }

            Ok(x) => {
                log::error!("Unhandled request enum variant {:?}", &x);
            }
            Err(e) => {
                log::error!(
                    "Error {} in deserializing to json of received_message_str: {} ",
                    e,
                    &input_message
                );
            }
        }
    }

    // Called first time when the extension sends the associate message
    // Need to check that either user has already enabled the browser extension use and if not we need to ask user
    // confirm the extension use
    async fn verify(client_id: String, sender: Arc<BrowserServiceTx>) {
        let browser_id = client_id.clone();

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
                    // TODO:  Instead of using 'client_id', Shoudle we generate a random name for each session?
                    let association_id = client_id.clone();
                    let sender = sender.clone();

                    async move {
                        // TODO: Need to send error response if 'confirmed' is false

                        log::debug!("Confirmed by user {}", &confirmed);

                        if !confirmed {
                            let resp = ResponseResult::with_error(
                                ResponseActionName::Associate,
                                &format!("User rejected the browser extension connection"),
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
                            }),

                            Err(e) => ResponseResult::with_error(
                                ResponseActionName::Associate,
                                &format!("{}", e),
                            ),
                        };
                        // Now send the response back to the extension
                        SessionStore::send_session_response(&association_id, &resp.json_str()).await;
                    }
                })
            }
        });

        // Needs user's previous confirmation  or wait for the user's confirmation from UI side
        verifier.run_verifier(&browser_id).await;
    }

    async fn init_session(association_id: &str, client_session_pub_key: &str) {
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

    async fn matched_entries_of_enabled_databases(association_id: &str, input_url: &str) {
        let matched_entries = db_calls::find_matching_in_enabled_db_entries(input_url)
            .and_then(|ref s| Ok(serde_json::to_string_pretty(s)?));

        let resp = match matched_entries {
            Ok(ref matched) => match SessionStore::encrypt(association_id, matched).await {
                Ok((nonce, enc_msg)) => {
                    ResponseResult::with_ok(Response::EnabledDatabaseMatchedEntryList {
                        message: enc_msg,
                        nonce: nonce,
                    })
                }
                Err(error) => ResponseActionName::EnabledDatabaseMatchedEntryList.with_error(error),
            },
            Err(e) => ResponseResult::with_error(
                ResponseActionName::EnabledDatabaseMatchedEntryList,
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
    },

    // Provides the app side pub key for the shared key encryption/decryption for the 'InitSessionKey' request
    InitSessionKey {
        app_session_pub_key: String,
        nonce: String,
        test_message: String,
    },

    // Sends the encrypted the serialized json object as message
    EnabledDatabaseMatchedEntryList {
        message: String,
        nonce: String,
    },
}

enum ResponseActionName {
    Associate,
    InitSessionKey,
    EnabledDatabaseMatchedEntryList,
}

impl ResponseActionName {
    fn name(&self) -> &str {
        use ResponseActionName::*;
        match self {
            Associate => "Associate",
            InitSessionKey => "InitSessionKey",
            EnabledDatabaseMatchedEntryList => "EnabledDatabaseMatchedEntryList",
        }
    }

    fn with_error(self, error: onekeepass_core::error::Error) -> ResponseResult {
        ResponseResult::with_error(self, &format!("{}", error))
    }
}

#[derive(Serialize)]
struct ErrorInfo {
    action: String,
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
