use std::{
    io::{Read, Write},
    sync::Arc,
};

use tipsy::Connection;
use tokio::io::{AsyncReadExt, AsyncWriteExt, ReadHalf, WriteHalf};

// Using 1024 * 1024 = 1,048,576 worked on both Mac and Linux, it crashed the Windows impl
// Using a separate testing client, the reason is found to be "thread 'main' has overflowed its stack"
// It appears on Windows the default stack size 1MB. Using a buf size well below (100KB) that worked
// Any attempt to use tokio::runtime::Builder based custom runtime with increased stack size failed with error
// when using 1024 * 1024.
// Final solution is to use a heap allocated buffer using Box::new. But need to use a size below 1_048_576 to work on windows

cfg_if::cfg_if! {
    if #[cfg(target_os = "windows")] {
        const BUFFER_SIZE: usize = 1_000_000; // Windows to avoid stack overflow
    } else {
        const BUFFER_SIZE: usize = 1_048_576; // 1024 * 1024 bytes for other OS
    }
}

// Receive the response from okp main app and write to stdout continuously in a spawned task loop
pub(crate) fn main_app_to_stdout(mut app_connection_reader: ReadHalf<Connection>) {
    tokio::spawn(async move {
        // Stack allocated buffer may cause stack overflow on Windows
        // See above BUFFER_SIZE comment
        // let mut buf = [0u8; BUFFER_SIZE];

        // Heap allocated buffer to avoid stack overflow on Windows
        let mut buf = Box::new([0u8; BUFFER_SIZE]);

        // 'main_app_to_stdout_outer: loop and then use break 'main_app_to_stdout_outer;
        'main_app_to_stdout_outer: loop {
            log::debug!("main_app_to_stdout: Waiting to read from the app...");

            // &mut *buf or buf.as_mut() will work in read call

            if let Ok(len) = app_connection_reader.read(&mut *buf).await {
                // This happens when the map connection is closed
                if len == 0 {
                    log::debug!("Received zero byte from app and breaking and exiting the proxy after sending error to extension");
                    send_app_connection_not_available_error();
                    break 'main_app_to_stdout_outer;
                }
                if len <= BUFFER_SIZE {
                    // log::debug!("Sending server mesage {:?} of size {} to extension in proxy", &buf[..len], len);

                    // The main app sends "DISCONNECT" string when user disables a previously connected browser
                    // Here the utf-8 bytes length of this string is 10
                    if len == 10 && String::from_utf8(buf[..len].to_vec()).is_ok() {
                        let s = String::from_utf8(buf[..len].to_vec()).unwrap();
                        // Check to ensure that string is DISCONNECT and if so exit the native program launched by the browser
                        // When this native app exits, the extension side the onDisconnect callback of 'port' will be called 
                        if s == "DISCONNECT" {
                            send_app_connection_disconnected();
                            log::debug!("Received disconnect message from app and breaking and exiting the proxy");
                            break 'main_app_to_stdout_outer;
                        }
                    }

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

        log::info!("Exiting the main_app_to_stdout loop");
        std::process::exit(0);
    });
}

// Reads the messages from stdin and writes to the main app
pub(crate) fn stdin_to_main_app(app_connection_writer: WriteHalf<Connection>) {
    let shared_writer = Arc::new(tokio::sync::Mutex::new(app_connection_writer));

    // 'std::io::stdin().read_exact' blocks other tasks of tokio runtime
    // Because of that we need to make that call in a dedicated thread like using the 'spawn_blocking' call

    tokio::task::spawn_blocking(move || {
        // Lock stdin once for this thread. It seems the following works even without locking
        // let mut locked_stdin = std::io::stdin().lock();

        log::debug!("STD-IN-TO-APP: In spawn_blocking but before loop");

        // 'stdin_outer: loop  and then use break 'stdin_outer;
        'stdin_outer: loop {
            // Read message size
            let mut length_bytes = [0; 4];

            log::debug!("STD-IN-TO-APP: WAITING at loop top to read the stdin ...");

            // Read the 4 bytes message length from stdin
            if let Err(e) = std::io::stdin().read_exact(&mut length_bytes) {
                log::error!("STDIN - Message length bytes read error {}", &e);
                // The error may be 'failed to fill whole buffer' when the following 'message_length' will be zero
                // when the extension is removed or the browser is closed
                send_proxy_error_message("Reading extension's message length from stdin failed");
                break 'stdin_outer;
            }

            log::debug!("STD-IN-TO-APP:Reading of stdin message length is done");

            // Gets the message length integer value from a Native Endidan bytes buf
            let message_length = u32::from_ne_bytes(length_bytes) as usize;
            if message_length == 0 {
                // message_length is zero when the browser is closed. For now we continue
                // and the browser will close this native app.
                log::debug!("STD-IN-TO-APP: Message length is zero and exiting the proxy after sending error to extension");
                // is ther any need to send an error message to extension here since the browser is closed?
                send_proxy_error_message("Message length from stdin is zero");
                break 'stdin_outer;
            }

            log::debug!("STD-IN-TO-APP: Received message of size {} from extension", &message_length);

            let mut message_bytes_buf = vec![0; message_length];

            // Read the message from the extension from stdin to this buffer
            if let Err(e) = std::io::stdin().read_exact(&mut message_bytes_buf) {
                log::error!("STDIN - Message content read error {}", &e);
                send_proxy_error_message("Reading message content from stdin failed");
                break 'stdin_outer;
            }

            log::debug!(
                "STD-IN-TO-APP: Sending stdin read  str {:?} to the app",
                String::from_utf8(message_bytes_buf.to_vec())
            );

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

                log::debug!("STD-IN-TO-APP: In the spawned task AFTER writing to app");
            });

            // std::thread::sleep(std::time::Duration::from_millis(500));

            log::debug!("STD-IN-TO-APP: Will go back to the top of the loop");
        }
        log::info!("Exiting the stdin_to_main_app loop");
        std::process::exit(0);
    });
}

// Send the proxy error message to the extension and this is not async fn so it completes before returning
// Also after sending this message the proxy exits
pub(crate) fn send_proxy_error_message(error_message: &str) {
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
pub(crate) fn send_app_connection_not_available_error() {
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

// Sends the disconnect message to the browser extension.
pub(crate) fn send_app_connection_disconnected() {
    let resp = r#"{"ok" : {"action": "DISCONNECT"}}"#;
    let len = resp.as_bytes().len();
    let response_length = len as u32;

    if let Err(e) = std::io::stdout().write_all(&response_length.to_ne_bytes()) {
        log::error!("Writing DISCONNECT ok msg length to extension failed with error {}", e);
        return;
    }

    if let Err(e) = std::io::stdout().write_all(resp.as_bytes()) {
        log::error!("Writing DISCONNECT ok msg to extension failed with error {}", e);
        return;
    }

    let _ = std::io::stdout().flush();

    log::debug!("DISCONNECT ok message {} is send ", &resp);
}

pub(crate) fn send_app_connection_available_ok() {
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

/*
fn remove_dir_files<P: AsRef<Path>>(path: P) {
    if let Ok(p) = fs::read_dir(path) {
        for entry in p {
            let _ = entry.and_then(|e| fs::remove_file(e.path()));
        }
    }
}
*/
