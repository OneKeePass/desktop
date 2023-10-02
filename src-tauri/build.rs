fn main() {
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
