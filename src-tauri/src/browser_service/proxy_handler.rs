use std::sync::Arc;

use futures_util::StreamExt as _;
use tipsy::{Connection, Endpoint, OnConflict, ServerId};
use tokio::{
    io::{split, AsyncReadExt, AsyncWriteExt, ReadHalf, WriteHalf},
    sync::mpsc,
};

use crate::browser_service::{
    key_share::{BrowserServiceRx, BrowserServiceTx},
    message::Request,
};
use crate::sandbox;

cfg_if::cfg_if! {
    if #[cfg(feature = "onekeepass-dev")] {
        const NATIVE_MESSAGE_CONNECTION_NAME: &str = "okp_browser_ipc_dev";
    } else {
        const NATIVE_MESSAGE_CONNECTION_NAME: &str = "okp_browser_ipc";
    }
}

const BUFFER_SIZE: usize = 1024 * 1024;

// Warn when an outbound response approaches the 1 MB native-messaging cap that
// Chrome/Firefox enforce on host -> extension messages. The lazy-fetching
// keeps responses small in practice, so a hit here signals a payload regression
// before users actually trip the browser-side limit.
const SIZE_WARN_BYTES: usize = 800 * 1024;

async fn read_framed_message(
    reader: &mut ReadHalf<Connection>,
) -> std::io::Result<Vec<u8>> {
    let mut length_bytes = [0u8; 4];
    reader.read_exact(&mut length_bytes).await?;

    let message_length = u32::from_ne_bytes(length_bytes) as usize;
    if message_length == 0 {
        return Ok(Vec::new());
    }
    if message_length > BUFFER_SIZE {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            "Extension request size exceeded the limit",
        ));
    }

    let mut message_bytes = vec![0u8; message_length];
    reader.read_exact(&mut message_bytes).await?;
    Ok(message_bytes)
}

// Reads the bytes from the proxy app and sends to the request handler (Request::handle_input_message).
// The request handler after processing sends the response to a channel 'sender'
fn handle_input(mut reader: ReadHalf<Connection>, sender: Arc<BrowserServiceTx>) {
    tauri::async_runtime::spawn(async move {
        loop {
            match read_framed_message(&mut reader).await {
                Ok(body) if body.is_empty() => {
                    log::info!("Proxy connection closed (zero-length read)");
                    break;
                }
                Ok(body) => match String::from_utf8(body) {
                    Ok(input_message) => {
                        Request::handle_input_message(input_message, sender.clone()).await;
                    }
                    Err(e) => {
                        log::error!("Converting message bytes to string error {}", &e);
                    }
                },
                Err(e) => {
                    log::error!("Error in reading stream {}", &e);
                    break;
                }
            }
        }
    });
}

async fn write_framed_message(
    writer: &mut WriteHalf<Connection>,
    message: &str,
) -> std::io::Result<()> {
    let message_bytes = message.as_bytes();
    let message_length = message_bytes.len() as u32;
    writer.write_all(&message_length.to_ne_bytes()).await?;
    writer.write_all(message_bytes).await?;
    writer.flush().await
}

// Receives the request handler's reponse (see Request.handle_input_message) from the channel 'channel_receiver' and then
// writes that response to the proxy app connection writer 'proxy_connection_writer'
fn handle_output(
    mut channel_receiver: BrowserServiceRx,
    proxy_connection_writer: Arc<tokio::sync::Mutex<WriteHalf<Connection>>>,
) {
    // log::debug!("handle_output is called  before spawn");

    let writer = proxy_connection_writer.clone();

    tauri::async_runtime::spawn(async move {
        // log::debug!("In handle_output after spawn before loop");

        // TODO:
        // Need to break this loop when connection to the proxy is no more available and remove session data
        while let Some(message) = channel_receiver.recv().await {
            // log::debug!(" Received message {}", &message);

            let message_byte_len = message.as_bytes().len();
            if message_byte_len > SIZE_WARN_BYTES {
                log::warn!(
                    "Outbound browser-extension response is {} bytes - approaching native-messaging 1 MB cap",
                    message_byte_len
                );
            }

            let mut writer_guard = writer.lock().await;

            if let Err(e) = write_framed_message(&mut writer_guard, &message).await {
                log::error!("Error in writing to the proxy connection: {}", &e);
                log::info!("Breaking the writing loop");
                break;
            }
        }
    });
}

static ENDPOINT_SERVER_RUNNING: std::sync::OnceLock<std::sync::Mutex<bool>> =
    std::sync::OnceLock::new();

