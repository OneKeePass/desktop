use onekeepass_core::db_service::WordListLoader;
use onekeepass_core::error::{self, Result};
use tauri::Manager;

use crate::app_state::AppState;

pub(crate) struct WordListLoaderImpl {
  app_handle: tauri::AppHandle,
}

impl WordListLoaderImpl {
  pub(crate) fn new(app_handle: tauri::AppHandle) -> Self {
    Self { app_handle }
  }
}

impl WordListLoader for WordListLoaderImpl {
  fn load_from_resource(&self, wordlist_file_name: &str) -> Result<String> {
    // Though we can get the resouerce dir from 'tauri::AppHandle', we are getting
    // our own AppState from this handle and use it to get the resource dir.
    // In that way our app specific preferences can be accessed if required

    let app_state = self.app_handle.state::<AppState>();
    let Some(rseource_path) = app_state.resource_dir_path() else {
      return Err(error::Error::DataError("No app resource dir is found"));
    };

    // IMPORTANT:
    // We need to add "../resources/public/wordlists" src-tauri/tauri.conf.json
    // in order for the file reading. See also translation::load_language_translations and 'load_custom_svg_icons'
    // for other app level resources handling

    // _up_/resources/public is the resource dir and all wordlist files are found
    // in the sub dir _up_/resources/public/wordlists

    let full_path = rseource_path.join("wordlists").join(wordlist_file_name);

    log::debug!("Going to load the wordlist file {:?}", &full_path);

    Ok(std::fs::read_to_string(&full_path)?)
  }
}

// Previously the following was used in module callback_service_provider.rs so that
// onekeepass_core we make use of the 'CoreCommonCallbackService' impl
// With the new 'WordListLoader' trait use ( passed as arg to 'generate' fn), this is no more
// required. Keeping it here in case, we can use the conccept if any other need comes
/*

use std::sync::Arc;

use log::debug;
use onekeepass_core::callback_service::{self, CoreCommonCallbackService};
use onekeepass_core::error::{self, Result};
use tauri::Manager;

use crate::app_state::AppState;

// IMPORTANT:
// Should be called during app init call so that the backend api can make a callback to the Runtime
pub(crate) fn init_callback_service_provider(app_handle: tauri::AppHandle) {
  let instance = Arc::new(CommonCallbackServiceImpl::new(app_handle));
  // In case, we need to hold any reference at this module, then we need to Arc::clone and use it
  // CallbackServiceProvider::init(instance);
  callback_service::initialize_with_provider(instance);
  debug!("init_callback_service_provider is called and CallbackServiceProvider init is done");
}

//#[derive(Default)]
struct CommonCallbackServiceImpl {
  // We need to hold a copy of app handle to get back the AppState and use fields from that
  app_handle: tauri::AppHandle,
}

impl CommonCallbackServiceImpl {
  fn new(app_handle: tauri::AppHandle) -> Self {
    Self { app_handle }
  }
}

impl CoreCommonCallbackService for CommonCallbackServiceImpl {
  fn load_wordlist(&self, wordlist_file_name: &str) -> Result<String> {
    // Though we can get the resouerce dir from 'tauri::AppHandle', we are getting
    // our own AppState from this handle and use it to get the resource dir.
    // In that way our app specific preferences can be accessed if required

    let app_state = self.app_handle.state::<AppState>();
    let Some(rseource_path) = app_state.resource_dir_path() else {
      return Err(error::Error::DataError("No app resource dir is found"));
    };

    // IMPORTANT:
    // We need to add "../resources/public/wordlists" src-tauri/tauri.conf.json
    // in order for the file reading. See also translation::load_language_translations and 'load_custom_svg_icons'
    // for other app level resources handling

    // _up_/resources/public is the resource dir and all wordlist files are found
    // in the sub dir _up_/resources/public/wordlists

    let full_path = rseource_path.join("wordlists").join(wordlist_file_name);

    debug!("Going to load the wordlist file {:?}", &full_path);

    Ok(std::fs::read_to_string(&full_path)?)
  }
}



*/
