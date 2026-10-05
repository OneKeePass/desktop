fn main() {
    #[cfg(target_os = "macos")]
    {
        // This was added so that editor makes available code mas-build feature available for navigating
        println!("cargo::rustc-check-cfg=cfg(rust_analyzer)");

        // swift-rs only links the Swift runtime here. The swift-lib package is built
        // by swift_package below because swift-rs 1.0.8 cannot build it correctly
        // with Xcode 27 (see the comments there).
        use swift_rs::SwiftLinker;
        SwiftLinker::new(swift_package::MACOS_MIN_VERSION).link();

        swift_package::build_and_link("swift-lib", "swift-lib");
    }

    tauri_build::build()
}

// Builds a Swift package as a static library for the cargo target and links it.
//
// Two Xcode 27 changes break swift-rs 1.0.8 for this app:
// - swift-rs passes the host arch with `--arch` and sets the real target through
//   `-Xswiftc -target`. Xcode 27's SwiftPM appends the host target after that, so an
//   x86_64 build on an Apple Silicon Mac silently gets arm64 Swift code. Passing
//   `--triple` builds for the requested arch.
// - Xcode 27's SwiftPM release builds turn every @_cdecl export into a local symbol,
//   so Rust cannot link against them. swift-rs promotes only the package's own
//   object file back to global, skipping SwiftRs.o which holds swift-rs's runtime
//   functions. Here every @_cdecl export in the archive is promoted, which is safe
//   because this app links exactly one Swift package. Debug builds keep the
//   symbols global, which is why dev builds were unaffected.
#[cfg(target_os = "macos")]
mod swift_package {
    use std::collections::HashMap;
    use std::path::{Path, PathBuf};
    use std::process::Command;

    // Xcode 27 rejects macOS deployment targets below 12.0. Keep in sync with
    // bundle.macOS.minimumSystemVersion in tauri.conf.json and Package.swift.
    pub const MACOS_MIN_VERSION: &str = "12.0";

    pub fn build_and_link(package_name: &str, package_dir: &str) {
        let manifest_dir = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
        let package_path = manifest_dir.join(package_dir);
        let build_path = PathBuf::from(std::env::var("OUT_DIR").unwrap())
            .join("swift-package")
            .join(package_name);

        let arch = match std::env::var("CARGO_CFG_TARGET_ARCH").unwrap().as_str() {
            "aarch64" => "arm64".to_string(),
            other => other.to_string(),
        };
        let triple = format!("{arch}-apple-macosx{MACOS_MIN_VERSION}");
        let configuration = if std::env::var("DEBUG").unwrap() == "true" {
            "debug"
        } else {
            "release"
        };

        let sdk_output = Command::new("xcrun")
            .args(["--sdk", "macosx", "--show-sdk-path"])
            .output()
            .expect("Failed to run xcrun");
        let sdk_path = String::from_utf8_lossy(&sdk_output.stdout).trim().to_string();

        let status = Command::new("swift")
            .current_dir(&package_path)
            .args(["build", "--sdk", &sdk_path])
            .args(["-c", configuration])
            .args(["--triple", &triple])
            .arg("--build-path")
            .arg(&build_path)
            .status()
            .expect("Failed to run swift build");
        if !status.success() {
            panic!("Failed to build Swift package {package_name} for {triple}");
        }

        // The product location differs between Xcode versions (e.g. out/Products/Release
        // on Xcode 27, <triple>/release before), so look for the archive itself
        let archive_name = format!("lib{package_name}.a");
        let mut archives = Vec::new();
        find_files(&build_path, &archive_name, &mut archives);
        let archive = archives
            .into_iter()
            .max_by_key(|p| p.metadata().and_then(|m| m.modified()).ok())
            .unwrap_or_else(|| panic!("{archive_name} not found under {}", build_path.display()));

        globalize_cdecl_symbols(&archive);

        println!("cargo:rerun-if-changed={}", package_path.display());
        println!(
            "cargo:rustc-link-search=native={}",
            archive.parent().unwrap().display()
        );
        println!("cargo:rustc-link-lib=static={package_name}");
    }

    fn globalize_cdecl_symbols(archive: &Path) {
        let nm_output = Command::new("nm")
            .arg(archive)
            .output()
            .expect("Failed to run nm");
        let nm_text = String::from_utf8_lossy(&nm_output.stdout);

        let mut occurrences: HashMap<&str, u32> = HashMap::new();
        let mut local_candidates: Vec<&str> = Vec::new();
        for line in nm_text.lines() {
            let mut fields = line.split_whitespace();
            let (Some(_addr), Some(kind), Some(name), None) =
                (fields.next(), fields.next(), fields.next(), fields.next())
            else {
                continue;
            };
            *occurrences.entry(name).or_insert(0) += 1;
            if kind == "t" && is_cdecl_name(name) && !local_candidates.contains(&name) {
                local_candidates.push(name);
            }
        }

        // A name appearing more than once in the archive would become a duplicate global
        let symbols: Vec<&str> = local_candidates
            .into_iter()
            .filter(|s| occurrences.get(s).copied().unwrap_or(0) <= 1)
            .collect();
        if symbols.is_empty() {
            return;
        }

        let objcopy = rustup_llvm_objcopy().unwrap_or_else(|| {
            panic!(
                "llvm-objcopy not found. Run `rustup component add llvm-tools`. It is needed to \
                 make the Swift @_cdecl functions in {} linkable",
                archive.display()
            )
        });

        let mut cmd = Command::new(objcopy);
        for symbol in &symbols {
            cmd.arg(format!("--globalize-symbol={symbol}"));
        }
        cmd.arg(archive);
        if !cmd.status().map(|s| s.success()).unwrap_or(false) {
            panic!("llvm-objcopy failed on {}", archive.display());
        }
    }

    // A @_cdecl export is a plain C identifier: '_' followed by a name that does not
    // itself start with '_'. Swift mangled names ($s...), ObjC selectors and compiler
    // helpers are excluded by this rule or explicitly.
    fn is_cdecl_name(symbol: &str) -> bool {
        let Some(bare) = symbol.strip_prefix('_') else {
            return false;
        };
        !bare.is_empty()
            && !bare.starts_with('_')
            && bare.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
            && !bare.contains("block_copy_helper")
            && !bare.contains("block_destroy_helper")
    }

    fn find_files(dir: &Path, file_name: &str, found: &mut Vec<PathBuf>) {
        let Ok(entries) = std::fs::read_dir(dir) else {
            return;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                find_files(&path, file_name, found);
            } else if path.file_name().is_some_and(|n| n == file_name) {
                found.push(path);
            }
        }
    }

    // The llvm-objcopy from rustup's llvm-tools component. Apple's toolchain has none.
    fn rustup_llvm_objcopy() -> Option<PathBuf> {
        let rustc = std::env::var("RUSTC").unwrap_or_else(|_| "rustc".into());
        let output = Command::new(rustc)
            .args(["--print", "sysroot"])
            .output()
            .ok()?;
        let sysroot = String::from_utf8_lossy(&output.stdout).trim().to_string();
        let host = format!("{}-apple-darwin", std::env::consts::ARCH);
        let path = Path::new(&sysroot)
            .join("lib/rustlib")
            .join(host)
            .join("bin/llvm-objcopy");
        path.exists().then_some(path)
    }
}
