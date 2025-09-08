use serde::Serialize;

use onekeepass_core::error::{self, Result};

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
        let config_file_full_name = firefox_native_messaging_config_full_name()?;
        let p = proxy_full_path()?.to_string_lossy().to_string();
        let config = FirefoxNativeMessagingConfig {
            allowed_extensions: vec!["onekeepass@gmail.com"],
            description: "OneKeePass integration with native messaging support",
            name: "org.onekeepass.onekeepass_browser",
            path: &p,
            type_of_app: "stdio",
        };

        let json_str = serde_json::to_string_pretty(&config)?;

        // log::debug!("Config json is {}", json_str);
        // log::debug!("Will write to the file {}", &config_file_full_name);

        std::fs::write(&config_file_full_name, json_str)?;

        log::info!("Wrote the config file {} ", &config_file_full_name);

        Ok(())
    }

    // Called to delete the native messaging config file 
    pub(crate) fn remove() -> Result<()>  {
        let config_file_full_name = firefox_native_messaging_config_full_name()?;
        std::fs::remove_file(&config_file_full_name)?;
        log::debug!("Removed the config file {} ", &config_file_full_name);
        Ok(())
    }

}

// Based on https://www.reddit.com/r/tauri/comments/1l29c29/getting_absolute_path_of_sidecar_binary/
// Other refs:
// https://github.com/tauri-apps/tauri/issues/12621
// https://github.com/tauri-apps/plugins-workspace/issues/1333
// https://github.com/tauri-apps/plugins-workspace/issues/2151
// https://www.reddit.com/r/tauri/comments/11adbst/path_to_current_executables_directory/

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


use std::env;
use std::path::{Path, PathBuf};

const OKP_NATIVE_MESSAING_CONFIG_FILE_NAME: &str = "org.onekeepass.onekeepass_browser.json";

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
                let path  = home.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                full_name = path.to_string_lossy().to_string();
            }
        } else if #[cfg(target_os = "linux")] {
            if let Some(mut home) = env::home_dir() {
                home.push(".mozilla/native-messaging-hosts");
                let path = home.join(OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
                full_name = path.to_string_lossy().to_string();
            }
        }
        else if #[cfg(target_os = "windows")] {
            full_name = format!(r#"HKEY_CURRENT_USER\SOFTWARE\Mozilla\NativeMessagingHosts\{}"#, OKP_NATIVE_MESSAING_CONFIG_FILE_NAME);
        }
    }

    Ok(full_name)
}

/*
pub(crate) fn write_native_app_config() {
    let app_dir = std::env::current_exe()
        .unwrap()
        .parent()
        .unwrap()
        .to_path_buf();
    let bin = match std::env::consts::OS {
        "windows" => "onekeepass-proxy.exe",
        _ => "onekeepass-proxy",
    };
    let proxy_full_path = app_dir.join(bin);

    log::debug!("The proxy_full_path is {:?}", proxy_full_path);
}
*/

/*

// Function to demonstrate how to iterate through potential locations
// and find actual host manifest files.
fn find_host_manifests() {
    println!("Searching for Firefox Native Messaging hosts...");
    let search_paths = get_firefox_native_messaging_paths();

    for path in &search_paths {
        if path.exists() && path.is_dir() {
            println!("- Checking directory: {}", path.display());
            match std::fs::read_dir(path) {
                Ok(entries) => {
                    for entry in entries.flatten() {
                        if entry.path().extension().and_then(|s| s.to_str()) == Some("json") {
                            println!("  Found manifest: {}", entry.path().display());
                        }
                    }
                }
                Err(e) => eprintln!("  Error reading directory {}: {}", path.display(), e),
            }
        }
    }

    #[cfg(target_os = "windows")]
    {
        use winreg::enums::*;
        use winreg::RegKey;

        println!("- Checking Windows Registry...");

        let mut check_registry = |hkey: RegKey| {
            let path = r"Software\Mozilla\NativeMessagingHosts";
            if let Ok(nm_hosts_key) = hkey.open_subkey_with_flags(path, KEY_READ) {
                for key_name in nm_hosts_key.enum_keys().flatten() {
                    let full_key_path = format!("{}\\{}", path, key_name);
                    println!("  Found registry key: {}", full_key_path);
                    if let Ok(sub_key) = nm_hosts_key.open_subkey(&key_name) {
                        if let Ok(manifest_path) = sub_key.get_value::<String, _>("") {
                            println!("    Manifest Path: {}", manifest_path);
                        }
                    }
                }
            }
        };

        check_registry(RegKey::predef(HKEY_CURRENT_USER));
        check_registry(RegKey::predef(HKEY_LOCAL_MACHINE));
    }
}

fn main() {
    find_host_manifests();
}
*/

/*
{
    "allowed_extensions": [
        "onekeepass@gmail.com"
    ],
    "description": "OneKeePass integration with native messaging support",
    "name": "org.onekeepass.onekeepass_browser",
    "path": "/Users/jeyasankar/Development/repositories/github/OneKeePass-Organization/desktop/src-tauri/target/debug/onekeepass-proxy",
    "type": "stdio"
}

*/
