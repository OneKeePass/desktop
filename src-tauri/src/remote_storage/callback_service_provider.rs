use std::path::PathBuf;
use std::sync::Arc;

use onekeepass_core::error::Result;
use onekeepass_core::remote_storage::callback_service::{
    CallbackServiceProvider, CommonCallbackService,
};
use onekeepass_core::remote_storage::storage_service::RemoteStorageType;

// Desktop is kdbx-only: there's no on-disk private-key file management and
// no blob store to invalidate on delete, so every hook is a no-op. The
// CallbackServiceProvider still needs an implementation registered for
// storage_service to be initialized.
pub(crate) fn init() {
    CallbackServiceProvider::init(Arc::new(NoopCommonCallbackService));
}

struct NoopCommonCallbackService;

impl CommonCallbackService for NoopCommonCallbackService {
    fn sftp_private_key_file_full_path(
        &self,
        _connection_id: &str,
        _file_name: &str,
    ) -> PathBuf {
        PathBuf::new()
    }

    fn sftp_copy_from_temp_key_file(
        &self,
        _connection_id: &str,
        _file_name: &str,
    ) -> Result<()> {
        Ok(())
    }

    fn remote_storage_config_deleted(
        &self,
        _remote_type: RemoteStorageType,
        _connection_id: &str,
    ) -> Result<()> {
        Ok(())
    }
}
