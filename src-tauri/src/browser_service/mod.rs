mod db_calls;
mod key_share;
mod message;
mod proxy_handler;
mod verifier;

mod native_messaging_config;

pub(crate) use native_messaging_config::*;
pub(crate) use proxy_handler::start_proxy_handler;
pub(crate) use verifier::run_verifier;

pub(crate) const FIREFOX: &str = "Firefox";
pub(crate) const CHROME: &str = "Chrome";
//pub(crate) const EDGE: &str = "Edge";

const SUPPORTED_BROWSERS: [&str; 2] = [FIREFOX, CHROME];

// This also works and supported_browsers().contains(&client_id.as_str()) can be used
// fn supported_browsers() -> Vec<&'static str> {
//     vec![FIREFOX, CHROME]
// }
