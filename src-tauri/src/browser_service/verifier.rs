use std::{
    collections::HashMap,
    sync::{Arc, OnceLock},
};

use futures_util::future::BoxFuture;
use tauri::{Emitter, Manager};

use crate::{app_state, constants};

// Called when user accepts or rejects the first time browser ext connection
pub(crate) fn run_verifier(confirmed: bool) {
    tauri::async_runtime::spawn(async move {
        ConnectionVerifier::run_verifier_and_remove(confirmed).await;
    });
}

static VERIFIER: OnceLock<tokio::sync::Mutex<Option<Arc<ConnectionVerifier>>>> = OnceLock::new();

// Async callback stored as trait object returning a boxed future
type AsyncCallback = Arc<dyn Fn(bool) -> BoxFuture<'static, ()> + Send + Sync>;

pub(crate) struct ConnectionVerifier {
    callback: AsyncCallback,
}

impl ConnectionVerifier {
    pub(crate) fn new(
        callback: impl Fn(bool) -> BoxFuture<'static, ()> + Send + Sync + 'static,
    ) -> Self {
        Self {
            callback: Arc::new(callback),
        }
    }

    // The verifier is stored so that it can be run later after user confirms or rejects extension
    // connection request from UI
    async fn store_verifier(self) {
        if let Some(ver) = VERIFIER.get() {
            let mut guard = ver.lock().await;
            *guard = Some(Arc::new(self));
            log::debug!("The verifier is stored");
        } else {
            // Should be done once only
            let r = VERIFIER.set(tokio::sync::Mutex::new(Some(Arc::new(self))));

            // Should not happen!
            if let Err(_) = r {
                log::error!("VERIFIER.set is called multiple times");
                // panic!();
            }
        }
    }

    // Check whether user has allowed the browser to connect using app level browser specific flag (enabled/disabled)
    pub(crate) async fn run_verifier(self, browser_id: &str) {
        log::debug!("In run_verifier...");

        // If enabled, then call the 'callback' immediately
        if app_state::AppState::state_instance().is_browser_extension_use_enabled(browser_id) {
            log::debug!("Verfied state is true");

            // tokio::time::sleep(tokio::time::Duration::from_millis(1000)).await;

            // Already user has confirmed to use this browser connection at the app level
            self.run(true).await;
        } else {
            log::debug!("Verfied state is false. Storing the verifier for later use");
            // The verifier is stored so that it can be run after user confirms or rejects
            self.store_verifier().await;

            // Should something like 'async_send_connection_request' be called here
            // so that OneKeePass app is brought to the front and ask user to confirm extension use?

            send_browser_connection_request(browser_id);
        }
    }

    // Should be called from frontend after user confirms (true) or rejects (false) and that choice is
    // stored in app preference for later use - See 'VERIFIED' flag use above
    async fn run_verifier_and_remove(confirmed: bool) {
        // Take the verifier out under the lock, then drop the lock before await
        log::debug!("In run_verifier_and_remove...");   
        let verifier = {
            let g = VERIFIER.get();

            if g.is_none() {
                log::error!("Fn run_verifier_and_remove is called before VERIFIER is set ");
                return;
            }
            let mut guard = VERIFIER.get().unwrap().lock().await;
            guard.take()
        };

        if let Some(verifier) = verifier {
            log::debug!("Running the stored verifier after user confirmation");
            verifier.run(confirmed).await;
        } else {
            log::error!("No verifier is found when run_verifier_and_remove is called");
        }
    }

    #[inline]
    async fn run(&self, confirmed: bool) {
        // Check whether user has allowed the browser to connect using
        // app level browser specific flag (enabled/disabled)
        // If enabled, then call the 'callback'
        (self.callback)(confirmed).await;
    }
}

// Send an event to the front end which brigs the app to focus and expects user's input
// to continue the next action
fn send_browser_connection_request(browser_id: &str) {
    let win = app_state::AppState::global_app_handle()
        .get_webview_window(constants::window_labels::MAIN_WINDOW_LABEL)
        .unwrap();
    let args = {
        let mut m = HashMap::<String, String>::new();
        m.insert("browser_id".to_string(), browser_id.to_string());
        m
    };
    let _ = win.emit(
        constants::event_names::BROWSER_CONNECTION_REQUEST_EVENT,
        args,
    );
}

/*

// Showing to open a dialog from tauri and bring the app to focus

fn send_connection_request() {
    use tauri_plugin_dialog::{DialogExt, MessageDialogButtons};

    let _answer = app_state::AppState::global_app_handle()
        .dialog()
        .message("Browser connection is requested")
        .title("Browser connection")
        .buttons(MessageDialogButtons::OkCancel)
        .blocking_show();
}

// Another way of bringing the app to focus

async fn async_send_connection_request() {
    tauri::async_runtime::spawn(async move {
        let win = app_state::AppState::global_app_handle()
            .get_webview_window(constants::window_labels::MAIN_WINDOW_LABEL)
            .unwrap();

        let _ = win.set_always_on_top(true);

        let _ = win.set_focus();

        // Not sure the sending event to frontend is required or not
        let _ = win.emit(
            constants::event_names::BROWSER_CONNECTION_REQUEST_EVENT,
            HashMap::<String, String>::new(),
        );

        // send_connection_request();
    });
}

pub(crate) fn reset_main_window_always_on_top() {
    let win = app_state::AppState::global_app_handle()
        .get_webview_window(constants::window_labels::MAIN_WINDOW_LABEL)
        .unwrap();

    let _ = win.set_always_on_top(false);
}

*/
