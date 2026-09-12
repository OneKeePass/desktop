use std::{collections::HashMap, time::Duration};

use serde::{Deserialize, Serialize};

const MANIFEST_URL: &str = "https://onekeepass.com/updates/desktop-stable.json";
const USER_AGENT: &str = "OneKeePass-Updater";

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCheckResult {
    pub update_available: bool,
    // True when something other than this app installs updates, so the UI must
    // not offer a download. The remaining fields carry no useful value then.
    pub managed_externally: bool,
    pub current_version: String,
    pub latest_version: String,
    pub release_notes: String,
    pub download_url: String,
}

pub async fn check_for_updates(current_version: String) -> Result<UpdateCheckResult, String> {
    // A Flatpak is updated by the package manager it was installed with. Offering
    // a GitHub download here would leave the user with a second, unmanaged copy,
    // and the running one would keep reporting the old version. Answer without a
    // network call rather than asking GitHub something we cannot act on.
    if crate::sandbox::is_flatpak() {
        return Ok(UpdateCheckResult {
            update_available: false,
            managed_externally: true,
            current_version,
            latest_version: String::new(),
            release_notes: String::new(),
            download_url: String::new(),
        });
    }

    check_via_manifest(current_version).await
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct UpdateManifest {
    schema_version: u32,
    platforms: HashMap<String, PlatformRelease>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PlatformRelease {
    version: String,
    release_url: String,
}

async fn check_via_manifest(current_version: String) -> Result<UpdateCheckResult, String> {
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .map_err(|e| format!("Failed to build HTTP client: {e}"))?;
    let response = client
        .get(MANIFEST_URL)
        .header(reqwest::header::USER_AGENT, USER_AGENT)
        .header(reqwest::header::ACCEPT, "application/json")
        .send()
        .await
        .map_err(|e| format!("Update check request failed: {e}"))?;
    if !response.status().is_success() {
        return Err(format!(
            "Update manifest returned status {}",
            response.status()
        ));
    }
    let manifest: UpdateManifest = response
        .json()
        .await
        .map_err(|e| format!("Failed to parse update manifest: {e}"))?;
    select_release(
        manifest,
        current_version,
        std::env::consts::OS,
        std::env::consts::ARCH,
    )
}

// These are the build target OS/architecture, so an Intel build running under
// Rosetta continues to check the Intel entry. Never substitute another target.
fn select_release(
    manifest: UpdateManifest,
    current_version: String,
    os: &str,
    arch: &str,
) -> Result<UpdateCheckResult, String> {
    if manifest.schema_version != 1 {
        return Err(format!(
            "Unsupported update manifest schema: {}",
            manifest.schema_version
        ));
    }
    let target = format!("{os}-{arch}");
    let release = manifest
        .platforms
        .get(&target)
        .ok_or_else(|| format!("No update information is available for {target}"))?;
    let latest = parse_version(&release.version)?;
    let current = parse_version(&current_version)?;
    let latest_version = release
        .version
        .strip_prefix('v')
        .unwrap_or(&release.version)
        .to_string();
    // The offline generator emits this exact release page. Validate before the
    // frontend passes the manifest URL to the system browser.
    let expected_url =
        format!("https://github.com/OneKeePass/desktop/releases/tag/v{latest_version}");
    if release.release_url != expected_url {
        return Err(format!(
            "Invalid release URL in update manifest for {target}"
        ));
    }
    Ok(UpdateCheckResult {
        update_available: latest > current,
        managed_externally: false,
        current_version,
        latest_version,
        // The offline manifest supplies no release notes. The existing dialog
        // already omits its notes section when this string is empty.
        release_notes: String::new(),
        download_url: release.release_url.clone(),
    })
}

// Match the stable major.minor.patch format accepted by the local generator.
// Invalid versions must fail a check rather than silently comparing as zero.
fn parse_version(version: &str) -> Result<[u64; 3], String> {
    let invalid = || format!("Invalid stable release version: {version}");
    let parts: Vec<_> = version
        .strip_prefix('v')
        .unwrap_or(version)
        .split('.')
        .collect();
    if parts.len() != 3 {
        return Err(invalid());
    }
    let mut parsed = [0; 3];
    for (index, part) in parts.iter().enumerate() {
        if part.is_empty()
            || !part.bytes().all(|b| b.is_ascii_digit())
            || (part.len() > 1 && part.starts_with('0'))
        {
            return Err(invalid());
        }
        parsed[index] = part.parse().map_err(|_| invalid())?;
    }
    Ok(parsed)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn manifest() -> UpdateManifest {
        serde_json::from_str(r#"{
          "schemaVersion": 1,
          "platforms": {
            "macos-aarch64": {"version": "0.25.0", "releaseUrl": "https://github.com/OneKeePass/desktop/releases/tag/v0.25.0"},
            "macos-x86_64": {"version": "0.24.0", "releaseUrl": "https://github.com/OneKeePass/desktop/releases/tag/v0.24.0"},
            "windows-x86_64": {"version": "0.26.0", "releaseUrl": "https://github.com/OneKeePass/desktop/releases/tag/v0.26.0"},
            "linux-x86_64": {"version": "0.23.0", "releaseUrl": "https://github.com/OneKeePass/desktop/releases/tag/v0.23.0"}
          }
        }"#).unwrap()
    }

    #[test]
    fn platform_only_release_does_not_notify_other_platforms() {
        let windows = select_release(manifest(), "0.25.0".into(), "windows", "x86_64").unwrap();
        assert!(windows.update_available);
        assert_eq!(
            windows.download_url,
            "https://github.com/OneKeePass/desktop/releases/tag/v0.26.0"
        );
        let mac = select_release(manifest(), "0.25.0".into(), "macos", "aarch64").unwrap();
        assert!(!mac.update_available);
        assert!(mac.release_notes.is_empty());
        let linux = select_release(manifest(), "0.25.0".into(), "linux", "x86_64").unwrap();
        assert!(!linux.update_available);
    }

    #[test]
    fn architecture_is_selected_exactly() {
        let arm = select_release(manifest(), "0.24.0".into(), "macos", "aarch64").unwrap();
        let intel = select_release(manifest(), "0.24.0".into(), "macos", "x86_64").unwrap();
        assert!(arm.update_available);
        assert!(!intel.update_available);
        assert!(select_release(manifest(), "0.24.0".into(), "windows", "aarch64").is_err());
    }

    #[test]
    fn invalid_schema_missing_target_and_bad_url_are_errors() {
        let mut data = manifest();
        data.schema_version = 2;
        assert!(select_release(data, "0.24.0".into(), "macos", "aarch64").is_err());
        let mut data = manifest();
        data.platforms.clear();
        assert!(select_release(data, "0.24.0".into(), "macos", "aarch64").is_err());
        let mut data = manifest();
        data.platforms.get_mut("macos-aarch64").unwrap().release_url = "https://example.com".into();
        assert!(select_release(data, "0.24.0".into(), "macos", "aarch64").is_err());
        assert!(serde_json::from_str::<UpdateManifest>("<html>404</html>").is_err());
    }

    #[test]
    fn versions_are_compared_numerically_and_strictly() {
        assert!(parse_version("0.10.0").unwrap() > parse_version("0.9.0").unwrap());
        assert_eq!(
            parse_version("v0.25.0").unwrap(),
            parse_version("0.25.0").unwrap()
        );
        for value in [
            "",
            "0.25",
            "0.25.0-beta",
            "0.25.0.1",
            "0.025.0",
            "x.1.0",
            "+1.0.0",
        ] {
            assert!(parse_version(value).is_err(), "{value}");
        }
        let mut data = manifest();
        data.platforms.get_mut("macos-aarch64").unwrap().version = "invalid".into();
        assert!(select_release(data, "0.24.0".into(), "macos", "aarch64").is_err());
    }
}
