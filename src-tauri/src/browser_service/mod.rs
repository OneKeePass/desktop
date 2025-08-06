mod db_calls;
mod key_share;
mod message;

use std::{
    sync::{Arc, OnceLock},
};

use futures_util::StreamExt as _;
use tipsy::{Connection, Endpoint, OnConflict, ServerId};
use tokio::{
    io::{split, AsyncReadExt, AsyncWriteExt, ReadHalf, WriteHalf},
    runtime::{Builder, Runtime},
    sync::mpsc,
};

use crate::browser_service::{
    key_share::{BrowserServiceRx, BrowserServiceTx, Session, SessionStore},
    message::{InvokeResult, Request, Response},
};

const BUFFER_SIZE: usize = 1024 * 1024;

async fn send_response(
    session: &mut Session,
    received_message_str: &String,
    writer: &mut WriteHalf<impl tokio::io::AsyncRead + tokio::io::AsyncWrite>,
) {
    match serde_json::from_str(&received_message_str) {
        Ok(Request::Associate { client_id }) => {
            let resp = InvokeResult::with_ok(Response::Associate {
                client_id,
                association_id: "Your id".into(),
            });
            log::debug!("Sending Response::Associate");
            writer
                .write_all(resp.json_str().as_bytes())
                .await
                .expect("unable to write to socket");
            let _ = writer.flush().await;
        }
        Ok(Request::InitSessionKey {
            association_id,
            client_session_pub_key,
        }) => {
            let resp = match session.init_session(&association_id, &client_session_pub_key) {
                Ok(app_session_pub_key) => {
                    let (nonce, enc_msg) = session
                        .encrypt(r#"{"message":"Server test message"}"#)
                        .unwrap();

                    InvokeResult::with_ok(Response::InitSessionKey {
                        app_session_pub_key,
                        nonce: nonce,
                        test_message: enc_msg,
                    })
                }
                Err(e) => InvokeResult::with_error(&format!("{}", e)),
            };
            writer
                .write_all(resp.json_str().as_bytes())
                .await
                .expect("unable to write to socket");
            let _ = writer.flush().await;
        }
        Ok(_) => {}
        Err(e) => {
            log::error!(
                "Error {} in deserializing to json of received_message_str: {} ",
                e,
                &received_message_str
            );
        }
    }
}

async fn handle_input_message(input_message: String, sender: Arc<BrowserServiceTx>) {
    log::debug!("In handle_input_message ...");

    match serde_json::from_str(&input_message) {
        Ok(Request::Associate { client_id }) => {
            let association_id = &client_id.clone() ;
            let resp = match SessionStore::session_start(association_id, sender).await {
                Ok(_) => InvokeResult::with_ok(Response::Associate {
                    client_id,
                    association_id: association_id.into(),
                }),
                Err(e) => InvokeResult::with_error(&format!("{}", e)),
            };

            SessionStore::send_response(association_id, &resp.json_str()).await;
            // let r = sender1.send(resp.json_str()).await;
            // log::debug!("Sending result is {:?}", &r);
        }
        Ok(Request::InitSessionKey {
            association_id,
            client_session_pub_key,
        }) => {
            let resp =
                match SessionStore::init_session(&association_id, &client_session_pub_key).await {
                    Ok(app_session_pub_key) => {
                        let (nonce, enc_msg) = SessionStore::encrypt(
                            &association_id,
                            r#"{"message":"Server ENCRYPTED test message"}"#,
                        )
                        .await
                        .unwrap();

                        InvokeResult::with_ok(Response::InitSessionKey {
                            app_session_pub_key,
                            nonce: nonce,
                            test_message: enc_msg,
                        })
                    }
                    Err(e) => InvokeResult::with_error(&format!("{}", e)),
                };

            // Send using tx to output writer
            SessionStore::send_response(&association_id, &resp.json_str()).await;
        }
        Ok(_) => {}
        Err(e) => {
            log::error!(
                "Error {} in deserializing to json of received_message_str: {} ",
                e,
                &input_message
            );
        }
    }
}

// async fn handle_input(shared_reader: Arc<tokio::sync::Mutex<ReadHalf<Connection>>>,input_message: String, sender: BrowserServiceTx) {

fn handle_input(
    mut reader: ReadHalf<Connection>,
    sender: Arc<BrowserServiceTx>,
)  {
    log::debug!("handle_input is called  before spawn");

    tauri::async_runtime::spawn(async move {
        let mut buf = vec![0u8; BUFFER_SIZE];

        log::debug!("In handle_input 'spawn'  and before loop");

        loop {
            // log::debug!("Going to read input");

            match reader.read(&mut buf).await {
                Ok(len) => {
                    if len <= BUFFER_SIZE {
                        if len > 0 {
                            log::debug!("Message size {}", &len);

                            let message_bytes = buf[..len].to_vec();
                            match String::from_utf8(message_bytes) {
                                Ok(input_message) => {
                                    handle_input_message(input_message, sender.clone()).await;
                                }
                                Err(e) => {
                                    log::error!("Converting message bytes to string error {} ", &e);
                                }
                            }
                        } 
                        // else {
                        //     log::debug!("Message len is zero");
                        // }
                    } 
                }
                Err(e) => {
                    log::error!("Error in reading stream {}", &e);
                }
            }
        }
    });
}

fn handle_output(
    mut receiver: BrowserServiceRx,
    shared_writer: Arc<tokio::sync::Mutex<WriteHalf<Connection>>>,
)  {

    log::debug!("handle_output is called  before spawn");

    let writer = shared_writer.clone();
    tauri::async_runtime::spawn(async move {

        log::debug!("In handle_output after spawn before loop");

        while let Some(message) = receiver.recv().await {
            log::debug!(" Received message {}",&message);

            let mut writer_guard = writer.lock().await;

            writer_guard
                .write_all(message.as_bytes())
                .await
                .expect("unable to write to socket");

            let _ = writer_guard.flush().await;
        }
    });
}

async fn run_server(path: String) {
    let endpoint = Endpoint::new(ServerId::new(path), OnConflict::Overwrite).unwrap();

    let incoming = endpoint.incoming().expect("failed to open new socket");

    // pins a mutable reference to a value on the stack
    futures_util::pin_mut!(incoming);

    log::debug!("Browser Server started ...");

    while let Some(result) = incoming.next().await {
        log::debug!("Incoming got some result...");

        match result {
            Ok(stream) => {
                let (reader, writer) = split(stream);

                log::debug!("Starting the loop...");

                let (tx, rx): (BrowserServiceTx, BrowserServiceRx) = mpsc::channel(32);

                let sender = Arc::new(tx);
                let shared_writer = Arc::new(tokio::sync::Mutex::new(writer));

                handle_input(reader, sender);
                handle_output(rx, shared_writer);
                
                log::debug!("Created both read_loop and write_loop");
            }
            _ => unreachable!("ideally"),
        }

        log::debug!("Going to wait for the next connection");
    }
}

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

pub(crate) fn start_proxy_handler() {
    log::debug!("In start_proxy_handler....");
    tauri::async_runtime::spawn(async move {
        println!("Starting the app side proxy listening service");
        run_server("okp_browser_ipc".to_string()).await;
    });
}

/* */
async fn run_server2(path: String) {
    let endpoint = Endpoint::new(ServerId::new(path), OnConflict::Overwrite).unwrap();

    let incoming = endpoint.incoming().expect("failed to open new socket");

    // pins a mutable reference to a value on the stack
    futures_util::pin_mut!(incoming);

    log::debug!("Browser Server started ...");

    while let Some(result) = incoming.next().await {
        log::debug!("Incoming got some result...");

        match result {
            Ok(stream) => {
                let (mut reader, mut writer) = split(stream);
                let mut session = Session::default();

                log::debug!("Starting the loop...");

                tauri::async_runtime::spawn(async move {

                    log::debug!("Before the loop...");

                    // This resulted in error something similar
                    // "results in thread 'tokio-runtime-worker' has overflowed its stack"
                    // let mut buf = [0u8; BUFFER_SIZE];

                    // Solutions to avoid that
                    // Solution 1
                    // Create a tokio runtime (OKP_BROWSER_TOKIO_RUNTIME) with increased stack size in 'thread_stack_size' call

                    // Ref:
                    // https://blog.cloudflare.com/pin-and-unpin-in-rust/
                    // https://rust-lang.github.io/wg-async/vision/submitted_stories/status_quo/alan_runs_into_stack_trouble.html
                    // https://rust-dd.com/post/async-rust-explained-pinning-part-2

                    // Solution 2
                    // Heap allocation  and pin the buffer
                    // Box::pin allocates the buffer on the heap and pins it.
                    // let mut buf = Box::pin([0u8; BUFFER_SIZE]);
                    // Then use reader.read(&mut *buf).await
                    // To get a &mut [u8], use &mut *buf (dereferencing the Pin<Box<[u8; N]>> to a &mut [u8] slice

                    // This solution 2 is required if we want to store the buffer across await points or in self-referential structs


                    // Solution 3
                    // Create a heap-allocated buffer
                    let mut buf = vec![0u8; BUFFER_SIZE];

                    loop {

                        if let Ok(len) = reader.read(&mut buf).await {

                            if len <= BUFFER_SIZE {
                                if len == 0 {
                                    // log::debug!("reader.read len is zero");
                                    continue;
                                }

                                // log::debug!("Geting message from extension through proxy size {}",&len);

                                let message_bytes = buf[..len].to_vec();

                                match String::from_utf8(message_bytes) {
                                    Ok(s) => {
                                        send_response(&mut session, &s, &mut writer).await;
                                    }
                                    Err(e) => {
                                        log::error!("Reading error {} ", &e);
                                    }
                                }
                            }
                        } else {
                            // log
                            log::debug!("Server error of reading from  proxy");
                        }
                    }

                });
            }
            _ => unreachable!("ideally"),
        }
    }
}


