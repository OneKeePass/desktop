#[allow(dead_code)]
pub(crate) mod bookmarks {
    pub(crate) fn create(_path: &str) -> Option<String> {
        None
    }

    pub(crate) fn store_backup_dir(_path: &str, _b64: &str) {}

    pub(crate) fn load_backup_dir(_path: &str) -> Option<String> {
        None
    }

    pub(crate) fn remove_backup_dir(_path: &str) {}

    pub(crate) fn resolve_and_start(
        _b64: &str,
    ) -> Result<(super::BookmarkHandle, Option<String>), &'static str> {
        Err("MAS bookmarks are disabled")
    }

    pub(crate) fn release(_handle: super::BookmarkHandle) {}

    pub(crate) fn load_browser_dir(_browser_id: &str) -> Option<String> {
        None
    }

    pub(crate) fn store_browser_dir(_browser_id: &str, _path: &str, _b64: &str) {}

    pub(crate) fn remove_browser_dir(_browser_id: &str) {}

    pub(crate) fn clear_browser_dirs() {}

    pub(crate) fn remove_db_file(_path: &str) {}

    pub(crate) fn clear_db_files() {}
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct BookmarkHandle;

#[allow(dead_code)]
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub(crate) enum ScopedAccessKey {
    Db(String),
    DbDir(String),
    KeyFile(String),
    BackupDir,
}

#[derive(Default)]
pub(crate) struct LoadKdbxAccess;

impl LoadKdbxAccess {
    pub(crate) fn prepare(_db_file_name: &str, _key_file_name: Option<&str>) -> Self {
        Self
    }

    pub(crate) fn release_all(&mut self) {}

    pub(crate) fn store_success_handles(
        &mut self,
        _db_file_name: &str,
        _key_file_name: Option<&str>,
        _app_state: &crate::app_state::AppState,
    ) {
    }
}

pub(crate) fn should_request_db_repick(
    _io_error_kind: std::io::ErrorKind,
    _db_file_name: &str,
    _app_state: &crate::app_state::AppState,
) -> bool {
    false
}

pub(crate) struct ScopedAccessGuard;

pub(crate) fn auto_open_root_dir_access(_db_file_name: &str) -> ScopedAccessGuard {
    ScopedAccessGuard
}

pub(crate) fn prepare_auto_open_db(
    _db_file_name: &str,
    _key_file_name: Option<&str>,
) -> LoadKdbxAccess {
    LoadKdbxAccess
}

pub(crate) fn store_auto_open_success_handles(
    _access: &mut LoadKdbxAccess,
    _db_file_name: &str,
    _key_file_name: Option<&str>,
    _app_state: &crate::app_state::AppState,
) {
}

pub(crate) fn create_backup_dir_bookmark(_dir: &str) {}

pub(crate) fn record_user_granted_db_file(
    _db_file_name: &str,
    _app_state: &crate::app_state::AppState,
) {
}

pub(crate) fn init_backup_dir_scoped_access(_app_state: &crate::app_state::AppState) {}

pub(crate) fn release_scoped_handle(_handle: BookmarkHandle) {}
