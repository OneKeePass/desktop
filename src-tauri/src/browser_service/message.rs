use std::sync::Arc;

use serde::{Deserialize, Serialize};

use crate::browser_service::{
    db_calls,
    key_share::{BrowserServiceTx, SessionStore},
};

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "action")]
pub enum Request {
    CheckAppAvailability,

    Associate {
        client_id: String,
    },

    // Session pub key from client for the shared key encryption/decryption
    InitSessionKey {
        association_id: String,
        client_session_pub_key: String,
    },

    // Get a list of databases that can be used
    EnabledDatabases {
        association_id: String,
    },

    EnabledDatabaseMatchedEntryList {
        association_id: String,
        form_url: String,
    },
}

impl Request {
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
        SessionStore::send_response(&association_id, &resp.json_str()).await;
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
        SessionStore::send_response(&association_id, &resp.json_str()).await;
    }

    async fn verify(client_id: String, sender: Arc<BrowserServiceTx>) {
        let verifier = super::ConnectionVerifier::new({
            move |confirmed: bool| {
                Box::pin({
                    let client_id = client_id.clone();
                    let association_id = client_id.clone();
                    let sender = sender.clone();

                    async move {
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
                        SessionStore::send_response(&association_id, &resp.json_str()).await;
                    }
                })
            }
        });

        super::ConnectionVerifier::run_verifier(verifier).await;
    }

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

            Ok(Request::EnabledDatabases { association_id }) => {
                log::debug!("Handling EnabledDatabases call");
                let resp = ResponseResult::with_error(
                    ResponseActionName::EnabledDatabases,
                    &format!("Simulated error message "),
                );
                log::debug!("Sending test error message {}", &resp.json_str());
                // Send using tx to output writer
                SessionStore::send_response(&association_id, &resp.json_str()).await;
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
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "action")]
pub enum Response {
    // Responds with association_id from app
    Associate {
        client_id: String,
        association_id: String,
    },

    // App side pub key for the shared key encryption/decryption
    InitSessionKey {
        app_session_pub_key: String,
        nonce: String,
        test_message: String,
    },

    // Response for the 'GetEnabledDatabases' request
    EnabledDatabases {
        // The message is encrypted serialized string of Vec<String>
        message: String,
        nonce: String,
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
    EnabledDatabases,
}

impl ResponseActionName {
    fn name(&self) -> &str {
        use ResponseActionName::*;
        match self {
            Associate => "Associate",
            InitSessionKey => "InitSessionKey",
            EnabledDatabaseMatchedEntryList => "EnabledDatabaseMatchedEntryList",
            EnabledDatabases => "EnabledDatabases",
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

/*
// Convertable to a json string as
// "{ok: 'a string value serialized from T', error: null }" or  "{ok: null, error: 'error string'}"
#[derive(Serialize)]
pub struct InvokeResult<T> {
    ok: Option<T>,
    error: Option<String>,
}

impl<T: Serialize> InvokeResult<T> {
    // Creates a ok part which can be converted to a ok response json string
    pub fn with_ok(val: T) -> Self {
        InvokeResult {
            ok: Some(val),
            error: None,
        }
    }

    // Creates a error part which can be converted to an error response json string
    pub fn with_error(val: &str) -> Self {
        InvokeResult {
            ok: None,
            error: Some(val.into()),
        }
    }

    // Converts OK to json string with "ok" key
    fn ok_json_str(val: T) -> String {
        Self::with_ok(val).json_str()
    }

    // Converts Err to json string with "error" key
    pub fn json_str(&self) -> String {
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

*/

// pub struct ResponseResult1<T> {
//     action:String,
//     ok: Option<T>,
//     error: Option<String>,
// }

/*

{:action "Associate" :ok {} :error {}}

*/

// impl Response {
//     pub(crate) fn ok(&self) -> ResponseResult {
//         match self {
//             Response::Associate {
//                 client_id,
//                 association_id,
//             } => ResponseResult::with_ok(self),
//             Response::InitSessionKey {
//                 app_session_pub_key,
//                 nonce,
//                 test_message,
//             } => ResponseResult::with_ok(self),
//             Response::EnabledDatabases { message, nonce } => todo!(),
//             Response::EnabledDatabaseMatchedEntryList { message, seq } => todo!(),
//         }
//     }

//     pub(crate) fn error(enum_name: String, error: String) -> ResponseResult {
//         ResponseResult {
//             ok: None,
//             error: Some(ErrorInfo {
//                 action: enum_name,
//                 error_message: error,
//             }),
//         }
//     }
// }
