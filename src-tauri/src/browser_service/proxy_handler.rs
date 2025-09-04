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

const BUFFER_SIZE: usize = 1024 * 1024;

// Reads the bytes from the proxy app and sends to the request handler (Request::handle_input_message). 
// The request handler after processing sends the response to a channel 'sender'
fn handle_input(mut reader: ReadHalf<Connection>, sender: Arc<BrowserServiceTx>) {
    // log::debug!("handle_input is called  before spawn");

    tauri::async_runtime::spawn(async move {
        // let mut buf = [0u8; BUFFER_SIZE];
        // This resulted in error something similar
        // "results in thread 'tokio-runtime-worker' has overflowed its stack"

        // Solutions to avoid that
        // Solution 1
        // Create a tokio runtime (OKP_BROWSER_TOKIO_RUNTIME) with increased stack size in 'thread_stack_size' call

        // Ref:
        // https://blog.cloudflare.com/pin-and-unpin-in-rust/
        // https://rust-lang.github.io/wg-async/vision/submitted_stories/status_quo/alan_runs_into_stack_trouble.html
        // https://rust-dd.com/post/async-rust-explained-pinning-part-2

        // Solution 2
        // Heap allocation and pin the buffer
        // Box::pin allocates the buffer on the heap and pins it.
        // let mut buf = Box::pin([0u8; BUFFER_SIZE]);
        // Then use reader.read(&mut *buf).await
        // To get a &mut [u8], use &mut *buf (dereferencing the Pin<Box<[u8; N]>> to a &mut [u8] slice

        // This solution 2 is required if we want to store the buffer across await points or in self-referential structs

        // Solution 3
        // Create a heap-allocated buffer

        let mut buf = vec![0u8; BUFFER_SIZE];

        // log::debug!("In handle_input 'spawn'  and before loop");

        // TODO:
        // Need to break this loop when connection to the proxy is no more available and the remove session data
        loop {
            // Reads data from proxy app connection
            match reader.read(&mut buf).await {
                Ok(len) => {
                    if len <= BUFFER_SIZE {
                        if len > 0 {
                            // log::debug!("Message size {}", &len);

                            let message_bytes = buf[..len].to_vec();
                            match String::from_utf8(message_bytes) {
                                Ok(input_message) => {
                                    // Handles the received proxy side message data and the handler will send the respnse in channel 'sender'
                                    Request::handle_input_message(input_message, sender.clone())
                                        .await;
                                }
                                Err(e) => {
                                    log::error!("Converting message bytes to string error {} ", &e);
                                }
                            }
                        }
                    }
                }
                Err(e) => {
                    // Should we break from the reading loop ?
                    log::error!("Error in reading stream {}", &e);
                }
            }
        }
    });
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

            let mut writer_guard = writer.lock().await;

            if let Err(e) = writer_guard.write_all(message.as_bytes()).await {
                log::error!("Error in writing to the proxy connection: {}", &e);
                log::info!("Breaking the writing loop");
                break;
            }

            let _ = writer_guard.flush().await;
        }
    });
}

// Called to connect to the browser native message proxy app endpoint and provides listeners to receive
// messages( or send) messages from (or to) the proxy app
async fn run_server(path: String) {
    let endpoint = Endpoint::new(ServerId::new(path), OnConflict::Overwrite).unwrap();

    let incoming = match endpoint.incoming() {
        Ok(incoming) => incoming,
        Err(e) => {
            log::error!("Proxy handler failed to open new socket with error {}", &e);
            return;
        }
    };

    // pins a mutable reference to a value on the stack
    futures_util::pin_mut!(incoming);

    log::debug!("Listener to the Browser native message proxy is started ...");

    while let Some(result) = incoming.next().await {
        log::debug!("Incoming connections is made to native message proxy");

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
                let shared_writer = Arc::new(tokio::sync::Mutex::new(
                    writer_to_browser_native_app,
                ));

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

        log::debug!("Going to wait for the next connection");
    }
}

// This should be called to start the backend listener to receive messages / send messages from/to a browser native message proxy app
pub(crate) fn start_proxy_handler() {
    log::debug!("In start_proxy_handler....");
    tauri::async_runtime::spawn(async move {
        log::debug!("Starting the app side proxy listening service");
        run_server("okp_browser_ipc".to_string()).await;
    });
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
