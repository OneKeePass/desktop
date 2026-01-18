use serde::Serialize;
use std::env;
use std::path::PathBuf;

use onekeepass_core::error::{self, Result};

#[cfg(target_os = "windows")]
use winreg::{enums::HKEY_CURRENT_USER, RegKey};

use crate::browser_service::{CHROME, FIREFOX, message::Response};

cfg_if::cfg_if! {
    if #[cfg(feature = "onekeepass-dev")] {
        const OKP_NATIVE_APP_NAME: &str = "org.onekeepass.onekeepass_browser_dev";
    } else {
        const OKP_NATIVE_APP_NAME: &str = "org.onekeepass.onekeepass_browser";
    }
}

cfg_if::cfg_if! {
    if #[cfg(feature = "onekeepass-dev")] {
        const OKP_NATIVE_MESSAING_CONFIG_FILE_NAME: &str = "org.onekeepass.onekeepass_browser_dev.json";
    } else {
        const OKP_NATIVE_MESSAING_CONFIG_FILE_NAME: &str = "org.onekeepass.onekeepass_browser.json";
    }
}

// Based on https://www.reddit.com/r/tauri/comments/1l29c29/getting_absolute_path_of_sidecar_binary/
// Other refs:
// https://github.com/tauri-apps/tauri/issues/12621
// https://github.com/tauri-apps/plugins-workspace/issues/1333
// https://github.com/tauri-apps/plugins-workspace/issues/2151
// https://www.reddit.com/r/tauri/comments/11adbst/path_to_current_executables_directory/

// Determine the full path of the proxy binary
fn proxy_full_path() -> Result<PathBuf> {
    let app_dir = std::env::current_exe()?;

    let parent = app_dir
        .parent()
        .ok_or_else(|| error::Error::DataError("No parent dir is found"))?;

    let bin = match std::env::consts::OS {
        "windows" => "onekeepass-proxy.exe",
        _ => "onekeepass-proxy",
    };
    let full_path = parent.join(bin);

    Ok(full_path)
}

#[derive(Serialize)]
pub(crate) struct FirefoxNativeMessagingConfig<'a> {
    allowed_extensions: Vec<&'a str>,
    description: &'a str,
    name: &'a str,
    path: &'a str,
    #[serde(rename(serialize = "type"))]
    type_of_app: &'a str,
}

impl<'a> FirefoxNativeMessagingConfig<'a> {
    // TODO: Need to do platform specifc native message config

    pub(crate) fn write() -> Result<()> {
        let config_file_full_name = Self::firefox_native_messaging_config_full_name()?;
        
        let proxy_executable_path = proxy_full_path()?.to_string_lossy().to_string();
        log::debug!(
            "Firefox proxy executable path is {} ",
            &proxy_executable_path
        );

        let config = FirefoxNativeMessagingConfig {
            allowed_extensions: vec!["onekeepass@gmail.com"],
            description: "OneKeePass integration with native messaging support",
            name: OKP_NATIVE_APP_NAME,
            path: &proxy_executable_path,
            type_of_app: "stdio",
        };

        let json_str = serde_json::to_string_pretty(&config)?;

        // log::debug!("Config json is {}", json_str);
        // log::debug!("Will write to the file {}", &config_file_full_name);

        log::info!(
            "Going to write mozilla native messaging the config file {} ",
            &config_file_full_name
        );

        std::fs::write(&config_file_full_name, json_str)?;

        log::info!(
            "Wrote the mozilla native messaging config file {} ",
            &config_file_full_name
        );

        #[cfg(target_os = "windows")]
        Self::write_win_reg_value(&config_file_full_name)?;

        Ok(())
    }

    // Called to delete the native messaging config file
    pub(crate) fn remove() -> Result<()> {
        let config_file_full_name = Self::firefox_native_messaging_config_full_name()?;
        std::fs::remove_file(&config_file_full_name)?;
        log::debug!("Removed the config file {} ", &config_file_full_name);

        #[cfg(target_os = "windows")]
        Self::delete_win_reg_key()?;

        // Send this message to the browser extension to disconnect any existing 
        // connection as user has diabled this browser use
        Response::disconnect(FIREFOX);

        Ok(())
    }

