// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

mod daemon;
mod ipc;
mod proto;
mod scan;
mod setup;
mod transport;
mod uinput;
mod updater;

use anyhow::{bail, Result};
use clap::{Parser, Subcommand};
use tracing::info;

// ── CLI ───────────────────────────────────────────────────────────────────────

#[derive(Parser)]
#[command(
    name    = "lintab",
    about   = "LinTab — Tableta gráfica de ultra baja latencia para Linux",
    version
)]
struct Cli {
    #[command(subcommand)]
    command: Option<Commands>,
}

#[derive(Subcommand)]
enum Commands {
    /// Conectar a un dispositivo específico
    Connect {
        /// Nombre mDNS del dispositivo (ej. "pixel-7.local")
        #[arg(long, conflicts_with = "ip", required_unless_present = "ip")]
        dname: Option<String>,
        /// Dirección IP directa (ej. "192.168.1.42")
        #[arg(long, conflicts_with = "dname", required_unless_present = "dname")]
        ip: Option<String>,
    },

    /// Buscar dispositivos disponibles en la red y por USB
    Scan,

    /// Configurar permisos del sistema (ejecutar una sola vez)
    Setup {
        /// Aplicar automáticamente con pkexec o sudo
        #[arg(long)]
        auto: bool,
    },

    /// Verificar si hay una nueva versión disponible en GitHub
    CheckUpdate,
}

// ── Entry point ───────────────────────────────────────────────────────────────

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(
            std::env::var("LINTAB_LOG").unwrap_or_else(|_| "lintab=info".into()),
        )
        .init();

    match Cli::parse().command {
        None                                       => daemon::run().await,
        Some(Commands::Scan)                       => cmd_scan().await,
        Some(Commands::Connect { dname, ip })      => cmd_connect(dname, ip).await,
        Some(Commands::Setup { auto: run_auto })   => cmd_setup(run_auto).await,
        Some(Commands::CheckUpdate)                => cmd_check_update().await,
    }
}

// ── Command implementations ───────────────────────────────────────────────────

async fn cmd_scan() -> Result<()> {
    // Try running daemon first; fall back to direct scan
    if let Ok(resp) = ipc::send_ipc_command(&ipc::IpcCommand::Scan).await {
        print_response(resp);
        return Ok(());
    }

    println!("Buscando dispositivos LinTab…\n");

    let (mdns_res, adb_res) =
        tokio::join!(scan::mdns::discover(), scan::adb::list_devices());

    println!("── Red local (mDNS/ZeroConf) ─────────────────");
    match mdns_res {
        Ok(ref v) if v.is_empty() => println!("  Sin dispositivos en la red."),
        Ok(v) => v.iter().for_each(|d| println!("  {} · {}", d.name, d.addr)),
        Err(e) => println!("  Error: {e}"),
    }

    println!();
    println!("── USB / ADB ──────────────────────────────────");
    match adb_res {
        Ok(ref v) if v.is_empty() => println!("  Sin dispositivos USB conectados."),
        Ok(v) => v.iter().for_each(|d| println!("  {} · {}", d.serial, d.state)),
        Err(e) => println!("  Error: {e}"),
    }

    Ok(())
}

async fn cmd_connect(dname: Option<String>, ip: Option<String>) -> Result<()> {
    let cmd = ipc::IpcCommand::Connect { dname: dname.clone(), ip: ip.clone() };

    if let Ok(resp) = ipc::send_ipc_command(&cmd).await {
        print_response(resp);
        return Ok(());
    }

    // No daemon running — one-shot mode
    let target = match (dname, ip) {
        (Some(name), _) => {
            info!("Resolviendo '{name}' vía mDNS…");
            scan::mdns::resolve(&name).await?
        }
        (_, Some(raw)) => raw
            .parse::<std::net::IpAddr>()
            .map(|a| a.to_string())
            .map_err(|_| anyhow::anyhow!("'{raw}' no es una dirección IP válida"))?,
        _ => bail!("Usa --dname o --ip"),
    };

    if !setup::uinput_accessible() {
        setup::print_setup_guide();
        bail!("Permisos insuficientes.");
    }

    info!("Conectando a {target}…");
    let device = uinput::TabletDevice::new()?;
    transport::serve(device, Some(target)).await
}

async fn cmd_setup(run_auto: bool) -> Result<()> {
    println!("LinTab — Verificación del sistema\n");

    let uinput_ok  = setup::uinput_accessible();
    let udev_ok    = setup::udev_rule_exists();
    let adb_ok     = setup::adb_available().await;

    println!("  /dev/uinput accesible : {}", if uinput_ok { "✓" } else { "✗" });
    println!("  Regla udev instalada  : {}", if udev_ok   { "✓" } else { "✗" });
    println!("  adb disponible        : {}", if adb_ok    { "✓" } else { "— (opcional, para USB)" });
    println!();

    if uinput_ok && udev_ok {
        println!("✓ Todo en orden. Ejecuta `lintab` para iniciar.");
        return Ok(());
    }

    if run_auto {
        setup::run_auto_setup().await?;
    } else {
        setup::print_setup_guide();
        println!("Tip: ejecuta `lintab setup --auto` para configurar automáticamente.");
    }

    Ok(())
}

async fn cmd_check_update() -> Result<()> {
    // Try running daemon first (avoid duplicate network requests)
    if let Ok(resp) = ipc::send_ipc_command(&ipc::IpcCommand::CheckUpdate).await {
        print_response(resp);
        return Ok(());
    }

    println!("Verificando actualizaciones en GitHub…");
    let info = updater::check().await?;

    if info.update_available {
        println!("✓ Nueva versión disponible: v{}", info.latest);
        println!("  Actual : v{}", info.current);
        println!("  Descarga: {}", info.url);
        if let Some(notes) = &info.changelog {
            let preview: String = notes.lines().take(5).collect::<Vec<_>>().join("\n  ");
            println!("  Cambios:\n  {preview}");
        }
    } else {
        println!("✓ LinTab v{} está al día.", info.current);
    }
    Ok(())
}

fn print_response(resp: ipc::IpcResponse) {
    if resp.ok {
        if let Some(data) = resp.data {
            println!("{}", serde_json::to_string_pretty(&data).unwrap_or_default());
        } else {
            println!("OK");
        }
    } else {
        eprintln!("Error: {}", resp.error.unwrap_or_else(|| "desconocido".into()));
    }
}
