use std::time::Duration;

use serde::{Deserialize, Serialize};

// We have two ways to query GitHub for the latest release:
//
//   1. The JSON REST API at /repos/.../releases/latest. Rich data (assets,
//      release notes body) but rate-limited to 60 req/hr per IP for
//      unauthenticated callers — easy to hit on shared/CGNAT IPs (returns 403).
//
//   2. The releases.atom feed. Unauthenticated, not rate-limited, but does
//      not expose per-asset download URLs.
//
// The active implementation is the Atom feed (`check_via_atom_feed`).
// The REST implementation (`check_via_rest_api`) is kept for reference
// in case we add an authenticated/token-backed flow later.
const RELEASES_ATOM_URL: &str = "https://github.com/OneKeePass/desktop/releases.atom";
const RELEASES_API_URL: &str =
    "https://api.github.com/repos/OneKeePass/desktop/releases/latest";
const RELEASES_PAGE_URL: &str = "https://github.com/OneKeePass/desktop/releases/latest";
const USER_AGENT: &str = "OneKeePass-Updater";

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateCheckResult {
    pub update_available: bool,
    pub current_version: String,
    pub latest_version: String,
    pub release_notes: String,
    pub download_url: String,
}

pub async fn check_for_updates(current_version: String) -> Result<UpdateCheckResult, String> {
    check_via_atom_feed(current_version).await
}

fn http_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .map_err(|e| format!("Failed to build HTTP client: {e}"))
}

// =========================================================================
// Active path: GitHub releases.atom feed (unauthenticated, no rate limit)
// =========================================================================

async fn check_via_atom_feed(current_version: String) -> Result<UpdateCheckResult, String> {
    let client = http_client()?;

    let resp = client
        .get(RELEASES_ATOM_URL)
        .header(reqwest::header::USER_AGENT, USER_AGENT)
        .send()
        .await
        .map_err(|e| format!("Update check request failed: {e}"))?;

    if !resp.status().is_success() {
        return Err(format!(
            "GitHub releases feed returned status {}",
            resp.status()
        ));
    }

    let xml = resp
        .text()
        .await
        .map_err(|e| format!("Failed to read releases feed: {e}"))?;

    let entry =
        parse_latest_atom_entry(&xml).ok_or_else(|| "No releases found in feed.".to_string())?;

    let latest_version = entry.tag.trim_start_matches('v').to_string();
    let update_available = is_newer(&latest_version, &current_version);

    let download_url = entry
        .link
        .unwrap_or_else(|| RELEASES_PAGE_URL.to_string());

    Ok(UpdateCheckResult {
        update_available,
        current_version,
        latest_version,
        release_notes: entry.notes,
        download_url,
    })
}

struct AtomEntry {
    tag: String,
    notes: String,
    link: Option<String>,
}

fn parse_latest_atom_entry(xml: &str) -> Option<AtomEntry> {
    let entry_start = xml.find("<entry>")?;
    let entry_close_rel = xml[entry_start..].find("</entry>")?;
    let entry = &xml[entry_start..entry_start + entry_close_rel];

    // Prefer the tag from the link href (e.g. .../releases/tag/v0.20.0) —
    // it's an exact identifier; fall back to parsing the <title>.
    let link = extract_alternate_href(entry);
    let tag = link
        .as_deref()
        .and_then(|h| h.rsplit_once("/tag/").map(|(_, t)| t.to_string()))
        .or_else(|| extract_tag_from_title(&extract_element(entry, "title")?))?;

    let raw_notes = extract_element(entry, "content").unwrap_or_default();
    let notes = clean_html(&raw_notes);

    Some(AtomEntry { tag, notes, link })
}

fn extract_element(entry: &str, tag: &str) -> Option<String> {
    // Tolerate attributes on the opening tag (e.g. `<content type="html">`).
    let open = format!("<{tag}");
    let close = format!("</{tag}>");
    let open_idx = entry.find(&open)?;
    let after_open = open_idx + open.len();
    let gt = entry[after_open..].find('>')?;
    let body_start = after_open + gt + 1;
    let body_end_rel = entry[body_start..].find(&close)?;
    Some(entry[body_start..body_start + body_end_rel].to_string())
}

