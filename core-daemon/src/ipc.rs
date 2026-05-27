// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

//! Shared IPC types and helpers used by both the daemon (server) and
//! CLI one-shot commands (client). Transport: newline-delimited JSON
//! over a Unix domain socket.

use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

// ── Types ─────────────────────────────────────────────────────────────────────

/// Commands understood by the daemon.
#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(tag = "cmd", rename_all = "snake_case")]
pub enum IpcCommand {
    /// Query daemon status.
    Status,
    /// Discover devices on the local network and over USB.
    Scan,
    /// Initiate a tablet session with a specific device.
    Connect {
        #[serde(skip_serializing_if = "Option::is_none")]
        ip: Option<String>,
        #[serde(skip_serializing_if = "Option::is_none")]
        dname: Option<String>,
    },
    /// Tear down the active tablet session.
    Disconnect,
    /// Update the screen-to-tablet mapping and input mode.
    SetMapping {
        screen_x: u32,
        screen_y: u32,
        screen_width: u32,
        screen_height: u32,
        rotation: u32,
        #[serde(default)]
        relative_mode: bool,
    },
    /// Returns last received pressure/tilt snapshot for the GUI to display.
    GetPrecision,
}

/// Response envelope sent back for every command.
#[derive(Debug, Serialize, Deserialize)]
pub struct IpcResponse {
    pub ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<serde_json::Value>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

impl IpcResponse {
    pub fn ok(data: impl Serialize) -> Self {
        Self {
            ok: true,
            data: Some(serde_json::to_value(data).unwrap_or(serde_json::Value::Null)),
            error: None,
        }
    }

    pub fn err(msg: impl ToString) -> Self {
        Self { ok: false, data: None, error: Some(msg.to_string()) }
    }
}

// ── Socket path ───────────────────────────────────────────────────────────────

/// Canonical path for the daemon's Unix socket.
/// Uses `$XDG_RUNTIME_DIR` if available, otherwise `/tmp/lintab.sock`.
pub fn socket_path() -> PathBuf {
    std::env::var("XDG_RUNTIME_DIR")
        .map(|d| PathBuf::from(d).join("lintab.sock"))
        .unwrap_or_else(|_| PathBuf::from("/tmp/lintab.sock"))
}

// ── Client helper ─────────────────────────────────────────────────────────────

/// Send one command to the running daemon and return its response.
/// Returns `Err` immediately if no daemon is listening on the socket.
pub async fn send_ipc_command(cmd: &IpcCommand) -> Result<IpcResponse> {
    use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
    use tokio::net::UnixStream;

    let mut stream = UnixStream::connect(socket_path()).await?;

    let mut payload = serde_json::to_string(cmd)?;
    payload.push('\n');
    stream.write_all(payload.as_bytes()).await?;

    let mut reader = BufReader::new(&mut stream);
    let mut line = String::new();
    reader.read_line(&mut line).await?;

    Ok(serde_json::from_str::<IpcResponse>(&line)?)
}
