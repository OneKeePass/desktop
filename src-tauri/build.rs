fn main() {
  // This was added so that editor makes available code mas-build feature available for navigating
  println!("cargo::rustc-check-cfg=cfg(rust_analyzer)");

  #[cfg(target_os = "macos")]
  {
    use swift_rs::SwiftLinker;
    SwiftLinker::new("10.15")
      .with_ios("11")
      .with_package("swift-lib", "./swift-lib/")
      //.with_package("auto-type-lib", "./swift/auto-type-lib/")
      .link();
  }

  tauri_build::build()
}
