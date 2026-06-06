fn main() {
    #[cfg(target_os = "macos")]
    {
        // This was added so that editor makes available code mas-build feature available for navigating
        println!("cargo::rustc-check-cfg=cfg(rust_analyzer)");
        use swift_rs::SwiftLinker;
        SwiftLinker::new("10.15")
            .with_ios("11")
            .with_package("swift-lib", "./swift-lib/")
            //.with_package("auto-type-lib", "./swift/auto-type-lib/")
            .link();
    }

    tauri_build::build()
}