fn endpoint_server_started() {
    if let Some(a) = ENDPOINT_SERVER_RUNNING.get() {
        let mut b = a.lock().unwrap();
        *b = true;
    } else {
        let r = ENDPOINT_SERVER_RUNNING.set(std::sync::Mutex::new(true));
        if r.is_err() {
            log::error!(
                "ENDPOINT_SERVER_RUNNING is already set. Should not be called multiple times"
            )
        }
    }
}

fn is_endpoint_server_running() -> bool {
    if let Some(a) = ENDPOINT_SERVER_RUNNING.get() {
        let b = a.lock().unwrap();
        *b
    } else {
        false
    }
}

// Called to connect to the browser native message proxy app endpoint and provides listeners to receive
// messages( or send) messages from (or to) the native message proxy app for all browser extensions
async fn run_server(path: String) {
    log::info!(
        "Proxy listener - Run server is called with path {}, ",
        &path
    );

    // Under macOS App Sandbox (Mac App Store build), tipsy's default location
    // ($TMPDIR) is per-process and unreachable from the browser-spawned proxy.
    // Bind the socket inside the shared App Group container instead. Both ends
    // must hold the application-groups entitlement for this path to be writable.
    let server_id = match sandbox::group_container_path() {
        Some(parent) => ServerId::new(path).parent_folder(parent),
        None => ServerId::new(path),
    };

    log::info!(
        "App side end point connection path (server_id) is {:?}",
        &server_id
    );

    let endpoint = Endpoint::new(server_id, OnConflict::Overwrite).unwrap();

    let incoming = match endpoint.incoming() {
        Ok(incoming) => incoming,
        Err(e) => {
            log::error!("Proxy handler failed to open new socket with error {}", &e);
            return;
        }
    };

    // pins a mutable reference to a value on the stack
    futures_util::pin_mut!(incoming);

    log::info!("Listener to the Browser native message proxy is started ...");

    // Set the flag saying that endpoint server is started
    endpoint_server_started();

    // When each browser's native message proxy app is launched (Ext -> Native message -> Lauches the proxy),
    // the 'next' call returns with the connection to that proxy

    // For example when user uses browser extension of Firefox and Chrome at the same time, each browser extension would
    // have launched its own proxy app and both connects to this endpoint.
    while let Some(result) = incoming.next().await {
        log::info!("Incoming connections is made to native message proxy of a browser");

        // Handles one browser's ext proxy conenction
        match result {
            Ok(stream_to_browser_native_app) => {
                // log::debug!("Connection is estabilished");

                // We get the separated proxy reader and writer
                let (reader_from_browser_native_app, writer_to_browser_native_app) =
                    split(stream_to_browser_native_app);

                // Create the channel that is used for communication between
                // request handling task () and response sending task
                let (tx, rx): (BrowserServiceTx, BrowserServiceRx) = mpsc::channel(32);

                // Sending (message producing) side is shared in many async fns
                let channel_sender = Arc::new(tx);

                // Need to wrap the channel writer in Arc so that it can be moved to writing task's loop (see handle_output)
                let shared_writer = Arc::new(tokio::sync::Mutex::new(writer_to_browser_native_app));

                // Reads incoming message
                handle_input(reader_from_browser_native_app, channel_sender);

                // Handles the writing of final received message in 'rx' to native message proxy
                handle_output(rx, shared_writer);

                log::debug!("Created both read_loop and write_loop");
            }

            Err(e) => {
                // unreachable!("ideally")
                log::error!("Error in making incoming connection {}", &e);
            }
        }

        // Will look for the "next" connection from another browser proxy
        log::debug!("Going to wait for the next browser connection");
    }
}

// This should be called to start the backend listener to receive messages / send messages from/to a browser native message proxy app
pub(crate) fn start_proxy_handler() {
    log::debug!("In start_proxy_handler....");
    if !is_endpoint_server_running() {
        tauri::async_runtime::spawn(async move {
            log::debug!(
                "Starting the app side proxy listening service with IPC name {} ",
                NATIVE_MESSAGE_CONNECTION_NAME
            );
            run_server(NATIVE_MESSAGE_CONNECTION_NAME.to_string()).await;
        });
    } else {
        log::debug!("Endpoint server for proxy connetion listener is already running");
    }
}

/*
static OKP_BROWSER_TOKIO_RUNTIME: OnceLock<Arc<Runtime>> = OnceLock::new();

// May be called from multiple threads and this Runtime ref is shared
fn async_runtime() -> &'static Runtime {
    OKP_BROWSER_TOKIO_RUNTIME.get_or_init(|| {
        let runtime = Builder::new_multi_thread()
            //.worker_threads(4)
            .thread_name("okp-browser-async-service")
            .thread_stack_size(2 * 1024 * 1024)
            .enable_all()
            .build()
            .unwrap();
        Arc::new(runtime)
    })
}
*/