    // Determines the full path of the firefox native messaging config file for all platforms
    // Ref: https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Native_manifests#manifest_location
    fn firefox_native_messaging_config_full_name() -> Result<String> {
        // #[allow(unused_mut)]
        // let mut path: PathBuf = PathBuf::new();

        #[allow(unused_mut)]
        let mut full_name = String::default();

        cfg_if::cfg_if! {
            if #[cfg(target_os = "macos")] {
                if let Some(mut home) = env::home_dir() {
                    home.push("Library/Application Support/Mozilla/NativeMessagingHosts");
                    // It is seen that when the firefox installation does not have
                    // any NativeMessagingHosts sub dir, the config writting failed
                    // We need to create the sub dir it does not exist
                    if !home.exists() {
                        let r = std::fs::create_dir_all(&home);
                        log::info!("Created mozilla native messaging config dir {:?} with result {:?}",&home,&r);
                    }
                    let path  = home.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                    full_name = path.to_string_lossy().to_string();
                }
            } else if #[cfg(target_os = "linux")] {
                if let Some(mut home) = env::home_dir() {
                    home.push(".mozilla/native-messaging-hosts");
                    if !home.exists() {
                        let r = std::fs::create_dir_all(&home);
                        log::debug!("Created mozilla proxy location dir {:?} with result {:?}",&home,&r);
                    }
                    let path = home.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                    full_name = path.to_string_lossy().to_string();
                }
            }
            else if #[cfg(target_os = "windows")] {
                if let Some(mut okp_dir) = dirs::config_local_dir() {
                    // Need to use Firefox specific folder where the Firefox native messaging config file is created
                    okp_dir.push(FIREFOX);
                    okp_dir.push("OneKeePass");
                    
                    // let okp_dir = home.join("OneKeePass");

                    if !okp_dir.exists() {
                        let _r = std::fs::create_dir_all(&okp_dir);
                        log::debug!("Created dir {:?}",&okp_dir);
                    }
                    let path = okp_dir.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                    full_name = path.to_string_lossy().to_string();
                }
            }
        }

        Ok(full_name)
    }
    // Writes the windows registry entry for firefox native messaging host
    #[cfg(target_os = "windows")]
    fn write_win_reg_value(manifest_path_str: &str) -> Result<()> {
        let hkey_current_user = RegKey::predef(HKEY_CURRENT_USER);
        let reg_path = format!(
            "Software\\Mozilla\\NativeMessagingHosts\\{}",
            OKP_NATIVE_APP_NAME
        );

        let (key, disposition) = hkey_current_user.create_subkey(&reg_path)?;

        println!(
            "Key {:?} is created with disposition {:?}",
            &key, &disposition
        );

        key.set_value("", &manifest_path_str)?;
        println!("Created registry key at HKCU\\{}", reg_path);

        Ok(())
    }

    #[cfg(target_os = "windows")]
    fn delete_win_reg_key() -> Result<()> {
        let hkey_current_user = RegKey::predef(HKEY_CURRENT_USER);
        let reg_path = format!(
            "Software\\Mozilla\\NativeMessagingHosts\\{}",
            OKP_NATIVE_APP_NAME
        );

        hkey_current_user.delete_subkey(&reg_path)?;
        println!("Sub key {} is deleted ", &reg_path);
        Ok(())
    }
}

const CHROME_EXTENSION_ID1: &str = "ijkbdjdmmmbkjbdmmlcejonhmjnkhkka";
const CHROME_EXTENSION_ID2: &str = "cmdmojmbfcpkloflnjkkdjcflaidangh";

#[derive(Serialize)]
pub(crate) struct ChromeNativeMessagingConfig<'a> {
    allowed_origins: Vec<&'a str>,
    description: &'a str,
    name: &'a str,
    path: &'a str,
    #[serde(rename(serialize = "type"))]
    type_of_app: &'a str,
}

impl<'a> ChromeNativeMessagingConfig<'a> {
    pub(crate) fn write() -> Result<()> {
        let config_file_full_name = Self::chrome_native_messaging_config_full_name()?;
        
        let proxy_executable_path = proxy_full_path()?.to_string_lossy().to_string();

        log::debug!(
            "Chrome proxy executable path is {} ",
            &proxy_executable_path
        );

        let cid1 = format!("chrome-extension://{}/", CHROME_EXTENSION_ID1);
        let cid2 = format!("chrome-extension://{}/", CHROME_EXTENSION_ID2);
        let config = ChromeNativeMessagingConfig {
            allowed_origins: vec![&cid1, &cid2],
            description: "OneKeePass integration with native messaging support",
            name: OKP_NATIVE_APP_NAME,
            path: &proxy_executable_path,
            type_of_app: "stdio",
        };
        let json_str = serde_json::to_string_pretty(&config)?;

        log::info!(
            "Going to write chrome native messaging the config file {} ",
            &config_file_full_name
        );

        std::fs::write(&config_file_full_name, json_str)?;

        #[cfg(target_os = "windows")]
        Self::write_win_reg_value(&config_file_full_name)?;

        log::info!(
            "Wrote the native messaging config file {} ",
            &config_file_full_name
        );

        Ok(())
    }

