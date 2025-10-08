use std::collections::HashSet;

use serde::{Deserialize, Serialize};

use onekeepass_core::error::Result;

use crate::browser_service::{self,start_proxy_handler, FirefoxNativeMessagingConfig};

pub(crate) const FIREFOX: &str = "Firefox";
// pub(crate) const CHROME: &str = "Chrome";
// pub(crate) const EDGE: &str = "Edge";

#[derive(Debug,Deserialize)]
pub(crate) struct BrowserExtSupportData {
    extension_use_enabled: bool,
    allowed_browsers: Vec<String>,
}

// App level extension preference
#[derive(Default, Clone, Serialize, Deserialize, Debug)]
pub(crate) struct BrowserExtSupport {
    extension_use_enabled: bool,

    // Browsers granted to use extension to connect OneKeePass app
    // Still we need to enable browser ext connection to use individual database separately

    // If a browser name is in the allowed list, then we to add native message config file - org.onekeepass.onekeepass_browser.json
    // Need to remove native message config file when the browser name is removed from the list
    allowed_browsers: Vec<String>,

    // We do not get this value from frontend when we update this preference through 'update' fn
    // We update this field through 'user_confirmation' fn separately
    #[serde(skip)]
    user_confirmed_browsers: HashSet<String>,
}

impl BrowserExtSupport {

    pub(crate) fn is_extension_use_enabled(&self, browser_id:&str) -> bool{
        // self.extension_use_enabled && self.allowed_browsers.contains(&FIREFOX.to_string()) 
        let val = browser_id.to_string();
        self.extension_use_enabled && self.allowed_browsers.contains(&val) && self.user_confirmed_browsers.contains(&val)
    }

    // Need user's permission first time the browser extension tries to connect
    // It is assumed that the user has already checked the browsers to use with app after enabling the ap level extension use 
    pub(super) fn user_confirmation(&mut self,browser_id:&str, confirmed:bool) {
        if confirmed {
            // We need to this browser id so that we do not ask for the user permission again
            self.user_confirmed_browsers.insert(browser_id.to_string());
            browser_service::run_verifier(true);
        }
        else {
            // User has rejected the connection
            self.user_confirmed_browsers.remove(&browser_id.to_string());
            browser_service::run_verifier(false);
        }
    }

    // Called when the app level "Browser extension support" settings are changed
    pub(super) fn update(&mut self, other: BrowserExtSupportData) -> Result<()> {
        log::debug!("BrowserExtSupport update is called ");
        self.extension_use_enabled = other.extension_use_enabled;
        self.allowed_browsers = other.allowed_browsers;

        if self.extension_use_enabled {
            // TODO: Need to call browser specific config file writing
            if self.allowed_browsers.contains(&FIREFOX.to_string()) {
                // Extension is enabled at the app level and browser Firefox is ext support enabled

                log::debug!("Writing the firefox config....");

                FirefoxNativeMessagingConfig::write()?;

                // As app level ext is enabled, we need to start the app side Endpoint to listen messages from proxy
                start_proxy_handler();
            } else {
                // Extension is enabled at the app level and but browser Firefox ext support is disabled
                // log::debug!("Removing the firefox config...");
                let _ = FirefoxNativeMessagingConfig::remove();

                // If firefox is removed from the allowed list, we need to remove it from user confirmed list also
                // So that next time if user enables firefox in the allowed list, we will ask for permission again
                // log::debug!("Removing the firefox from user confirmed list as it is removed from allowed list");
                self.user_confirmed_browsers.remove(&FIREFOX.to_string());
            }
        } else {
            self.allowed_browsers = vec![];
            self.user_confirmed_browsers = HashSet::default();

            // log::debug!("Removing the firefox config as extension_use_enabled is disabled");

            // TODO: Need to call browser specific config file remove calls
            let _ = FirefoxNativeMessagingConfig::remove();
        }

        Ok(())
    }

    // Called onetime to start the extension proxy listener from app side when the app starts
    pub(crate) fn start_proxy_handling_service(&self, ) {
        // Assuming 'allowed_browsers' will have one or more  browser ids
        if self.extension_use_enabled {
            // Here we are assuming the native messsage config file is already written/copied 
            // to browser specific location
            // TODO: Ensure that the config files are copied if required for all enabled browsers   
            start_proxy_handler();
        }
    }


}

// For now this feature is not used in the UI

// Browser permision for each individual database needs to be enabled or disabled
// #[derive(Default, Clone, Serialize, Deserialize, Debug)]
// pub(crate) struct DatabaseBrowserExtSupport {
//     db_key: String,
//     // List of browsers granted to use extension to connect to this database in 'db_key'
//     allowed_browsers: Vec<String>,
// }
