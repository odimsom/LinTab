// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

use anyhow::Result;
use mdns_sd::{ServiceDaemon, ServiceEvent, ServiceInfo};
use serde::Serialize;
use std::time::Duration;

pub const SERVICE_TYPE: &str = "_lintab._tcp.local.";
const BROWSE_TIMEOUT: Duration = Duration::from_secs(4);

#[derive(Serialize)]
pub struct MdnsDevice {
    pub name: String,
    pub addr: String,
    pub service: String,
}

// ── Advertise (daemon → Android discovers) ────────────────────────────────────

/// Registers this daemon on the local network via mDNS/ZeroConf.
/// The Android client browses for `_lintab._tcp` and connects automatically.
pub fn advertise(mdns: &ServiceDaemon, port: u16) -> Result<()> {
    let hostname_os = gethostname::gethostname();
    let hostname    = hostname_os.to_string_lossy();
    let local_ip    = local_ip_address::local_ip()
        .unwrap_or(std::net::IpAddr::V4(std::net::Ipv4Addr::LOCALHOST));

    let service = ServiceInfo::new(
        SERVICE_TYPE,
        "lintab-daemon",
        &format!("{hostname}.local."),
        local_ip,
        port,
        None,
    )?;

    mdns.register(service)?;
    Ok(())
}

// ── Discover (CLI scan → browse for Android clients advertising themselves) ───

/// Browse the local network for any LinTab-compatible devices.
/// Used by `lintab scan` to list what's available.
pub async fn discover() -> Result<Vec<MdnsDevice>> {
    let mdns     = ServiceDaemon::new()?;
    let receiver = mdns.browse(SERVICE_TYPE)?;

    let mut devices = Vec::new();
    let deadline    = tokio::time::Instant::now() + BROWSE_TIMEOUT;

    loop {
        let remaining = deadline.saturating_duration_since(tokio::time::Instant::now());
        if remaining.is_zero() { break; }

        let recv_result = tokio::time::timeout(remaining, async {
            let rx = receiver.clone();
            tokio::task::spawn_blocking(move || rx.recv_timeout(Duration::from_millis(200))).await
        })
        .await;

        if let Ok(Ok(Ok(ServiceEvent::ServiceResolved(info)))) = recv_result {
            let addr = info.get_addresses().iter().next()
                .map(|a| a.to_string())
                .unwrap_or_default();
            devices.push(MdnsDevice {
                name:    info.get_fullname().to_owned(),
                addr,
                service: info.get_type().to_owned(),
            });
        }
    }

    mdns.shutdown()?;
    Ok(devices)
}

/// Resolve a single mDNS hostname to its IP address string.
pub async fn resolve(hostname: &str) -> Result<String> {
    discover().await?
        .into_iter()
        .find(|d| d.name.starts_with(hostname))
        .map(|d| d.addr)
        .ok_or_else(|| anyhow::anyhow!("Dispositivo '{hostname}' no encontrado en la red local"))
}