    pub(crate) fn remove() -> Result<()> {
        let config_file_full_name = Self::chrome_native_messaging_config_full_name()?;
        std::fs::remove_file(&config_file_full_name)?;
        log::debug!("Removed the config file {} ", &config_file_full_name);

        #[cfg(target_os = "windows")]
        Self::delete_win_reg_key()?;

        // Send this message to the browser extension to disconnect any existing 
        // connection as user has diabled this browser use
        Response::disconnect(CHROME);

        Ok(())
    }

    // Determines the full path of the chrome native messaging config file for all platforms
    // Ref: https://developer.chrome.com/docs/extensions/mv3/nativeMessaging/#native-messaging-host-location
    // https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging#native-messaging-host-location
    // https://stackoverflow.com/questions/20257415/where-do-chrome-and-firefox-look-for-native-messaging-manifest-files-on-different
    fn chrome_native_messaging_config_full_name() -> Result<String> {
        #[allow(unused_mut)]
        let mut full_name = String::default();

        cfg_if::cfg_if! {
            if #[cfg(target_os = "macos")] {
                if let Some(mut home) = env::home_dir() {
                    home.push("Library/Application Support/Google/Chrome/NativeMessagingHosts");

                    if !home.exists() {
                        let r = std::fs::create_dir_all(&home);
                        log::info!("Created chrome native messaging config dir {:?} with result {:?}",&home,&r);
                    }

                    let path  = home.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                    full_name = path.to_string_lossy().to_string();
                }
            } else if #[cfg(target_os = "linux")] {
                if let Some(mut home) = env::home_dir() {
                    home.push(".config/google-chrome/NativeMessagingHosts");
                    if !home.exists() {
                        let r = std::fs::create_dir_all(&home);
                        log::debug!("Created google-chrome proxy location dir {:?} with result {:?}",&home,&r);
                    }
                    let path = home.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                    full_name = path.to_string_lossy().to_string();
                }
            }
            else if #[cfg(target_os = "windows")] {
                if let Some(mut okp_dir) = dirs::config_local_dir() {
                    // Need to use Chrome specific folder where the Chrome native messaging config file is created
                    okp_dir.push(CHROME);
                    okp_dir.push("OneKeePass");
                    // let okp_dir = home.join("OneKeePass");
                    if !okp_dir.exists() {
                        let _r = std::fs::create_dir_all(&okp_dir);
                        log::debug!("Created dir {:?}",&okp_dir);
                    }
                    let path = okp_dir.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                    full_name = path.to_string_lossy().to_string();
                }
            }
        }

        Ok(full_name)
    }

    // Writes the windows registry entry for chrome native messaging host
    #[cfg(target_os = "windows")]
    fn write_win_reg_value(manifest_path_str: &str) -> Result<()> {
        let hkey_current_user = RegKey::predef(HKEY_CURRENT_USER);
        let reg_path = format!(
            "Software\\Google\\Chrome\\NativeMessagingHosts\\{}",
            OKP_NATIVE_APP_NAME
        );

        let (key, disposition) = hkey_current_user.create_subkey(&reg_path)?;

        println!(
            "Key {:?} is created with disposition {:?}",
            &key, &disposition
        );

        key.set_value("", &manifest_path_str)?;
        println!("Created registry key at HKCU\\{}", reg_path);

        Ok(())
    }

    #[cfg(target_os = "windows")]
    fn delete_win_reg_key() -> Result<()> {
        let hkey_current_user = RegKey::predef(HKEY_CURRENT_USER);
        let reg_path = format!(
            "Software\\Google\\Chrome\\NativeMessagingHosts\\{}",
            OKP_NATIVE_APP_NAME
        );

        hkey_current_user.delete_subkey(&reg_path)?;
        println!("Sub key {} is deleted ", &reg_path);
        Ok(())
    }
}
