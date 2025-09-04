use std::{
    fs,
    io::{Read, Write},
    path::Path,
    sync::Arc,
};

use tipsy::{Connection, Endpoint, ServerId};
use tokio::io::{split, AsyncReadExt, AsyncWriteExt, ReadHalf, WriteHalf};

use chrono::Local;
use log::LevelFilter;
use log4rs::{
    append::file::FileAppender,
    config::{Appender, Root},
    encode::pattern::PatternEncoder,
    Config,
};

fn init_log(log_dir: &str) {
    // let local_time = Local::now().format("Client-%Y-%m-%d-%H%M%S").to_string();
    let local_time = Local::now().format("Client").to_string();
    let log_file = format!("{}.log", local_time);
    let log_file = Path::new(log_dir).join(log_file);

    let time_format = "{d(%Y-%m-%d %H:%M:%S)} - {m}{n}";

    let tofile = FileAppender::builder()
        .encoder(Box::new(PatternEncoder::new(time_format)))
        .build(log_file)
        .unwrap();

    let level = LevelFilter::Debug;

    let config = Config::builder()
        //.appender(Appender::builder().build("stdout", Box::new(stdout)))
        .appender(Appender::builder().build("file", Box::new(tofile)))
        //.build(Root::builder().appenders(["stdout", "file"]).build(level))
        .build(Root::builder().appenders(["file"]).build(level))
        .unwrap();

    log4rs::init_config(config).unwrap();
}

const BUFFER_SIZE: usize = 1024 * 1024;

// Receive the response from okp main app and write to stdout
fn main_app_to_stdout(mut app_connection_reader: ReadHalf<Connection>) {
    // log::debug!("2 In main_app_to_stdout before loop...");

    tokio::spawn(async move {
        let mut buf = [0u8; BUFFER_SIZE];
        loop {
            // log::debug!("2 main_app_to_stdout: Waiting to read from the app...");

            if let Ok(len) = app_connection_reader.read(&mut buf).await {
                // This happens when the map connection is closed
                if len == 0 {
                    log::debug!("Received zero byte from app and breaking");
                    // TODO: Should we write to stdout any message for this?
                    //       or Will 'onDisconnect' handler of  browser extension take care of this
                    break;
                }
                if len <= BUFFER_SIZE {
                    // log::debug!("Sending server mesage {:?} of size {} to extension in proxy", &buf[..len], len);

                    // Write response length first
                    let response_length = len as u32;
                    std::io::stdout().write_all(&response_length.to_ne_bytes()).unwrap();

                    log::debug!("Writing message to stdout for extension  {:?}", String::from_utf8(buf[..len].to_vec()));

                    // The message content is written. This should be parseable as json
                    std::io::stdout().write_all(&buf[..len]).unwrap();
                    std::io::stdout().flush().unwrap();
                } else {
                    // Should not happen
                    log::error!("Main app message size exceeded");
                }
            } else {
                log::debug!("Proxy error of reading reply mesage from  server");
                // TODO: Write a error message to stdout accordingly
            }
        }
    });
}

// Reads the messages from stdin and writes to the main app
fn stdin_to_main_app(app_connection_writer: WriteHalf<Connection>) {
    // log::debug!("1 Entering stdin_to_main_app before loop...");

    let shared_writer = Arc::new(tokio::sync::Mutex::new(app_connection_writer));

    // 'std::io::stdin().read_exact' blocks other tasks of tokio runtime
    // Because of that we need to make that call in a dedicated thread like using the 'spawn_blocking' call

    tokio::task::spawn_blocking(move || {
        loop {
            // Read message size
            let mut length_bytes = [0; 4];

            // log::debug!("1 stdin_to_main_app:WAITING to read the stdin...");

            // std::io::stdin().read_exact(&mut length_bytes).expect("Prefixed length bytes read error");

            if let Err(_e) = std::io::stdin().read_exact(&mut length_bytes) {
                // log::error!("STDIN - Message length bytes read error {}", &e);
                // The error may be 'failed to fill whole buffer' when the following 'message_length' will be zero
                // when the extension is removed or the brower is closed
            }

            // Gets the message length integer value from a Native Endidan bytes buf
            let message_length = u32::from_ne_bytes(length_bytes) as usize;
            if message_length == 0 {
                // message_length is zero when the browser is closed. For now we continue
                // and the browser will close this native app.
                // TODO: Close the connection to the app by sending a message to 'app_connection_writer' and break instead of continuing
                continue;
            }

            // log::debug!("Received message of size {} from extension", &message_length);

            // Read the message from the extension from stdin to this buffer
            let mut message_bytes_buf = vec![0; message_length];

            if let Err(e) = std::io::stdin().read_exact(&mut message_bytes_buf) {
                // log::error!("STDIN - Message content read error {}", &e);
                // What should we do instead of continuing?
                continue;
            }

            log::debug!(
                "Sending stdin read  str {:?} to the main app",
                String::from_utf8(message_bytes_buf.to_vec())
            );

            let shared_writer_cloned = shared_writer.clone();

            // log::debug!("++Going to call async writing...");

            // Write to the app side asynchronously and goes back to the loop begining
            tokio::spawn(async move {
                let mut app_connection_writer = shared_writer_cloned.lock().await;

                log::debug!("BEFORE async writing to app");



                // Write extension message content to the main app
                // TODO: Need to send an error to extension if there is a possibilty the application is closed.
                if let Err(e) = app_connection_writer.write_all(&message_bytes_buf).await {
                    log::error!("Unable to write message to the main app connection. Error is {}", &e);
                }

                let _ = app_connection_writer.flush().await;
            });
        }
    });
}

