// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

//! Background daemon mode.
//!
//! Startup sequence:
//!   1. Check udev permissions — guide user if missing.
//!   2. Create the virtual uinput tablet device.
//!   3. Advertise service via mDNS so Android finds the daemon automatically.
//!   4. Spawn the ADB device watcher (auto reverse-tunnel on USB plug-in).
//!   5. Spawn the TCP transport loop (receives stylus events from Android).
//!   6. Serve the Unix IPC socket (Tauri GUI + CLI one-shots).

use crate::{
    ipc::{socket_path, IpcCommand, IpcResponse},
    scan, setup, transport,
    transport::{SharedLastEvent, SharedMapping},
    uinput,
};
use anyhow::Result;
use mdns_sd::ServiceDaemon;
use std::sync::Arc;
use tokio::{
    io::{AsyncBufReadExt, AsyncWriteExt, BufReader},
    net::{UnixListener, UnixStream},
    sync::Mutex,
};
use tracing::{error, info, warn};

pub async fn run() -> Result<()> {
    // ── 1. Permission check ───────────────────────────────────────────────────
    if !setup::uinput_accessible() {
        setup::print_setup_guide();
        anyhow::bail!("Permisos insuficientes. Sigue las instrucciones anteriores.");
    }

    // ── 2. Virtual uinput device ──────────────────────────────────────────────
    let device = Arc::new(Mutex::new(uinput::TabletDevice::new()?));
    info!("Dispositivo virtual uinput creado — LinTab aparece como tableta en tu sistema.");

    // ── 3. mDNS advertisement ─────────────────────────────────────────────────
    let mdns = ServiceDaemon::new()?;
    match scan::mdns::advertise(&mdns, setup::TABLET_PORT) {
        Ok(_)  => info!("Servicio mDNS activo — la app Android te encontrará automáticamente."),
        Err(e) => warn!("mDNS no disponible ({e:#}) — usa la IP manualmente si estás en WiFi."),
    }

    // ── 4. ADB device watcher ─────────────────────────────────────────────────
    tokio::spawn(setup::watch_adb_devices());

    // ── 5. TCP transport loop ─────────────────────────────────────────────────
    let mapping:    SharedMapping   = transport::new_shared_mapping();
    let last_event: SharedLastEvent = transport::new_shared_last_event();
    {
        let dev = Arc::clone(&device);
        let map = Arc::clone(&mapping);
        let evt = Arc::clone(&last_event);
        tokio::spawn(async move {
            if let Err(e) = transport::serve_loop(dev, map, evt).await {
                error!("Error en el transporte: {e:#}");
            }
        });
    }

    // ── 6. IPC socket ─────────────────────────────────────────────────────────
    let sock_path = socket_path();
    if sock_path.exists() {
        warn!("Eliminando socket anterior en {}", sock_path.display());
        std::fs::remove_file(&sock_path)?;
    }

    let listener = UnixListener::bind(&sock_path)?;
    info!("LinTab daemon listo. Socket IPC: {}", sock_path.display());
    info!("Abre la app LinTab en tu Android para comenzar.");

    loop {
        match listener.accept().await {
            Ok((stream, _)) => {
                let dev = Arc::clone(&device);
                let map = Arc::clone(&mapping);
                let evt = Arc::clone(&last_event);
                tokio::spawn(handle_client(stream, dev, map, evt));
            }
            Err(e) => error!("Error en socket IPC: {e:#}"),
        }
    }
}

// ── Per-client IPC handler ────────────────────────────────────────────────────

async fn handle_client(
    stream:     UnixStream,
    device:     Arc<Mutex<uinput::TabletDevice>>,
    mapping:    SharedMapping,
    last_event: SharedLastEvent,
) {
    let (reader_half, mut writer) = stream.into_split();
    let mut lines = BufReader::new(reader_half).lines();

    while let Ok(Some(line)) = lines.next_line().await {
        let response = match serde_json::from_str::<IpcCommand>(&line) {
            Ok(cmd) => dispatch(cmd, &device, &mapping, &last_event).await,
            Err(e)  => IpcResponse::err(format!("parse error: {e}")),
        };

        let mut encoded = serde_json::to_string(&response).unwrap_or_default();
        encoded.push('\n');
        if writer.write_all(encoded.as_bytes()).await.is_err() { break; }
    }
}

// ── Command dispatcher ────────────────────────────────────────────────────────

async fn dispatch(
    cmd:        IpcCommand,
    _device:    &Arc<Mutex<uinput::TabletDevice>>,
    mapping:    &SharedMapping,
    last_event: &SharedLastEvent,
) -> IpcResponse {
    match cmd {
        IpcCommand::Status => IpcResponse::ok(serde_json::json!({
            "running": true,
            "version": env!("CARGO_PKG_VERSION"),
            "port": setup::TABLET_PORT,
            "relative_mode": mapping.read().await.relative_mode,
        })),

        IpcCommand::Scan => {
            let (mdns_res, adb_res) = tokio::join!(
                scan::mdns::discover(),
                scan::adb::list_devices(),
            );
            IpcResponse::ok(serde_json::json!({
                "mdns": mdns_res.unwrap_or_default().iter()
                    .map(|d| serde_json::json!({"name": d.name, "addr": d.addr}))
                    .collect::<Vec<_>>(),
                "adb": adb_res.unwrap_or_default().iter()
                    .map(|d| serde_json::json!({"serial": d.serial, "state": d.state}))
                    .collect::<Vec<_>>(),
            }))
        }

        IpcCommand::Connect { ip, dname } => {
            let target = match (dname, ip) {
                (Some(name), _) => match scan::mdns::resolve(&name).await {
                    Ok(addr) => addr,
                    Err(e)   => return IpcResponse::err(e.to_string()),
                },
                (_, Some(raw)) => raw,
                _ => return IpcResponse::err("Especifica --ip o --dname"),
            };
            IpcResponse::ok(serde_json::json!({
                "message": format!("Esperando conexión de {target}:{}", setup::TABLET_PORT)
            }))
        }

        IpcCommand::Disconnect => {
            IpcResponse::ok(serde_json::json!({"disconnected": true}))
        }

        IpcCommand::SetMapping { screen_x, screen_y, screen_width, screen_height, rotation, relative_mode } => {
            let mut map = mapping.write().await;
            map.monitor_width  = screen_width;
            map.monitor_height = screen_height;
            map.rotation       = rotation;
            map.relative_mode  = relative_mode;
            IpcResponse::ok(serde_json::json!({
                "mapping": {"x": screen_x, "y": screen_y,
                            "w": screen_width, "h": screen_height,
                            "rotation": rotation,
                            "relative_mode": relative_mode}
            }))
        }

        IpcCommand::GetPrecision => {
            let snap = last_event.read().await.clone().unwrap_or_default();
            IpcResponse::ok(serde_json::json!({
                "pressure": snap.pressure,
                "tilt_x":   snap.tilt_x,
                "tilt_y":   snap.tilt_y,
            }))
        }
    }
}
