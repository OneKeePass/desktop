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

use log4rs::append::{
    rolling_file::{
        policy::compound::{roll::fixed_window::FixedWindowRoller, trigger::size::SizeTrigger, CompoundPolicy},
        RollingFileAppender,
    },
};

const BUFFER_SIZE: usize = 1024 * 1024;

// Receive the response from okp main app and write to stdout continuously in a spawned task loop
fn main_app_to_stdout(mut app_connection_reader: ReadHalf<Connection>) {
    // log::debug!("2 In main_app_to_stdout before loop...");

    tokio::spawn(async move {
        let mut buf = [0u8; BUFFER_SIZE];

        // 'main_app_to_stdout_outer: loop and then use break 'main_app_to_stdout_outer;
        'main_app_to_stdout_outer: loop {
            // log::debug!("2 main_app_to_stdout: Waiting to read from the app...");

            if let Ok(len) = app_connection_reader.read(&mut buf).await {
                // This happens when the map connection is closed
                if len == 0 {
                    log::debug!("Received zero byte from app and breaking and exiting the proxy after sending error to extension");
                    send_app_connection_not_available_error();
                    break 'main_app_to_stdout_outer;
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
                    log::error!("Main app message content response size exceeded the limit");
                    send_proxy_error_message("Main app message content response size exceeded the limit");
                    // Should we break or continue?
                    break 'main_app_to_stdout_outer;
                }
            } else {
                log::debug!("Proxy error of reading reply mesage from  server");
                send_proxy_error_message("Reading reply message from main app failed");
                break 'main_app_to_stdout_outer;
            }
        }

        log::debug!("Exiting the main_app_to_stdout loop");
        std::process::exit(0);
    });
}

// Reads the messages from stdin and writes to the main app
fn stdin_to_main_app(app_connection_writer: WriteHalf<Connection>) {
    // log::debug!("1 Entering stdin_to_main_app before loop...");

    let shared_writer = Arc::new(tokio::sync::Mutex::new(app_connection_writer));

    let write_completed = Arc::new(std::sync::Mutex::new(true));

    // 'std::io::stdin().read_exact' blocks other tasks of tokio runtime
    // Because of that we need to make that call in a dedicated thread like using the 'spawn_blocking' call

    tokio::task::spawn_blocking(move || {
        // 'stdin_outer: loop  and then use break 'stdin_outer;
        'stdin_outer: loop {
            // Read message size
            let mut length_bytes = [0; 4];

            log::debug!("STD-IN-TO-APP: WAITING at loop top to read the stdin ...");

            // std::io::stdin().read_exact(&mut length_bytes).expect("Prefixed length bytes read error");

            if let Err(e) = std::io::stdin().read_exact(&mut length_bytes) {
                log::error!("STDIN - Message length bytes read error {}", &e);
                // The error may be 'failed to fill whole buffer' when the following 'message_length' will be zero
                // when the extension is removed or the browser is closed
                send_proxy_error_message("Reading extension's message length from stdin failed");
                break 'stdin_outer;
            }

            // Gets the message length integer value from a Native Endidan bytes buf
            let message_length = u32::from_ne_bytes(length_bytes) as usize;
            if message_length == 0 {
                // message_length is zero when the browser is closed. For now we continue
                // and the browser will close this native app.
                // TODO: Close the connection to the app by sending a message to 'app_connection_writer' and break instead of continuing

                // continue;
                log::debug!("STD-IN-TO-APP: Message length is zero and exiting the proxy after sending error to extension");
                send_proxy_error_message("Message length from stdin is zero");
                break 'stdin_outer;
            }

            log::debug!("STD-IN-TO-APP: Received message of size {} from extension", &message_length);

            // Read the message from the extension from stdin to this buffer
            let mut message_bytes_buf = vec![0; message_length];

            if let Err(e) = std::io::stdin().read_exact(&mut message_bytes_buf) {
                log::error!("STDIN - Message content read error {}", &e);
                // What should we do instead of continuing? Should we break and exit?
                // continue;
                send_proxy_error_message("Reading message content from stdin failed");
                break 'stdin_outer;
            }

            log::debug!(
                "STD-IN-TO-APP: Sending stdin read  str {:?} to the app",
                String::from_utf8(message_bytes_buf.to_vec())
            );

            let mut val = write_completed.lock().unwrap();
            // Wait until the previous write is completed in the spawned task below
            while !(*val) {
                std::thread::sleep(std::time::Duration::from_millis(500));
            }
            *val = false;

            let write_completed_clone = write_completed.clone();

            let shared_writer_cloned = shared_writer.clone();

            // log::debug!("++Going to call async writing...");

            // Write to the app side asynchronously and goes back to the loop begining
            tokio::spawn(async move {
                let mut app_connection_writer = shared_writer_cloned.lock().await;

                log::debug!("STD-IN-TO-APP: In the spawned task BEFORE writing to the app");

                // Write extension message content to the main app

                if let Err(e) = app_connection_writer.write_all(&message_bytes_buf).await {
                    // Seen this error if the main is closed - Error is Broken pipe (os error 32)
                    log::error!("Unable to write message to the main app connection. Error is {}", &e);
                    // Need to send an error to extension if there is a possibilty the application is closed.
                    send_app_connection_not_available_error();
                    log::debug!("Exiting the proxy?");
                    std::process::exit(32);
                    // return;
                    // Do we need to exit the loop and how ?
                }

                let _ = app_connection_writer.flush().await;

                let mut flag = write_completed_clone.lock().unwrap();
                *flag = true;

                log::debug!("STD-IN-TO-APP: In the spawned task AFTER writing to app");
            });

            // std::thread::sleep(std::time::Duration::from_millis(500));

            log::debug!("STD-IN-TO-APP: Will go back to the top of the loop");
        }
        log::debug!("Exiting the stdin_to_main_app loop");
        std::process::exit(0);
    });
}

