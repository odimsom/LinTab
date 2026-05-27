// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

//! Version check against GitHub Releases.
//!
//! Queries the latest release for `odimsom/LinTab` and compares it with the
//! running binary version.  Never auto-downloads or modifies the system —
//! just reports whether an update is available and where to get it.

use anyhow::Result;
use serde::Deserialize;

const GITHUB_REPO: &str = "odimsom/LinTab";
pub const CURRENT: &str = env!("CARGO_PKG_VERSION");

#[derive(Deserialize)]
struct Release {
    tag_name: String,
    html_url: String,
    body:     Option<String>,
}

pub struct UpdateInfo {
    pub current:          &'static str,
    pub latest:           String,
    pub update_available: bool,
    pub url:              String,
    pub changelog:        Option<String>,
}

/// Hit the GitHub Releases API and return update status.
pub async fn check() -> Result<UpdateInfo> {
    let api = format!(
        "https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
    );

    let release = reqwest::Client::new()
        .get(&api)
        .header("User-Agent", format!("lintab/{CURRENT}"))
        .header("Accept", "application/vnd.github+json")
        .send()
        .await?
        .error_for_status()?
        .json::<Release>()
        .await?;

    let latest = release.tag_name.trim_start_matches('v').to_string();
    let update_available = is_newer(&latest, CURRENT);

    Ok(UpdateInfo {
        current: CURRENT,
        latest,
        update_available,
        url: release.html_url,
        changelog: release.body,
    })
}

/// Semantic-version comparison: returns true if `candidate` > `base`.
fn is_newer(candidate: &str, base: &str) -> bool {
    let parse = |v: &str| -> (u32, u32, u32) {
        let mut p = v.split('.').filter_map(|s| s.parse::<u32>().ok());
        (p.next().unwrap_or(0), p.next().unwrap_or(0), p.next().unwrap_or(0))
    };
    parse(candidate) > parse(base)
}