fn remove_dir_files<P: AsRef<Path>>(path: P) {
    if let Ok(p) = fs::read_dir(path) {
        for entry in p {
            let _ = entry.and_then(|e| fs::remove_file(e.path()));
        }
    }
}

fn send_app_connection_not_available_error() {
    let resp = r#"{"error" : {"action": "PROXY_APP_CONNECTION"}, "error_message": "App is not running"}"#;
    let len = resp.as_bytes().len();
    let response_length = len as u32;
    
    if let Err(e) = std::io::stdout().write_all(&response_length.to_ne_bytes()) {
        log::error!("Writing PROXY_APP_CONNECTION error msg length to extension failed with error {}",e);
        return;
    }

     if let Err(e) = std::io::stdout().write_all(resp.as_bytes()) {
        log::error!("Writing PROXY_APP_CONNECTION error msg to extension failed with error {}",e);
        return;
     }

    let _ = std::io::stdout().flush();

    log::debug!("PROXY_APP_CONNECTION error message {} is send ", &resp);
}

fn send_app_connection_available_ok() {
    let resp = r#"{"ok" : {"action": "PROXY_APP_CONNECTION"}}"#;
    let len = resp.as_bytes().len();
    let response_length = len as u32;
    
    if let Err(e) = std::io::stdout().write_all(&response_length.to_ne_bytes()) {
        log::error!("Writing PROXY_APP_CONNECTION ok msg length to extension failed with error {}",e);
        return;
    }

     if let Err(e) = std::io::stdout().write_all(resp.as_bytes()) {
        log::error!("Writing PROXY_APP_CONNECTION ok msg to extension failed with error {}",e);
        return;
     }

    let _ = std::io::stdout().flush();

    log::debug!("PROXY_APP_CONNECTION ok message {} is send ", &resp);
}

//#[tokio::main(flavor = "multi_thread", worker_threads = 10)]

#[tokio::main]
async fn main() {
    let path = "okp_browser_ipc";

    ////
    let log_dir = "/Users/jeyasankar/Development/repositories/github/OneKeePass-Organization/desktop/onekeepass-proxy/logs";
    let log_dir = Path::new(log_dir);
    if !log_dir.exists() {
        let _ = fs::create_dir_all(&log_dir);
    } else {
        // Each time we remove any old log file.
        // TODO: Explore the use file rotation
        let _r = remove_dir_files(&log_dir);
    }
    init_log(log_dir.to_path_buf().to_string_lossy().as_ref());

    ////

    let Ok(connection_to_server) = Endpoint::connect(ServerId::new(path)).await else {
        log::error!("Failed to connect to the server and sending error msg");
        send_app_connection_not_available_error();
        return;
    };

    let (app_connection_reader, app_connection_writer) = split(connection_to_server);

    log::debug!("===============================");
    
    log::debug!("Connected to server and sending initial ok message");

    send_app_connection_available_ok();

    // let mut stdin_handle = std::io::stdin().lock();
    // let mut stdout_handle = std::io::stdout().lock();

    stdin_to_main_app(app_connection_writer);

    main_app_to_stdout(app_connection_reader);

    log::debug!("++ Both spawn calls are done++");

    // Keep the main thread alive
    loop {}
}

/*
/Users/jeyasankar/Development/repositories/github/OneKeePass-Organization/desktop/src-tauri/target/debug
{
    "allowed_extensions": [
        "onekeepass@gmail.com"
    ],
    "description": "OneKeePass integration with native messaging support",
    "name": "org.onekeepass.onekeepass_browser",
    "path": "/Users/jeyasankar/Development/RustProjects/okp_browser_native_messaging/onekeepass-proxy/target/debug/onekeepass-proxy",
    "type": "stdio"
}
*/
