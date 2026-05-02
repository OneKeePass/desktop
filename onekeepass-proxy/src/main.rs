mod proxy_client;
mod sandbox;

use std::{fs, path::{Path, PathBuf}};

use log::LevelFilter;
use log4rs::{
    config::{Appender, Root},
    encode::pattern::PatternEncoder,
    Config,
};

use log4rs::append::rolling_file::{
    policy::compound::{roll::fixed_window::FixedWindowRoller, trigger::size::SizeTrigger, CompoundPolicy},
    RollingFileAppender,
};
use tipsy::{Endpoint, ServerId};
use tokio::io::split;

use crate::proxy_client::{
    main_app_to_stdout, send_app_connection_available_ok, send_app_connection_not_available_error, send_proxy_error_message, stdin_to_main_app,
};

fn init_log() -> Result<(), Box<dyn std::error::Error>> {
    let max_file_size_mb: u64 = 5;
    let backup_count: u32 = 2;
    cfg_if::cfg_if! {
        if #[cfg(debug_assertions)] {
            // Debug mode
            // Set lower log level for debug mode
            let log_filter_level = LevelFilter::Debug;
            let log_file_name = "onekeepass-proxy-dev.log";
        } else {
            // Release mode
            let log_filter_level: LevelFilter = LevelFilter::Info;
            let log_file_name = "onekeepass-proxy.log";
        }
    }

    // Log directory:
    //   - Sandboxed (Mac App Store): App Group container's Logs/ subdir, so the
    //     parent app and developer can locate logs at a known shared path.
    //     Requires the application-groups entitlement.
    //   - Non-sandboxed (DMG / Linux / Windows): system temp dir, current behavior.
    let log_dir: PathBuf = match sandbox::group_container_path() {
        Some(group) => group.join("Logs"),
        None => std::env::temp_dir().join("okp"),
    };

    // Ensure the log directory exists
    if !log_dir.exists() {
        fs::create_dir_all(&log_dir)?;
    }

    let log_file_path = Path::new(&log_dir).join(log_file_name);

    // --- Pattern Encoder ---
    // Defines the format for each log entry.
    // {d} - timestamp
    // {l} - log level
    // {t} - target (module path)
    // {m} - message
    // {n} - newline
    // let pattern = "{d(%Y-%m-%d %H:%M:%S%.3f %Z)} [{l}] {t} - {m}{n}";
    let pattern = "{d(%Y-%m-%d %H:%M:%S)} [{l}] {t} - {m}{n}";
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

    // Combines the trigger (when to roll) and the roller (how to roll).
    let policy = Box::new(CompoundPolicy::new(size_trigger, roller));

    // The actual appender that writes to the file and uses the policy for rotation.
    let file_appender = RollingFileAppender::builder()
        .encoder(Box::new(encoder.clone()))
        .build(log_file_path, policy)?;

    // We should not have console appender for native messaging app
    // as it will interfere with the communication protocol.
    // Following is just for reference if needed in future for other apps.

    // let stdout_appender = ConsoleAppender::builder()
    //     .encoder(Box::new(encoder))
    //     .build();

    // TODO:
    // Need to determine the loginng level dev vs prod mode
    // May use env variable to determine the mode

    // Puts all the appenders together. We can log to multiple places at once.
    let config = Config::builder()
        //.appender(Appender::builder().build("stdout", Box::new(stdout_appender)))
        .appender(Appender::builder().build("file_roller", Box::new(file_appender)))
        .build(
            Root::builder()
                //.appenders(["stdout", "file_roller"]) // Log to both console and file
                .appenders(["file_roller"]) // Log only to file
                .build(log_filter_level), // Set the minimum log level
        )?;

    // Initialize the logger with the configuration.
    log4rs::init_config(config)?;

    Ok(())
}

// IMPORTANT: Same path should be used in the app side proxy handler of the main app
cfg_if::cfg_if! {
    if #[cfg(debug_assertions)] {
        // Debug mode
        const NATIVE_MESSAGE_CONNECTION_NAME: &str = "okp_browser_ipc_dev";
    } else {
        // Release mode
        const NATIVE_MESSAGE_CONNECTION_NAME: &str = "okp_browser_ipc";
    }
}

//#[tokio::main(flavor = "multi_thread", worker_threads = 10)]
#[tokio::main]
async fn main() {
    if let Err(e) = init_log() {
        // eprintln!("Logging initialization failed with error {}", e);
        // send_proxy_error_message(format!("Logging initialization failed with error {}", e).as_ref());
        send_proxy_error_message(&format!("Logging initialization failed with error {}", e));
        return;
    }

    // Under sandbox the parent app binds inside the App Group container; we must
    // connect to the same path. parent_folder() is a no-op on Windows (named pipes
    // ignore parent dir).
    //
    // Defensively ensure the group container dir exists before connecting.
    // The proxy is browser-spawned and may start before the parent app has
    // had a chance to create it. An entitled process can mkdir inside the
    // container directly without needing the NSFileManager API.
    let server_id = match sandbox::group_container_path() {
        Some(parent) => {
            let _ = std::fs::create_dir_all(&parent);
            ServerId::new(NATIVE_MESSAGE_CONNECTION_NAME).parent_folder(parent)
        }
        None => ServerId::new(NATIVE_MESSAGE_CONNECTION_NAME),
    };


    log::info!("Proxy side end point connection pat (server_id) is {:?}",&server_id);

    let Ok(connection_to_server) = Endpoint::connect(server_id).await else {
        log::error!("Failed to connect to the server and sending error msg.....");
        send_app_connection_not_available_error();
        return;
    };

    let (app_connection_reader, app_connection_writer) = split(connection_to_server);

    log::debug!("===============================");

    log::info!("Connected to server and sending initial ok message");

    send_app_connection_available_ok();

    log::debug!("Going to call stdin_to_main_app");
    // Reads the messages from stdin and writes to the main app
    stdin_to_main_app(app_connection_writer);

    log::debug!("Going to call main_app_to_stdout");
    // Receive the response from okp main app and write to stdout continuously in a spawned task loop
    main_app_to_stdout(app_connection_reader);

    log::debug!("++ Both spawn calls are done++");

    // Keep the main thread alive
    loop {}
}
