use std::collections::HashMap;
use std::path::Path;
use std::sync::{Arc, Mutex};

use log::{debug, error, info};
use notify::{Config, EventKind, RecommendedWatcher, RecursiveMode, Watcher};
use serde::Serialize;
use tauri::{Emitter, Manager};

use onekeepass_core::db_service as kp_service;

use crate::constants::event_names::DB_FILE_CHANGED_EVENT;
use crate::constants::window_labels::MAIN_WINDOW_LABEL;

struct WatcherEntry {
    watcher: RecommendedWatcher,
    // True once the event has been sent to the frontend but not yet acknowledged.
    // Prevents duplicate events if the file is written multiple times quickly.
    notification_pending: bool,
}

pub(crate) struct DbFileWatcherState {
    watchers: Arc<Mutex<HashMap<String, WatcherEntry>>>,
}

#[derive(Clone, Serialize)]
pub(crate) struct DbFileChangedPayload {
    pub db_key: String,
}

impl DbFileWatcherState {
    pub(crate) fn new() -> Self {
        Self {
            watchers: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    // Start watching the file at db_key (full file path).
    // Safe to call multiple times for the same key (idempotent).
    pub(crate) fn start_watching(&self, db_key: &str) {
        let mut watchers = self.watchers.lock().unwrap();
        if watchers.contains_key(db_key) {
            debug!("Watcher already active for {}", db_key);
            return;
        }

        let db_key_owned = db_key.to_string();
        let watchers_clone = Arc::clone(&self.watchers);

        let watcher_result = RecommendedWatcher::new(
            move |res: notify::Result<notify::Event>| {
                let Ok(event) = res else { return };

                // Only react to data-modifying or file-creation events.
                // Atomic-save patterns (write-to-temp + rename) may emit Create events.
                let is_relevant =
                    matches!(event.kind, EventKind::Modify(_) | EventKind::Create(_));
                if !is_relevant {
                    return;
                }

                // Confirm the event concerns our specific db file
                let file_changed = event
                    .paths
                    .iter()
                    .any(|p| p.to_string_lossy() == db_key_owned);
                if !file_changed {
                    return;
                }

                // Guard: skip if a notification is already pending for this db
                {
                    let mut map = watchers_clone.lock().unwrap();
                    match map.get_mut(&db_key_owned) {
                        Some(e) if e.notification_pending => {
                            debug!(
                                "Watcher: notification already pending for {} — skipping",
                                &db_key_owned
                            );
                            return;
                        }
                        None => {
                            // DB was closed (entry removed) while callback was in flight
                            return;
                        }
                        _ => {}
                    }
                }

                // Confirm it is truly an external change by comparing checksums.
                // Our own saves update checksum_hash via write_kdbx_file(), so they return Ok here.
                match kp_service::read_and_verify_db_file(&db_key_owned) {
                    Ok(()) => {
                        debug!(
                            "Watcher: checksum matches for {} — our own save, ignoring",
                            &db_key_owned
                        );
                    }
                    Err(_) => {
                        info!("External change confirmed for {}", &db_key_owned);

                        // Set notification_pending before emitting to prevent races
                        {
                            let mut map = watchers_clone.lock().unwrap();
                            if let Some(e) = map.get_mut(&db_key_owned) {
                                e.notification_pending = true;
                            }
                        }

                        // Emit event to the frontend via the global app handle
                        if let Some(win) = crate::app_state::AppState::global_app_handle()
                            .get_webview_window(MAIN_WINDOW_LABEL)
                        {
                            if let Err(e) = win.emit(
                                DB_FILE_CHANGED_EVENT,
                                DbFileChangedPayload {
                                    db_key: db_key_owned.clone(),
                                },
                            ) {
                                error!("Failed to emit DB_FILE_CHANGED_EVENT: {}", e);
                            }
                        }
                    }
                }
            },
            Config::default(),
        );

        match watcher_result {
            Ok(mut watcher) => {
                // Watch the parent directory rather than the file itself.
                // Atomic-save patterns replace the file via rename, which may not trigger
                // a Modify event on the target — watching the directory captures all cases.
                let path = Path::new(db_key);
                let watch_dir = path.parent().unwrap_or(path);
                match watcher.watch(watch_dir, RecursiveMode::NonRecursive) {
                    Ok(()) => {
                        info!(
                            "Started watching directory {:?} for db {}",
                            watch_dir, db_key
                        );
                        watchers.insert(
                            db_key.to_string(),
                            WatcherEntry {
                                watcher,
                                notification_pending: false,
                            },
                        );
                    }
                    Err(e) => {
                        error!("Failed to set up directory watch for {}: {}", db_key, e);
                    }
                }
            }
            Err(e) => {
                error!("Failed to create file watcher for {}: {}", db_key, e);
            }
        }
    }

    // Stop watching db_key. Called when the DB is closed.
    // Dropping WatcherEntry drops RecommendedWatcher, which unregisters the OS watch.
    pub(crate) fn stop_watching(&self, db_key: &str) {
        let mut watchers = self.watchers.lock().unwrap();
        if watchers.remove(db_key).is_some() {
            info!("Stopped watching {}", db_key);
        }
    }

    // Called when the user acknowledges the notification (Merge, Reload, or Ignore).
    // Clears the flag so future external changes can trigger the event again.
    pub(crate) fn clear_notification_pending(&self, db_key: &str) {
        let mut watchers = self.watchers.lock().unwrap();
        if let Some(e) = watchers.get_mut(db_key) {
            e.notification_pending = false;
        }
    }
}
