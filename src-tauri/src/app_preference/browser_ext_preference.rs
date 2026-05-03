use std::collections::{HashMap, HashSet};

use serde::{Deserialize, Serialize};

use onekeepass_core::error::{self, Result};

use crate::browser_service::{
    self, start_proxy_handler, ChromeNativeMessagingConfig, FirefoxNativeMessagingConfig, CHROME,
    FIREFOX,
};

#[derive(Debug, Deserialize)]
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

    // Per-browser security-scoped folder bookmarks (base64-encoded) used when
    // the app is running under macOS App Sandbox (MAS build). The bookmark
    // gives persistent write access to the browser's NativeMessagingHosts dir
    // without requiring a new NSOpenPanel grant on every launch.
    // Key: browser id (e.g. "firefox", "chrome"). Value: base64 bookmark blob.
    // Absent on non-macOS and on DMG/Developer-ID builds; #[serde(default)]
    // ensures old preference.toml files without this key deserialize to an
    // empty map.
    #[serde(default)]
    browser_dir_bookmarks: HashMap<String, String>,
}

impl BrowserExtSupport {
    pub(crate) fn is_extension_use_enabled(&self, browser_id: &str) -> bool {
        let val = browser_id.to_string();
        self.extension_use_enabled
            && self.allowed_browsers.contains(&val)
            && self.user_confirmed_browsers.contains(&val)
    }

    pub(crate) fn _extension_use_enabled(&self,) -> bool {
        self.extension_use_enabled
    }

    pub(crate) fn _is_allowed_browser(&self, browser_id: &str) -> bool  {
        self.allowed_browsers.contains(&browser_id.to_string())
    }

    pub(crate) fn store_browser_dir_bookmark(&mut self, browser_id: &str, b64: String) {
        self.browser_dir_bookmarks.insert(browser_id.to_string(), b64);
    }

    pub(crate) fn clear_browser_dir_bookmark(&mut self, browser_id: &str) {
        self.browser_dir_bookmarks.remove(browser_id);
    }

    pub(crate) fn has_dir_bookmark(&self, browser_id: &str) -> bool {
        self.browser_dir_bookmarks.contains_key(browser_id)
    }

    // Writes the native-messaging manifest for `browser_id`, establishing the
    // scoped folder access if sandboxed. Called after the user has picked the
    // folder via NSOpenPanel and the bookmark has already been stored.
    // Also (re)starts the proxy handler on success.
    pub(crate) fn write_browser_manifest_for(&mut self, browser_id: &str) -> Result<()> {
        match browser_id {
            FIREFOX => {
                self.write_manifest_with_scope(FIREFOX, FirefoxNativeMessagingConfig::write)?;
            }
            CHROME => {
                self.write_manifest_with_scope(CHROME, ChromeNativeMessagingConfig::write)?;
            }
            _ => {
                return Err(error::Error::UnexpectedError(format!(
                    "Unknown browser id '{}'",
                    browser_id
                )));
            }
        }
        start_proxy_handler();
        Ok(())
    }

    // Need user's permission first time the browser extension tries to connect
    // It is assumed that the user has already checked the browsers to use with app after enabling the ap level extension use
    pub(super) fn user_confirmation(&mut self, browser_id: &str, confirmed: bool) {
        if confirmed {
            // We need to this browser id so that we do not ask for the user permission again
            self.user_confirmed_browsers.insert(browser_id.to_string());
            browser_service::run_verifier(true);
        } else {
            // User has rejected the connection
            self.user_confirmed_browsers.remove(&browser_id.to_string());
            browser_service::run_verifier(false);
        }
    }

    // Wraps a manifest file operation (write or remove) with a security-scoped
    // folder access grant when running under macOS App Sandbox.
    //
    // Non-sandboxed: calls `op` directly.
    // Sandboxed, bookmark present: resolves the bookmark, calls `op`, releases.
    //   If the OS flagged the bookmark as stale, the refreshed blob is persisted.
    // Sandboxed, bookmark absent: returns BrowserManifestNeedsUserGrant so
    //   the caller can surface a folder-picker dialog to the user.
    fn write_manifest_with_scope(
        &mut self,
        browser_id: &str,
        op: impl FnOnce() -> Result<()>,
    ) -> Result<()> {
        if !crate::sandbox::is_sandboxed() {
            return op();
        }

        let b64 = match self.browser_dir_bookmarks.get(browser_id) {
            Some(b) => b.clone(),
            None => {
                // Sentinel prefix parsed by the cljs bg-update-preference callback to
                // distinguish this from a generic error and show the folder-picker dialog.
                // Append the actual (non-~-abbreviated) manifest dir after "|||" so the
                // cljs explainer dialog can show the real path to the user.
                let actual_dir = crate::sandbox::browser_manifest_dir(browser_id)
                    .map(|p| p.to_string_lossy().into_owned())
                    .unwrap_or_default();
                return Err(error::Error::UnexpectedError(format!(
                    "BrowserManifestNeedsUserGrant:{}|||{}",
                    browser_id, actual_dir
                )));
            }
        };

        let (handle, refreshed) = crate::bookmarks::resolve_and_start(&b64)
            .map_err(|e| error::Error::UnexpectedError(e.to_string()))?;

        if let Some(new_b64) = refreshed {
            self.browser_dir_bookmarks
                .insert(browser_id.to_string(), new_b64);
        }

        let result = op();
        crate::bookmarks::release(handle);
        result
    }

