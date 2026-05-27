// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

//! Tauri commands — bridge between the TypeScript frontend and the lintab daemon.

use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::path::PathBuf;
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::UnixStream;

// ── IPC helpers (mirrors core-daemon/src/ipc.rs) ─────────────────────────────

fn socket_path() -> PathBuf {
    std::env::var("XDG_RUNTIME_DIR")
        .map(|d| PathBuf::from(d).join("lintab.sock"))
        .unwrap_or_else(|_| PathBuf::from("/tmp/lintab.sock"))
}

#[derive(Serialize)]
#[serde(tag = "cmd", rename_all = "snake_case")]
enum IpcCommand<'a> {
    Status,
    Scan,
    Connect {
        #[serde(skip_serializing_if = "Option::is_none")]
        ip: Option<&'a str>,
        #[serde(skip_serializing_if = "Option::is_none")]
        dname: Option<&'a str>,
    },
    Disconnect,
    SetMapping {
        screen_x:      u32,
        screen_y:      u32,
        screen_width:  u32,
        screen_height: u32,
        rotation:      u32,
        relative_mode: bool,
    },
    GetPrecision,
    CheckUpdate,
}

#[derive(Deserialize)]
struct IpcResponse {
    ok: bool,
    data: Option<Value>,
    error: Option<String>,
}

/// Try to connect to the daemon socket; if it's not running, auto-start it.
async fn ensure_daemon() -> Result<(), String> {
    if socket_path().exists() { return Ok(()); }

    // Attempt to launch the daemon in the background
    let _ = tokio::process::Command::new("lintab")
        .arg("daemon")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn();

    // Wait up to 2 s for the socket to appear
    for _ in 0..20 {
        tokio::time::sleep(tokio::time::Duration::from_millis(100)).await;
        if socket_path().exists() { return Ok(()); }
    }
    Err("El daemon no pudo iniciarse. Ejecuta: lintab setup --auto".into())
}

async fn call_daemon(cmd: &IpcCommand<'_>) -> Result<Value, String> {
    ensure_daemon().await?;

    let mut stream = UnixStream::connect(socket_path())
        .await
        .map_err(|_| "Daemon no responde. Ejecuta: lintab".to_string())?;

    let mut payload = serde_json::to_string(cmd).map_err(|e| e.to_string())?;
    payload.push('\n');
    stream.write_all(payload.as_bytes()).await.map_err(|e| e.to_string())?;

    let mut reader = BufReader::new(&mut stream);
    let mut line = String::new();
    reader.read_line(&mut line).await.map_err(|e| e.to_string())?;

    let resp: IpcResponse = serde_json::from_str(&line).map_err(|e| e.to_string())?;

    if resp.ok {
        Ok(resp.data.unwrap_or(Value::Null))
    } else {
        Err(resp.error.unwrap_or_else(|| "unknown daemon error".into()))
    }
}

// ── Tauri commands ────────────────────────────────────────────────────────────

#[tauri::command]
pub async fn get_daemon_status() -> Result<Value, String> {
    call_daemon(&IpcCommand::Status).await
}

#[tauri::command]
pub async fn scan_devices() -> Result<Value, String> {
    call_daemon(&IpcCommand::Scan).await
}

#[tauri::command]
pub async fn connect_device(ip: Option<String>, dname: Option<String>) -> Result<Value, String> {
    call_daemon(&IpcCommand::Connect {
        ip: ip.as_deref(),
        dname: dname.as_deref(),
    })
    .await
}

#[tauri::command]
pub async fn disconnect_device() -> Result<Value, String> {
    call_daemon(&IpcCommand::Disconnect).await
}

#[tauri::command]
pub async fn set_tablet_mapping(
    screen_x:      u32,
    screen_y:      u32,
    screen_width:  u32,
    screen_height: u32,
    rotation:      u32,
    relative_mode: bool,
) -> Result<Value, String> {
    call_daemon(&IpcCommand::SetMapping {
        screen_x, screen_y, screen_width, screen_height, rotation, relative_mode,
    })
    .await
}

#[tauri::command]
pub async fn get_precision() -> Result<Value, String> {
    call_daemon(&IpcCommand::GetPrecision).await
}

#[tauri::command]
pub async fn check_for_update() -> Result<Value, String> {
    call_daemon(&IpcCommand::CheckUpdate).await
}
