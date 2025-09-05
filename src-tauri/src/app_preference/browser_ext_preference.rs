use std::collections::HashMap;

use serde::{Deserialize, Serialize};

#[derive(Default,Clone, Serialize, Deserialize, Debug)]
pub(crate) struct BrowserSpecificPermission {
    allow_firefox: bool,
    allow_chrome: bool,
    allow_edge: bool,
}

#[derive(Default,Clone, Serialize, Deserialize, Debug)]
pub(crate) struct BrowserExtSupport {
    extension_use_enabled: bool,
    // This is used to add or remove native message config file 
    // org.onekeepass.onekeepass_browser.json
    browser_specific_permission: BrowserSpecificPermission,
}

impl  BrowserExtSupport {
    pub(crate) fn set_extension_use_enabled(&mut self,flag:bool) {
        self.extension_use_enabled = flag;
    }

    pub(crate) fn set_browser_specific_permission(&mut self,permission:BrowserSpecificPermission) {
        self.browser_specific_permission = permission;
    }
}


#[derive(Default,Clone, Serialize, Deserialize, Debug)]
pub(crate) struct DatabaseBrowserExtSupport {
    // Browser permision for each individual database needs to be enabled or disabled
    enabled_databases: HashMap<String, BrowserSpecificPermission>,
}
