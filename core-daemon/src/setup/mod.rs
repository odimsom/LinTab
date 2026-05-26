// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

//! First-run setup helpers and background ADB device watcher.
//! All user-facing strings are in Spanish for a frictionless experience.

use anyhow::Result;
use std::collections::HashSet;
use tokio::process::Command;
use tracing::{info, warn};

const UDEV_RULE: &str = r#"KERNEL=="uinput", GROUP="input", MODE="0660""#;
const UDEV_RULE_PATH: &str = "/etc/udev/rules.d/99-lintab.rules";
pub const TABLET_PORT: u16 = 7654;

// ── Checks ────────────────────────────────────────────────────────────────────

/// Returns true if the current process can open /dev/uinput for writing.
pub fn uinput_accessible() -> bool {
    std::fs::OpenOptions::new()
        .write(true)
        .open("/dev/uinput")
        .is_ok()
}

/// Returns true if either the LinTab or generic uinput udev rule exists.
pub fn udev_rule_exists() -> bool {
    std::path::Path::new(UDEV_RULE_PATH).exists()
        || std::path::Path::new("/etc/udev/rules.d/99-uinput.rules").exists()
}

/// Returns true if `adb` is on PATH.
pub async fn adb_available() -> bool {
    Command::new("adb")
        .arg("version")
        .output()
        .await
        .map(|o| o.status.success())
        .unwrap_or(false)
}

// ── Setup guide ───────────────────────────────────────────────────────────────

pub fn print_setup_guide() {
    eprintln!();
    eprintln!("╔══════════════════════════════════════════════════════════════════╗");
    eprintln!("║  LinTab — Configuración de permisos (una sola vez)              ║");
    eprintln!("╠══════════════════════════════════════════════════════════════════╣");
    eprintln!("║                                                                  ║");
    eprintln!("║  Ejecuta en tu terminal:                                         ║");
    eprintln!("║                                                                  ║");
    eprintln!("║  sudo usermod -aG input $USER                                    ║");
    eprintln!("║  echo '{UDEV_RULE}' | \\");
    eprintln!("║      sudo tee {UDEV_RULE_PATH}");
    eprintln!("║  sudo udevadm control --reload-rules && sudo udevadm trigger     ║");
    eprintln!("║                                                                  ║");
    eprintln!("║  O más fácil:  lintab setup --auto                               ║");
    eprintln!("║                                                                  ║");
    eprintln!("║  Después cierra sesión y vuelve a entrar.                        ║");
    eprintln!("╚══════════════════════════════════════════════════════════════════╝");
    eprintln!();
}

// ── Automatic setup ───────────────────────────────────────────────────────────

/// Attempts to configure udev rules using pkexec (GUI polkit) or sudo.
pub async fn run_auto_setup() -> Result<()> {
    let script = format!(
        "usermod -aG input $(logname 2>/dev/null || whoami) && \
         printf '{UDEV_RULE}\\n' > {UDEV_RULE_PATH} && \
         udevadm control --reload-rules && \
         udevadm trigger"
    );

    println!("Intentando configurar permisos automáticamente…");

    // Try pkexec first (GNOME/KDE/XFCE with polkit)
    if let Ok(status) = Command::new("pkexec")
        .args(["bash", "-c", &script])
        .status()
        .await
    {
        if status.success() {
            println!("\n✓ Permisos configurados correctamente.");
            println!("  Cierra sesión y vuelve a entrar para activarlos.");
            return Ok(());
        }
    }

    // Fall back to sudo (terminal environments)
    let status = Command::new("sudo")
        .args(["bash", "-c", &script])
        .status()
        .await?;

    if status.success() {
        println!("\n✓ Permisos configurados correctamente con sudo.");
        println!("  Cierra sesión y vuelve a entrar para activarlos.");
    } else {
        anyhow::bail!(
            "No se pudieron configurar los permisos automáticamente.\n\
             Ejecuta los comandos de `lintab setup` manualmente."
        );
    }

    Ok(())
}

// ── ADB device watcher ────────────────────────────────────────────────────────

/// Background task: polls `adb devices` every 3 s.
/// When a new USB device appears, automatically runs
/// `adb reverse tcp:7654 tcp:7654` so the Android app can reach the daemon
/// through the USB tunnel without any user action.
pub async fn watch_adb_devices() {
    let mut known: HashSet<String> = HashSet::new();

    loop {
        tokio::time::sleep(std::time::Duration::from_secs(3)).await;

        let output = match Command::new("adb").arg("devices").output().await {
            Ok(o) => o,
            Err(_) => continue, // adb not installed — silently skip
        };

        let stdout = String::from_utf8_lossy(&output.stdout);
        let current: HashSet<String> = stdout
            .lines()
            .skip(1)
            .filter_map(|line| {
                let mut cols = line.split_whitespace();
                let serial = cols.next()?.to_owned();
                let state  = cols.next()?;
                (state == "device").then_some(serial)
            })
            .collect();

        for serial in current.difference(&known) {
            info!("Dispositivo USB detectado ({serial}) — activando túnel ADB…");
            let ok = Command::new("adb")
                .args(["-s", serial, "reverse", &format!("tcp:{TABLET_PORT}"), &format!("tcp:{TABLET_PORT}")])
                .status()
                .await
                .map(|s| s.success())
                .unwrap_or(false);

            if ok {
                info!("Túnel ADB listo — la app Android puede conectarse ahora.");
            } else {
                warn!("No se pudo configurar el túnel ADB para {serial}.");
            }
        }

        known = current;
    }
}
