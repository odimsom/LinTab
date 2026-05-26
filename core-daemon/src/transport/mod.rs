// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

use crate::{proto::TabletEvent, setup::TABLET_PORT, uinput::TabletDevice};
use anyhow::{Context, Result};
use prost::Message;
use std::sync::Arc;
use tokio::{
    io::AsyncReadExt,
    net::{TcpListener, TcpStream},
    sync::{Mutex, RwLock},
};
use tracing::{error, info, warn};

// ── Mapping state (updated via IPC SetMapping) ────────────────────────────────

#[derive(Clone)]
pub struct Mapping {
    pub monitor_width:  u32,
    pub monitor_height: u32,
}

impl Default for Mapping {
    fn default() -> Self { Self { monitor_width: 1920, monitor_height: 1080 } }
}

pub type SharedMapping = Arc<RwLock<Mapping>>;

pub fn new_shared_mapping() -> SharedMapping {
    Arc::new(RwLock::new(Mapping::default()))
}

// ── Coordinate helper ─────────────────────────────────────────────────────────

/// Maps an Android pixel coordinate to uinput abstract space (0–32767).
/// X_linux = (X_android / W_android) * 32767
#[inline]
fn map_axis(android_pos: i32, android_max: i32) -> i32 {
    if android_max <= 0 { return android_pos.clamp(0, 32767); }
    ((android_pos as f32 / android_max as f32) * 32767.0) as i32
}

// ── Daemon transport loop ─────────────────────────────────────────────────────

/// Long-running loop: listens on `0.0.0.0:TABLET_PORT` for the Android client.
/// Works for both USB (ADB reverse tunnel → localhost) and direct WiFi.
pub async fn serve_loop(device: Arc<Mutex<TabletDevice>>, mapping: SharedMapping) -> Result<()> {
    let addr = format!("0.0.0.0:{TABLET_PORT}");
    let listener = TcpListener::bind(&addr).await.with_context(|| {
        format!(
            "No se pudo abrir el puerto {TABLET_PORT}.\n\
             ¿Hay otra instancia de LinTab corriendo?"
        )
    })?;

    info!("Esperando la app Android en el puerto {TABLET_PORT}…");
    info!("  USB  → conecta el teléfono y abre la app LinTab.");
    info!("  WiFi → abre la app LinTab en la misma red.");

    loop {
        match listener.accept().await {
            Ok((stream, addr)) => {
                info!("📱 Android conectado desde {addr}");
                let dev = Arc::clone(&device);
                let map = Arc::clone(&mapping);
                tokio::spawn(async move {
                    if let Err(e) = handle_stream(stream, dev, map).await {
                        warn!("Sesión terminada: {e:#}");
                    }
                });
            }
            Err(e) => error!("Error aceptando conexión TCP: {e:#}"),
        }
    }
}

async fn handle_stream(
    stream: TcpStream,
    device: Arc<Mutex<TabletDevice>>,
    mapping: SharedMapping,
) -> Result<()> {
    stream.set_nodelay(true)?; // critical for low latency
    let mut reader = stream;

    loop {
        // 4-byte big-endian length prefix
        let len = match reader.read_u32().await {
            Ok(n)                                                     => n as usize,
            Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof  => break,
            Err(e)                                                    => return Err(e.into()),
        };

        if len == 0 || len > 65_536 {
            warn!("Frame fuera de rango (len={len}), cerrando conexión");
            break;
        }

        let mut payload = vec![0u8; len];
        reader.read_exact(&mut payload).await?;

        let ev = TabletEvent::decode(payload.as_slice())
            .context("Error al decodificar TabletEvent")?;

        // Map Android pixel coords → uinput abstract space (0–32767)
        let x = map_axis(ev.x, ev.screen_width).clamp(0, 32767);
        let y = map_axis(ev.y, ev.screen_height).clamp(0, 32767);

        let _ = mapping.read().await; // hold read lock briefly for future use

        device
            .lock()
            .await
            .emit_pen_event(
                x,
                y,
                ev.pressure.clamp(0, 8191),
                ev.tilt_x.clamp(-90, 90),
                ev.tilt_y.clamp(-90, 90),
            )
            .context("Error al emitir evento al kernel")?;
    }

    // Lift the pen when the Android app disconnects
    let _ = device.lock().await.emit_pen_up();
    info!("Android desconectado — dispositivo uinput liberado.");
    Ok(())
}

// ── One-shot CLI transport ────────────────────────────────────────────────────

/// Used by `lintab connect` when no daemon is running.
/// Wraps serve_loop with an owned device.
pub async fn serve(device: TabletDevice, _target: Option<String>) -> Result<()> {
    let dev     = Arc::new(Mutex::new(device));
    let mapping = new_shared_mapping();
    serve_loop(dev, mapping).await
}
