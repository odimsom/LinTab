// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

use crate::{proto::{ActionType, TabletEvent}, setup::TABLET_PORT, uinput::TabletDevice};
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
    pub rotation:       u32, // 0 | 90 | 180 | 270
    pub relative_mode:  bool,
}

impl Default for Mapping {
    fn default() -> Self {
        Self { monitor_width: 1920, monitor_height: 1080, rotation: 0, relative_mode: true }
    }
}

pub type SharedMapping   = Arc<RwLock<Mapping>>;
pub type SharedLastEvent = Arc<RwLock<Option<PrecisionSnapshot>>>;

/// Last known precision values, published after each event for the GUI to poll.
#[derive(Clone, Default)]
pub struct PrecisionSnapshot {
    pub pressure: i32,
    pub tilt_x:   i32,
    pub tilt_y:   i32,
}

pub fn new_shared_mapping() -> SharedMapping {
    Arc::new(RwLock::new(Mapping::default()))
}

pub fn new_shared_last_event() -> SharedLastEvent {
    Arc::new(RwLock::new(None))
}

// ── Coordinate helpers ────────────────────────────────────────────────────────

/// Maps an Android pixel coordinate to uinput abstract space (0–32767).
#[inline]
fn map_axis(pos: i32, max: i32) -> i32 {
    if max <= 0 { return pos.clamp(0, 32767); }
    ((pos as f32 / max as f32) * 32767.0) as i32
}

/// Apply rotation to absolute (ax, ay) coordinates in [0–32767] space.
fn apply_rotation(ax: i32, ay: i32, rotation: u32) -> (i32, i32) {
    match rotation {
        90  => (ay, 32767 - ax),
        180 => (32767 - ax, 32767 - ay),
        270 => (32767 - ay, ax),
        _   => (ax, ay),
    }
}

// ── Daemon transport loop ─────────────────────────────────────────────────────

pub async fn serve_loop(
    device:     Arc<Mutex<TabletDevice>>,
    mapping:    SharedMapping,
    last_event: SharedLastEvent,
) -> Result<()> {
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
                let evt = Arc::clone(&last_event);
                tokio::spawn(async move {
                    if let Err(e) = handle_stream(stream, dev, map, evt).await {
                        warn!("Sesión terminada: {e:#}");
                    }
                });
            }
            Err(e) => error!("Error aceptando conexión TCP: {e:#}"),
        }
    }
}

async fn handle_stream(
    stream:     TcpStream,
    device:     Arc<Mutex<TabletDevice>>,
    mapping:    SharedMapping,
    last_event: SharedLastEvent,
) -> Result<()> {
    stream.set_nodelay(true)?;
    let mut reader = stream;

    // Virtual cursor position for relative mode (center of uinput space)
    let mut virt_x: i32 = 16384;
    let mut virt_y: i32 = 16384;
    // Reference touch position captured on ACTION_DOWN
    let mut ref_x:  i32 = 0;
    let mut ref_y:  i32 = 0;

    loop {
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

        let map = mapping.read().await.clone();
        let is_up = ev.action == ActionType::ActionUp as i32;

        if is_up {
            // Full pen lift — releases BTN_TOOL_PEN so apps don't draw on jump
            let _ = device.lock().await.emit_pen_up();
        } else {
            let (final_x, final_y) = if map.relative_mode {
                // ── Relative mode ────────────────────────────────────────────
                // Touch position is used as movement delta, not absolute coords.
                // The virtual cursor persists between strokes.
                if ev.action == ActionType::ActionDown as i32 {
                    // Anchor: record touch-down position as reference
                    ref_x = ev.x;
                    ref_y = ev.y;
                    // Don't move cursor on pen down
                    (virt_x, virt_y)
                } else {
                    // Compute delta from reference, scale to uinput space
                    let raw_scale_x = if ev.screen_width  > 0 { 32767.0 / ev.screen_width  as f32 } else { 1.0 };
                    let raw_scale_y = if ev.screen_height > 0 { 32767.0 / ev.screen_height as f32 } else { 1.0 };
                    let dx = ((ev.x - ref_x) as f32 * raw_scale_x) as i32;
                    let dy = ((ev.y - ref_y) as f32 * raw_scale_y) as i32;
                    ref_x = ev.x;
                    ref_y = ev.y;
                    virt_x = (virt_x + dx).clamp(0, 32767);
                    virt_y = (virt_y + dy).clamp(0, 32767);
                    (virt_x, virt_y)
                }
            } else {
                // ── Absolute mode ─────────────────────────────────────────────
                let ax = map_axis(ev.x, ev.screen_width).clamp(0, 32767);
                let ay = map_axis(ev.y, ev.screen_height).clamp(0, 32767);
                apply_rotation(ax, ay, map.rotation)
            };

            device
                .lock()
                .await
                .emit_pen_event(
                    final_x,
                    final_y,
                    ev.pressure.clamp(0, 8191),
                    ev.tilt_x.clamp(-90, 90),
                    ev.tilt_y.clamp(-90, 90),
                )
                .context("Error al emitir evento al kernel")?;
        }

        // Publish precision snapshot for the GUI to poll
        *last_event.write().await = Some(PrecisionSnapshot {
            pressure: ev.pressure.clamp(0, 8191),
            tilt_x:   ev.tilt_x.clamp(-90, 90),
            tilt_y:   ev.tilt_y.clamp(-90, 90),
        });
    }

    let _ = device.lock().await.emit_pen_up();
    info!("Android desconectado — dispositivo uinput liberado.");
    Ok(())
}

// ── One-shot CLI transport ────────────────────────────────────────────────────

pub async fn serve(device: TabletDevice, _target: Option<String>) -> Result<()> {
    let dev        = Arc::new(Mutex::new(device));
    let mapping    = new_shared_mapping();
    let last_event = new_shared_last_event();
    serve_loop(dev, mapping, last_event).await
}
