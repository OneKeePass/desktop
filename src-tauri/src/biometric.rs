#![allow(dead_code, non_snake_case, non_upper_case_globals)]


swift!(fn save_key_in_secure_store());
swift!(fn read_key_from_secure_store());

pub fn save_key() {
    //sample_call();
    unsafe { save_key_in_secure_store() };
}

pub fn read_key() {
    unsafe { read_key_from_secure_store() };
}



/* */

use log::info;
use swift_rs::{swift, Bool, Int, SRObject, SRObjectArray, SRString};

#[repr(C)]
struct Volume {
    pub name: SRString,
    path: SRString,
    total_capacity: Int,
    available_capacity: Int,
    is_removable: Bool,
    is_ejectable: Bool,
    is_root_filesystem: Bool,
}

#[repr(C)]
struct Test {
    pub null: bool,
}

swift!(fn get_file_thumbnail_base64(path: &SRString) -> SRString);
swift!(fn get_mounts() -> SRObjectArray<Volume>);
swift!(fn return_nullable(null: Bool) -> Option<SRObject<Test>>);

pub fn sample_call() {
    let path = "/Users";
    let thumbnail = unsafe { get_file_thumbnail_base64(&path.into()) };
    info!(
        "length of base64 encoded thumbnail: {}",
        thumbnail.as_str().len()
    );

    let mounts = unsafe { get_mounts() };
    info!("First Volume Name: {}", mounts[0].name);

    let opt = unsafe { return_nullable(true) };
    info!("function returned nil: {}", opt.is_none());

    let opt = unsafe { return_nullable(false) };
    info!("function returned data: {}", opt.is_some());
}


