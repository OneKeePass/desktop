use serde::{Deserialize, Serialize};

use crate::browser_service::key_share::Session;

#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "action")]
pub enum Request {
    CheckAppAvailability,

    Associate {client_id:String,},

    // Session pub key from client for the shared key encryption/decryption
    InitSessionKey {association_id:String, client_session_pub_key:String,},
    
    // Get a list of databases that can be used
    GetEnabledDatabases {association_id:String, },

    EnabledDatabaseMatchedEntryList {form_url:String,seq:usize}
}

impl Request {
    async fn handle(session: &mut Session,  received_message_str: &String,) {
        // match serde_json::from_str(&received_message_str) {

        // }
    }
}


#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "action")]
pub enum Response {
    // Responds with association_id from app
    Associate {client_id:String, association_id:String,},

    // App side pub key for the shared key encryption/decryption
    InitSessionKey{app_session_pub_key:String, nonce:String, test_message:String,},
    
    // Response for the 'GetEnabledDatabases' request
    EnabledDatabases {message:String,nonce:String, }, // encrypted message names:Vec<String>

    EnabledDatabaseMatchedEntryList {message:String,seq:usize}
}




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

