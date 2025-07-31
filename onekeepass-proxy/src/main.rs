use std::{
    io::{Read, Write},
    path::Path,
};

use tipsy::{Endpoint, ServerId};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

use chrono::Local;
use log::LevelFilter;
use log4rs::{
    append::{console::ConsoleAppender, file::FileAppender},
    config::{Appender, Root},
    encode::pattern::PatternEncoder,
    Config,
};

fn init_log(log_dir: &str) {
    //let local_time = Local::now().format("Client-%Y-%m-%d-%H%M%S").to_string();
    let local_time = Local::now().format("Client").to_string();
    let log_file = format!("{}.log", local_time);
    let log_file = Path::new(log_dir).join(log_file);

    let time_format = "{d(%Y-%m-%d %H:%M:%S)} - {m}{n}";

    // let stdout = ConsoleAppender::builder()
    //     .encoder(Box::new(PatternEncoder::new(time_format)))
    //     .build();

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

#[tokio::main]
async fn main() {
    let path = "okp_browser_ipc";

    init_log("/Users/jeyasankar/Development/repositories/github/OneKeePass-Organization/desktop/onekeepass-proxy/logs");

    let Ok(mut connection_to_server) = Endpoint::connect(ServerId::new(path)).await else {
        log::error!("Failed to connect to the server");
        return;
    };

    log::debug!("===============================");
    log::debug!("Connected to server");

    // let mut stdin_handle = std::io::stdin().lock();
    // let mut stdout_handle = std::io::stdout().lock();

    let mut buf = [0u8; BUFFER_SIZE];
    loop {
        // Read message from extension through stdin

        // Read message size
        let mut length_bytes = [0; 4];

        std::io::stdin().read_exact(&mut length_bytes).expect("Prefixed length bytes read error");

        let message_length = u32::from_ne_bytes(length_bytes) as usize;

        log::debug!("Received message of size {} from extension", &message_length);

        // Read the message from the extension from stdin
        let mut message_bytes = vec![0; message_length];

        std::io::stdin().read_exact(&mut message_bytes).unwrap();

        log::debug!(
            "Sending stdin read mesage bytes {:?}, str {:?} to server",
            &message_bytes,
            String::from_utf8(message_bytes.to_vec())
        );

        // Write extension message content to the server
        connection_to_server
            .write_all(&message_bytes)
            .await
            .expect("Unable to write message to server connection");

        let _ = connection_to_server.flush().await;

    

        log::debug!("Reading server message");

        // Receive the response from okp server and write to stdout
        if let Ok(len) = connection_to_server.read(&mut buf).await {
            if len <= BUFFER_SIZE {
                log::debug!("Sending server mesage {:?} of size {} to extension in proxy", &buf[..len], len);
                // Write response length
                let response_length = len as u32;
                std::io::stdout().write_all(&response_length.to_ne_bytes()).unwrap();

                std::io::stdout().write_all(&buf[..len]).unwrap();
                std::io::stdout().flush().unwrap();
            } else {
                log::debug!("Server message exceeded");
            }
        } else {
            log::debug!("Proxy error of reading reply mesage from  server");
        }

        log::debug!("Will look for the next message");
    }
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

/*
#[tokio::main]
async fn main() {
    let path = "okp_browser_ipc";

    let mut client = Endpoint::connect(ServerId::new(path))
        .await
        .expect("Failed to connect client.");

    loop {
        let mut buf = [0u8; 4];
        println!("SEND: PING");
        client
            .write_all(b"ping")
            .await
            .expect("Unable to write message to client");
        client
            .read_exact(&mut buf[..])
            .await
            .expect("Unable to read buffer");
        if let Ok("pong") = std::str::from_utf8(&buf[..]) {
            println!("RECEIVED: PONG");
        } else {
            break;
        }

        tokio::time::sleep(std::time::Duration::from_secs(2)).await;
    }
}
*/