fn extract_alternate_href(entry: &str) -> Option<String> {
    for chunk in entry.split("<link") {
        if chunk.contains("rel=\"alternate\"") {
            if let Some(start) = chunk.find("href=\"") {
                let after = &chunk[start + 6..];
                if let Some(end) = after.find('"') {
                    return Some(after[..end].to_string());
                }
            }
        }
    }
    None
}

fn extract_tag_from_title(title: &str) -> Option<String> {
    // Titles look like "OneKeePass v0.20.0" — grab the last whitespace-separated token.
    title.split_whitespace().last().map(|s| s.to_string())
}

fn clean_html(s: &str) -> String {
    // Decode entities first so that block-boundary replacements (which
    // look for literal `</p>`, `<br>`, etc.) match the real tags rather
    // than their entity-encoded form (`&lt;/p&gt;`).
    let decoded = decode_entities(s);

    // Promote block boundaries to explicit newlines before stripping tags,
    // so inline elements like <a> or <strong> don't break the sentence.
    let with_breaks = decoded
        .replace("<br>", "\n")
        .replace("<br/>", "\n")
        .replace("<br />", "\n")
        .replace("</p>", "\n\n")
        .replace("</li>", "\n")
        .replace("<li>", "\n* ")
        .replace("</h1>", "\n\n")
        .replace("</h2>", "\n\n")
        .replace("</h3>", "\n\n")
        .replace("</h4>", "\n\n")
        .replace("</h5>", "\n\n")
        .replace("</h6>", "\n\n");

    let stripped = strip_tags(&with_breaks);

    // Collapse runs of blank lines that result from stripped block elements.
    let mut out = String::with_capacity(stripped.len());
    let mut blank_run = 0;
    for line in stripped.lines() {
        let trimmed = line.trim_end();
        if trimmed.is_empty() {
            blank_run += 1;
            if blank_run <= 1 {
                out.push('\n');
            }
        } else {
            blank_run = 0;
            out.push_str(trimmed);
            out.push('\n');
        }
    }
    out.trim().to_string()
}

fn decode_entities(s: &str) -> String {
    s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        // &amp; must be last so we do not double-decode entities like &amp;lt;.
        .replace("&amp;", "&")
}

fn strip_tags(s: &str) -> String {
    // Drop everything between `<` and `>`. Block boundaries are already
    // replaced with newlines by `clean_html`, so inline tags (e.g. `<a>`,
    // `<strong>`) disappear without breaking the surrounding sentence.
    let mut out = String::with_capacity(s.len());
    let mut in_tag = false;
    for ch in s.chars() {
        match ch {
            '<' => in_tag = true,
            '>' => in_tag = false,
            c if !in_tag => out.push(c),
            _ => {}
        }
    }
    out
}

// =========================================================================
// Reference path: GitHub REST API (rich data, but rate-limited per IP)
// =========================================================================

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
struct GithubRelease {
    tag_name: String,
    #[serde(default)]
    name: Option<String>,
    #[serde(default)]
    body: Option<String>,
    #[serde(default)]
    assets: Vec<GithubAsset>,
    #[serde(default)]
    html_url: Option<String>,
}

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
struct GithubAsset {
    name: String,
    browser_download_url: String,
}

#[allow(dead_code)]
async fn check_via_rest_api(current_version: String) -> Result<UpdateCheckResult, String> {
    let client = http_client()?;

    let resp = client
        .get(RELEASES_API_URL)
        .header(reqwest::header::USER_AGENT, USER_AGENT)
        .header(reqwest::header::ACCEPT, "application/vnd.github+json")
        .send()
        .await
        .map_err(|e| format!("Update check request failed: {e}"))?;

    if !resp.status().is_success() {
        return Err(format!(
            "GitHub releases API returned status {}",
            resp.status()
        ));
    }

    let release: GithubRelease = resp
        .json()
        .await
        .map_err(|e| format!("Failed to parse releases response: {e}"))?;

    let latest_version = release.tag_name.trim_start_matches('v').to_string();
    let update_available = is_newer(&latest_version, &current_version);

    let download_url = if update_available {
        match_asset(&release.assets).unwrap_or_else(|| {
            release
                .html_url
                .clone()
                .unwrap_or_else(|| RELEASES_PAGE_URL.to_string())
        })
    } else {
        release
            .html_url
            .clone()
            .unwrap_or_else(|| RELEASES_PAGE_URL.to_string())
    };

    let release_notes = release
        .name
        .filter(|s| !s.trim().is_empty())
        .map(|n| {
            if let Some(body) = release.body.as_ref().filter(|b| !b.trim().is_empty()) {
                format!("{n}\n\n{body}")
            } else {
                n
            }
        })
        .or_else(|| release.body.clone())
        .unwrap_or_default();

    Ok(UpdateCheckResult {
        update_available,
        current_version,
        latest_version,
        release_notes,
        download_url,
    })
}