fn remove_dir_files<P: AsRef<Path>>(path: P) {
    if let Ok(p) = fs::read_dir(path) {
        for entry in p {
            let _ = entry.and_then(|e| fs::remove_file(e.path()));
        }
    }
}

// Send the proxy error message to the extension and this is not async fn so it completes before returning
// Also after sending this message the proxy exits
fn send_proxy_error_message(error_message: &str) {
    let resp = format!(r#"{{"error" : {{"action": "PROXY_ERROR", "error_message": "{}"}}}}"#, error_message);
    let len = resp.as_bytes().len();
    let response_length = len as u32;

    if let Err(e) = std::io::stdout().write_all(&response_length.to_ne_bytes()) {
        log::error!("Writing PROXY_ERROR msg length to extension failed with error {}", e);
        return;
    }

    if let Err(e) = std::io::stdout().write_all(resp.as_bytes()) {
        log::error!("Writing PROXY_ERROR msg to extension failed with error {}", e);
        return;
    }

    let _ = std::io::stdout().flush();

    log::debug!("PROXY_ERROR message {} is send ", &resp);
}

// Send the app not available error to the extension and this is not async fn so it completes before returning
// Also after sending this message the proxy exits
fn send_app_connection_not_available_error() {
    let resp = r#"{"error" : {"action": "PROXY_APP_CONNECTION" , "error_message": "App is not running"}}"#;
    let len = resp.as_bytes().len();
    let response_length = len as u32;

    if let Err(e) = std::io::stdout().write_all(&response_length.to_ne_bytes()) {
        log::error!("Writing PROXY_APP_CONNECTION error msg length to extension failed with error {}", e);
        return;
    }

    if let Err(e) = std::io::stdout().write_all(resp.as_bytes()) {
        log::error!("Writing PROXY_APP_CONNECTION error msg to extension failed with error {}", e);
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
        log::error!("Writing PROXY_APP_CONNECTION ok msg length to extension failed with error {}", e);
        return;
    }

    if let Err(e) = std::io::stdout().write_all(resp.as_bytes()) {
        log::error!("Writing PROXY_APP_CONNECTION ok msg to extension failed with error {}", e);
        return;
    }

    let _ = std::io::stdout().flush();

    log::debug!("PROXY_APP_CONNECTION ok message {} is send ", &resp);
}

fn init_log2() -> Result<(), Box<dyn std::error::Error>> {

    let max_file_size_mb: u64 = 5;
    let backup_count: u32 = 2;
    let log_file_name = "onekeepass-proxy.log";

    // Log directory in the system temp directory
    let log_dir = std::env::temp_dir().join("okp");
    let log_dir = Path::new(&log_dir);

    // Ensure the log directory exists
    if !log_dir.exists() { 
        fs::create_dir_all(&log_dir)?;
    } 
    
    // fs::create_dir_all(&log_dir)?;
    
    let log_file_path = Path::new(log_dir).join(log_file_name);

    // --- Pattern Encoder ---
    // Defines the format for each log entry.
    // {d} - timestamp
    // {l} - log level
    // {t} - target (module path)
    // {m} - message
    // {n} - newline
    // let pattern = "{d(%Y-%m-%d %H:%M:%S%.3f %Z)} [{l}] {t} - {m}{n}";
    let pattern = "{d(%Y-%m-%d %H:%M:%S)} - {m}{n}";
    let encoder = PatternEncoder::new(pattern);

    // --- Size Trigger ---
    // This triggers the rotation when the log file reaches the specified size.
    let size_limit = max_file_size_mb * 1024 * 1024; // Convert MB to bytes
    let size_trigger = Box::new(SizeTrigger::new(size_limit));

    // --- Fixed Window Roller ---
    // This defines the naming scheme for the rotated log files.
    // It will create files like `app.1.log`, `app.2.log`, etc.
    // The `{}` is a placeholder for the backup number.
    let roller_pattern = format!("{}.{{}}.log", log_file_path.to_str().unwrap().replace(".log", ""));
    let roller = Box::new(FixedWindowRoller::builder().build(&roller_pattern, backup_count)?);

    // --- Compound Policy ---
    // Combines the trigger (when to roll) and the roller (how to roll).
    let policy = Box::new(CompoundPolicy::new(size_trigger, roller));

    // --- Rolling File Appender ---
    // The actual appender that writes to the file and uses the policy for rotation.
    let file_appender = RollingFileAppender::builder()
        .encoder(Box::new(encoder.clone()))
        .build(log_file_path, policy)?;

    // We should not have console appender for native messaging app
    // as it will interfere with the communication protocol.
    // Following is just for reference if needed in future for other apps.

    // --- Console Appender (for stdout) ---
    // Useful for seeing logs in the console during development.
    // let stdout_appender = ConsoleAppender::builder()
    //     .encoder(Box::new(encoder))
    //     .build();

    // --- Configuration ---
    // Puts all the appenders together. We can log to multiple places at once.
    let config = Config::builder()
        //.appender(Appender::builder().build("stdout", Box::new(stdout_appender)))
        .appender(Appender::builder().build("file_roller", Box::new(file_appender)))
        .build(
            Root::builder()
                //.appenders(["stdout", "file_roller"]) // Log to both console and file
                .appenders(["file_roller"]) // Log only to file
                .build(LevelFilter::Debug), // Set the minimum log level
        )?;

    // Initialize the logger with the configuration.
    log4rs::init_config(config)?;

    Ok(())
}

fn init_log(log_dir: &str) {
    // let local_time = Local::now().format("Client-%Y-%m-%d-%H%M%S").to_string();
    let local_time = Local::now().format("onekeepass-proxy").to_string();
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

//#[tokio::main(flavor = "multi_thread", worker_threads = 10)]

#[tokio::main]
async fn main() {
    let path = "okp_browser_ipc";

    /* 
    ////
    // let log_dir = "/Users/jeyasankar/Development/repositories/github/OneKeePass-Organization/desktop/onekeepass-proxy/logs";
    let log_dir = std::env::temp_dir().join("okp");
    let log_dir = Path::new(&log_dir);
    if !log_dir.exists() {
        let _ = fs::create_dir_all(&log_dir);
    } else {
        // Each time we remove any old log file.
        // IMPORTANT: TODO: Explore the use file rotation and size limit
        let _r = remove_dir_files(&log_dir);
    }
    init_log(log_dir.to_path_buf().to_string_lossy().as_ref());
    ////
    */

    if let Err(e) = init_log2() {
        // eprintln!("Logging initialization failed with error {}", e);
        // send_proxy_error_message(format!("Logging initialization failed with error {}", e).as_ref());
        send_proxy_error_message(&format!("Logging initialization failed with error {}", e));
        return;
    }

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
