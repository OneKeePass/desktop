mod db_calls;
mod key_share;
mod message;
mod proxy_handler;

use core::panic;
use std::{
    collections::HashMap,
    sync::{Arc, OnceLock},
};

use futures_util::future::BoxFuture;
pub(crate) use proxy_handler::start_proxy_handler;
use tauri::{Emitter, Manager};

use crate::{app_state, constants};

// Async callback stored as trait object returning a boxed future
type AsyncCallback = Arc<dyn Fn(bool) -> BoxFuture<'static, ()> + Send + Sync>;

static VERIFIER: OnceLock<tokio::sync::Mutex<Option<Arc<ConnectionVerifier>>>> = OnceLock::new();

///
static VERIFIED: OnceLock<std::sync::Mutex<bool>> = OnceLock::new();

// Simulates the verified state preference storage
pub(crate) async fn simulate_verified_flag_preference(confirmed: bool) {
    if let Some(a) = VERIFIED.get() {
        let mut b = a.lock().unwrap();
        *b = confirmed;
    } else {
        VERIFIED
            .set(std::sync::Mutex::new(confirmed))
            .expect("set_verified is called multiple time!");
    }
}

// Simulates getting the preference stored verified state using a global flag for now.
fn verified_state() -> bool {
    if let Some(a) = VERIFIED.get() {
        let b = a.lock().unwrap();
        *b
    } else {
        false
    }
}

///

pub(crate) async fn run_verifier(confirmed: bool) {
    ConnectionVerifier::run_verifier_and_remove(confirmed).await;
}

struct ConnectionVerifier {
    callback: AsyncCallback,
}

impl ConnectionVerifier {
    fn new(callback: impl Fn(bool) -> BoxFuture<'static, ()> + Send + Sync + 'static) -> Self {
        Self {
            callback: Arc::new(callback),
        }
    }

    async fn store_verifier(verifier: ConnectionVerifier) {
    if let Some(ver) = VERIFIER.get() {
        let mut guard = ver.lock().await;
        *guard = Some(Arc::new(verifier));
        log::debug!("The verifier is stored");
    } else {
        // Should be done once only
        let r = VERIFIER.set(tokio::sync::Mutex::new(Some(Arc::new(verifier))));

        // Should not happen!
        if let Err(_) = r {
            log::debug!("VERIFIER.set is called multiple times");
            panic!();
        }
    }
}

    async fn run_verifier(verifier: ConnectionVerifier) {
        // Check whether user has allowed the browser to connect using
        // app level browser specific flag (enabled/disabled)
        // If enabled, then call the 'callback'
        // Simulate the above condition

        log::debug!("In run_verifier...");
        if verified_state() {
            log::debug!("Verfied state is true");
            // Already user has confirmed to use this browser connection at the app level
            tokio::time::sleep(tokio::time::Duration::from_millis(1000)).await;
            verifier.run(true).await;
        } else {
            log::debug!("Verfied state is false. Storing the verifyer for later use");
            // The verifyer is stored so that it can be run after user confirms or rejects
            Self::store_verifier(verifier).await;
        }
    }

    // Should be called from frontend after user confirms (true) or rejects (false)
    async fn run_verifier_and_remove(confirmed: bool) {
        // Take the verifier out under the lock, then drop the lock before await

        let verifier = {
            let mut guard = VERIFIER
                .get()
                .expect("init_single_worker_store() must be called first")
                .lock()
                .await;
            guard.take()
        };

        if let Some(verifier) = verifier {
            verifier.run(confirmed).await;
        } else {
            eprintln!("no verifier stored");
        }
    }

    async fn run(&self, confirmed: bool) {
        // Check whether user has allowed the browser to connect using
        // app level browser specific flag (enabled/disabled)
        // If enabled, then call the 'callback'

        // Simulate the above condition
        // tokio::time::sleep(tokio::time::Duration::from_millis(1000)).await;

        (self.callback)(confirmed).await;

        // If not
        // 1. Store this 'ConnectionVerifier' to VERIFIER
        // 2. Bring app to focus asking user to allow enabling
        // If the user has enabled browser connection app level, then we will do the confirmation
        // of a specific enabled browser connection one time
        // 3. On user's confirmation, we will call 'callback'

        // (self.callback)().await;
    }
}

fn send_connection_request() {
    use tauri_plugin_dialog::{DialogExt, MessageDialogButtons};

    let _answer = app_state::AppState::global_app_handle()
        .dialog()
        .message("Browser connection is requested")
        .title("Browser connection")
        .buttons(MessageDialogButtons::OkCancel)
        .blocking_show();
}

// fn send_connection_request_event() {
//     app_state::AppState::global_app_handle().get_webview_window("main").unwrap().f
// }

async fn async_send_connection_request() {
    tauri::async_runtime::spawn(async move {
        let win = app_state::AppState::global_app_handle()
            .get_webview_window(constants::window_labels::MAIN_WINDOW_LABEL)
            .unwrap();

        let _ = win.set_always_on_top(true);

        let _ = win.set_focus();

        let _ = win.emit(
            constants::event_names::BROWSER_CONNECTION_REQUEST_EVENT,
            HashMap::<String, String>::new(),
        );

        // send_connection_request();
    });
}

// .buttons(MessageDialogButtons::OkCancelCustom(
//     "Absolutely",
//     "Totally",
// )