#[allow(dead_code)]
fn match_asset(assets: &[GithubAsset]) -> Option<String> {
    let os = std::env::consts::OS;
    let arch = std::env::consts::ARCH;

    let (exts, arch_hints): (&[&str], &[&str]) = match os {
        "macos" => (
            &[".dmg"],
            if arch == "aarch64" {
                &["aarch64", "arm64"]
            } else {
                &["x86_64", "x64", "intel"]
            },
        ),
        "windows" => (
            &[".msi", ".exe"],
            if arch == "aarch64" {
                &["aarch64", "arm64"]
            } else {
                &["x86_64", "x64"]
            },
        ),
        "linux" => (
            &[".AppImage", ".deb", ".rpm"],
            if arch == "aarch64" {
                &["aarch64", "arm64"]
            } else {
                &["x86_64", "amd64"]
            },
        ),
        _ => return None,
    };

    for ext in exts {
        for hint in arch_hints {
            if let Some(a) = assets
                .iter()
                .find(|a| a.name.ends_with(ext) && a.name.to_lowercase().contains(hint))
            {
                return Some(a.browser_download_url.clone());
            }
        }
        if let Some(a) = assets.iter().find(|a| a.name.ends_with(ext)) {
            return Some(a.browser_download_url.clone());
        }
    }

    None
}

// =========================================================================
// Shared
// =========================================================================

fn is_newer(latest: &str, current: &str) -> bool {
    let parse = |s: &str| -> Vec<u32> {
        s.trim_start_matches('v')
            .split(['.', '-'])
            .take(3)
            .map(|p| p.parse::<u32>().unwrap_or(0))
            .collect()
    };
    parse(latest) > parse(current)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn is_newer_basic() {
        assert!(is_newer("0.23.0", "0.22.0"));
        assert!(is_newer("v0.23.0", "0.22.0"));
        assert!(!is_newer("0.22.0", "0.22.0"));
        assert!(!is_newer("0.22.0", "0.23.0"));
        assert!(is_newer("1.0.0", "0.99.0"));
    }

    #[test]
    fn parses_atom_entry() {
        let xml = r#"<?xml version="1.0" encoding="UTF-8"?>
<feed>
  <entry>
    <id>tag:github.com,2008:Repository/1/v0.20.0</id>
    <updated>2026-04-28T15:15:35Z</updated>
    <link rel="alternate" type="text/html" href="https://github.com/OneKeePass/desktop/releases/tag/v0.20.0"/>
    <title>OneKeePass v0.20.0</title>
    <content type="html">&lt;p&gt;See assets to install. See the &lt;a href=&quot;CHANGELOG&quot;&gt;CHANGELOG&lt;/a&gt;&lt;/p&gt;</content>
  </entry>
  <entry>
    <title>OneKeePass v0.19.0</title>
  </entry>
</feed>"#;

        let entry = parse_latest_atom_entry(xml).expect("should parse");
        assert_eq!(entry.tag, "v0.20.0");
        assert_eq!(
            entry.link.as_deref(),
            Some("https://github.com/OneKeePass/desktop/releases/tag/v0.20.0")
        );
        // Inline anchor must not split the sentence onto separate lines.
        assert_eq!(entry.notes, "See assets to install. See the CHANGELOG");
        assert!(!entry.notes.contains("&lt;"));
        assert!(!entry.notes.contains("<p>"));
    }

    #[test]
    fn clean_html_preserves_paragraph_breaks() {
        let html =
            "&lt;p&gt;First paragraph.&lt;/p&gt;\n&lt;p&gt;&lt;strong&gt;Second&lt;/strong&gt; paragraph.&lt;/p&gt;";
        let out = clean_html(html);
        assert_eq!(out, "First paragraph.\n\nSecond paragraph.");
    }

    #[test]
    fn extract_tag_from_title_handles_v_prefix() {
        assert_eq!(
            extract_tag_from_title("OneKeePass v0.20.0").as_deref(),
            Some("v0.20.0")
        );
    }
}