    // Called when the app level "Browser extension support" settings are changed
    pub(super) fn update(&mut self, other: BrowserExtSupportData) -> Result<()> {
        log::debug!("BrowserExtSupport update is called - existing allowed_browsers {:?}, new allowed_browsers {:?}",
            &self.allowed_browsers, &other.allowed_browsers, );

        // Save prior state so we can roll back if writing the manifest fails.
        let orig_enabled = self.extension_use_enabled;

        self.extension_use_enabled = other.extension_use_enabled;

        let allowed_browsers = self.allowed_browsers.clone();

        let result = if self.extension_use_enabled {
            // We call browser specific config file writing/removal first
            let r = self
                .firefox_ext_add_or_remove(&allowed_browsers, &other.allowed_browsers)
                .and_then(|_| {
                    self.chrome_ext_add_or_remove(&allowed_browsers, &other.allowed_browsers)
                });

            if r.is_ok() {
                // Finally update the allowed browsers list. Done only when all writes succeeded.
                self.allowed_browsers = other.allowed_browsers;
            }
            r
        } else {
            self.allowed_browsers = vec![];
            self.user_confirmed_browsers = HashSet::default();

            // Remove all existing browser native messaging config files as app level
            // extension use is disabled. Bookmark scope is used if sandboxed.
            let _ = self.write_manifest_with_scope(FIREFOX, FirefoxNativeMessagingConfig::remove);
            let _ = self.write_manifest_with_scope(CHROME, ChromeNativeMessagingConfig::remove);

            // Clear the stored bookmarks when integration is fully disabled so that
            // the next enable re-prompts via NSOpenPanel (fresh grant).
            self.browser_dir_bookmarks.clear();

            Ok(())
        };

        if result.is_err() {
            self.extension_use_enabled = orig_enabled;
        }

        result
    }

    // Called onetime to start the extension proxy listener from app side when the app starts
    pub(crate) fn start_proxy_handling_service(&self) {
        // Assuming 'allowed_browsers' will have one or more  browser ids
        if self.extension_use_enabled {
            // Here we are assuming the native messsage config file is already written/copied
            // to browser specific location
            // TODO: Ensure that the config files are copied if required for all enabled browsers
            start_proxy_handler();
        }
    }

    fn firefox_ext_add_or_remove(
        &mut self,
        existing_allowed_browsers: &Vec<String>,
        new_allowed_browsers: &Vec<String>,
    ) -> Result<()> {
        // We call the config file writing/removal only if there is a change in allowed_browsers
        if !existing_allowed_browsers.contains(&FIREFOX.to_string())
            && new_allowed_browsers.contains(&FIREFOX.to_string())
        {
            // Extension is enabled at the app level and browser Firefox is ext support enabled

            log::debug!("Writing the firefox config....");

            self.write_manifest_with_scope(FIREFOX, FirefoxNativeMessagingConfig::write)?;

            // As app level ext is enabled, we need to start the app side Endpoint to listen messages from proxy
            start_proxy_handler();
        } else if existing_allowed_browsers.contains(&FIREFOX.to_string())
            && !new_allowed_browsers.contains(&FIREFOX.to_string())
        {
            // This means browser Firefox ext support is disabled and the allowed list does not have firefox
            // Then we remove the any previous config written so that next time
            // if user enables firefox in the allowed list, the config is written again

            // Need to remove any previous user confirmations
            self.user_confirmed_browsers.remove(FIREFOX);

            log::debug!("Removing the firefox config...");
            let r = self.write_manifest_with_scope(FIREFOX, FirefoxNativeMessagingConfig::remove);
            log::debug!(
                "After remove call for firefox native messaging config with result {:?}",
                &r
            );
        }

        Ok(())
    }

    fn chrome_ext_add_or_remove(
        &mut self,
        existing_allowed_browsers: &Vec<String>,
        new_allowed_browsers: &Vec<String>,
    ) -> Result<()> {
        // We call the config file writing/removal only if there is a change in allowed_browsers
        if !existing_allowed_browsers.contains(&CHROME.to_string())
            && new_allowed_browsers.contains(&CHROME.to_string())
        {
            // Extension is enabled at the app level and browser Chrome is ext support enabled

            log::debug!("Writing the chrome config....");

            self.write_manifest_with_scope(CHROME, ChromeNativeMessagingConfig::write)?;

            // As app level ext is enabled, we need to start the app side Endpoint to listen messages from proxy
            start_proxy_handler();
        } else if existing_allowed_browsers.contains(&CHROME.to_string())
            && !new_allowed_browsers.contains(&CHROME.to_string())
        {
            // This means browser Chrome ext support is disabled and the allowed list does not have Chrome
            // Then we remove the any previous config written so that next time
            // if user enables Chrome in the allowed list, the config is written again

            // Need to remove any previous user confirmations
            self.user_confirmed_browsers.remove(CHROME);

            log::debug!("Removing the chrome config...");
            let r = self.write_manifest_with_scope(CHROME, ChromeNativeMessagingConfig::remove);
            log::debug!(
                "After remove call for chrome native messaging config with result {:?}",
                &r
            );
        }

        Ok(())
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
