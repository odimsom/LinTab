// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

use anyhow::{Context, Result};
use serde::Serialize;
use std::path::PathBuf;
use tokio::process::Command;

#[derive(Serialize)]
pub struct AdbDevice {
    pub serial: String,
    pub state: String,
}

/// Locate the `adb` binary regardless of how the process was launched.
///
/// systemd user services inherit a minimal PATH that typically omits Android
/// SDK directories, so we probe environment variables and common install paths
/// before falling back to a PATH walk.
pub fn find_adb() -> Option<PathBuf> {
    // Env vars set by Android Studio / sdkmanager
    for var in &["ANDROID_HOME", "ANDROID_SDK_ROOT"] {
        if let Ok(root) = std::env::var(var) {
            let p = PathBuf::from(root).join("platform-tools/adb");
            if p.is_file() {
                return Some(p);
            }
        }
    }

    // Common fixed install locations
    let home = std::env::var("HOME").unwrap_or_default();
    let candidates = [
        format!("{home}/Android/Sdk/platform-tools/adb"), // Android Studio default
        format!("{home}/.local/lib/android-sdk/platform-tools/adb"),
        "/opt/android-sdk/platform-tools/adb".into(),
        "/usr/lib/android-sdk/platform-tools/adb".into(),
        "/usr/bin/adb".into(),
        "/usr/local/bin/adb".into(),
    ];
    for path in &candidates {
        let p = PathBuf::from(path);
        if p.is_file() {
            return Some(p);
        }
    }

    // Fall back to PATH resolution
    if let Ok(path_var) = std::env::var("PATH") {
        for dir in path_var.split(':') {
            let p = PathBuf::from(dir).join("adb");
            if p.is_file() {
                return Some(p);
            }
        }
    }

    None
}

/// List USB-connected Android devices by calling `adb devices`.
/// Returns an empty vec (not an error) if `adb` is not installed.
pub async fn list_devices() -> Result<Vec<AdbDevice>> {
    let adb = match find_adb() {
        Some(p) => p,
        None => {
            tracing::warn!("`adb` not found – skipping USB scan");
            return Ok(vec![]);
        }
    };

    let output = match Command::new(&adb).arg("devices").output().await {
        Ok(o) => o,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
            tracing::warn!("`adb` not found in PATH – skipping USB scan");
            return Ok(vec![]);
        }
        Err(e) => return Err(e).context("failed to run `adb devices`"),
    };

    let stdout = String::from_utf8_lossy(&output.stdout);
    let devices = parse_adb_output(&stdout);
    Ok(devices)
}

/// Parse `adb devices` output into a list of devices.
/// Expected format:
/// ```
/// List of devices attached
/// R3CN123ABCD    device
/// emulator-5554  offline
/// ```
fn parse_adb_output(raw: &str) -> Vec<AdbDevice> {
    raw.lines()
        .skip(1) // skip "List of devices attached"
        .filter_map(|line| {
            let mut parts = line.split_whitespace();
            let serial = parts.next()?.to_owned();
            let state = parts.next()?.to_owned();
            Some(AdbDevice { serial, state })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::parse_adb_output;

    #[test]
    fn parses_normal_output() {
        let raw = "List of devices attached\nR3CN123\tdevice\nemulator-5554\toffline\n";
        let devices = parse_adb_output(raw);
        assert_eq!(devices.len(), 2);
        assert_eq!(devices[0].serial, "R3CN123");
        assert_eq!(devices[0].state, "device");
        assert_eq!(devices[1].state, "offline");
    }

    #[test]
    fn parses_empty_output() {
        let raw = "List of devices attached\n";
        assert!(parse_adb_output(raw).is_empty());
    }
}
