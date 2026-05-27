// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later
import { invoke } from "@tauri-apps/api/core";
// ── DOM refs ──────────────────────────────────────────────────────────────────
const statusEl = document.getElementById("status-value");
const socketEl = document.getElementById("socket-path");
const deviceList = document.getElementById("device-list");
const linkError = document.getElementById("link-error");
const consoleLog = document.getElementById("console-log");
const quadLink = document.getElementById("quad-link");
const btnScan = document.getElementById("btn-scan");
const btnRestart = document.getElementById("btn-restart");
const barPressure = document.getElementById("bar-pressure");
const valPressure = document.getElementById("val-pressure");
const barTiltX = document.getElementById("bar-tilt-x");
const valTiltX = document.getElementById("val-tilt-x");
const barTiltY = document.getElementById("bar-tilt-y");
const valTiltY = document.getElementById("val-tilt-y");
const btnRelative = document.getElementById("btn-relative");
const btnAbsolute = document.getElementById("btn-absolute");
// ── Shared mapping state ──────────────────────────────────────────────────────
let relativeMode = true;
// ── Console logger ────────────────────────────────────────────────────────────
function log(msg, level = "info") {
    const ts = new Date().toLocaleTimeString("es-AR", { hour12: false });
    const line = document.createElement("span");
    line.className = `log-line ${level === "info" ? "" : level}`;
    line.textContent = `[${ts}] ${msg}`;
    consoleLog.appendChild(line);
    consoleLog.scrollTop = consoleLog.scrollHeight;
}
// ── Daemon status ─────────────────────────────────────────────────────────────
async function refreshStatus() {
    try {
        const data = await invoke("get_daemon_status");
        statusEl.textContent = data.running ? "DAEMON ACTIVO" : "DAEMON INACTIVO";
        statusEl.classList.toggle("active", data.running);
        socketEl.textContent = "$XDG_RUNTIME_DIR/lintab.sock";
        log(`Daemon v${data.version} — activo`, "ok");
    }
    catch {
        statusEl.textContent = "SETUP REQUERIDO";
        statusEl.classList.remove("active");
        log("ERROR 04: SOCKET NO ENCONTRADO. REQUIERE CONFIGURACIÓN INICIAL.", "error");
        showSetupGuide();
    }
}
function showSetupGuide() {
    const setupMsg = document.createElement("div");
    setupMsg.id = "setup-guide";
    setupMsg.style.cssText = `
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0,0,0,0.9);
    z-index: 10000;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: 'JetBrains Mono', monospace;
    color: #fff;
    padding: 2rem;
  `;
    setupMsg.innerHTML = `
    <div style="
      background: #1a1a1a;
      border: 2px solid #FF4F00;
      border-radius: 8px;
      padding: 2rem;
      max-width: 700px;
      text-align: left;
      line-height: 1.6;
    ">
      <h2 style="color: #FF4F00; margin-top: 0; font-size: 1.5rem;">
        ⚠️ CONFIGURACIÓN INICIAL REQUERIDA
      </h2>
      
      <p style="color: #aaa; margin: 1rem 0;">
        LinTab necesita permiso para acceder a <code style="background:#0a0a0a;padding:0.2rem 0.4rem;">/dev/uinput</code>.
        Ejecuta una sola vez en tu terminal:
      </p>
      
      <div style="
        background: #0a0a0a;
        border: 1px solid #333;
        border-radius: 4px;
        padding: 1rem;
        margin: 1.5rem 0;
        overflow-x: auto;
      ">
        <code style="color: #4ec9b0;">lintab setup --auto</code>
      </div>
      
      <p style="color: #aaa; margin: 1rem 0;">
        O manualmente (para máyor control):
      </p>
      
      <div style="
        background: #0a0a0a;
        border: 1px solid #333;
        border-radius: 4px;
        padding: 1rem;
        margin: 1.5rem 0;
        font-size: 0.9rem;
        overflow-x: auto;
      ">
        <code style="color: #4ec9b0; display: block; margin-bottom: 0.5rem;">sudo usermod -aG input \\$USER</code>
        <code style="color: #4ec9b0; display: block; margin-bottom: 0.5rem;">echo 'KERNEL=="uinput", GROUP="input", MODE="0660"' | \\</code>
        <code style="color: #4ec9b0; display: block; margin-bottom: 0.5rem;">&nbsp;&nbsp;&nbsp;&nbsp;sudo tee /etc/udev/rules.d/99-lintab.rules</code>
        <code style="color: #4ec9b0; display: block;">sudo udevadm control --reload-rules && sudo udevadm trigger</code>
      </div>
      
      <p style="color: #ffb74d; margin: 1rem 0;">
        Después <strong>cierra sesión y vuelve a entrar</strong>.
      </p>
      
      <button id="close-setup" style="
        background: #FF4F00;
        color: white;
        border: none;
        padding: 0.8rem 1.5rem;
        border-radius: 4px;
        cursor: pointer;
        font-family: 'JetBrains Mono', monospace;
        font-weight: bold;
        margin-top: 1rem;
      ">ENTENDIDO</button>
    </div>
  `;
    if (!document.getElementById("setup-guide")) {
        document.body.appendChild(setupMsg);
        document.getElementById("close-setup")?.addEventListener("click", () => {
            setupMsg.remove();
        });
    }
}
// ── Scan devices ──────────────────────────────────────────────────────────────
async function handleScan() {
    btnScan.textContent = "ESCANEANDO...";
    btnScan.setAttribute("disabled", "true");
    deviceList.innerHTML = "";
    linkError.hidden = true;
    try {
        const result = await invoke("scan_devices");
        const all = [
            ...result.mdns.map(d => ({ label: d.name, addr: d.addr, type: "mDNS" })),
            ...result.adb.map(d => ({ label: d.serial, addr: d.serial, type: `ADB·${d.state}` })),
        ];
        if (all.length === 0) {
            log("Escaneo completado — sin dispositivos detectados.", "warn");
            linkError.textContent = "SIN DISPOSITIVOS. ABRE LA APP EN TU ANDROID.";
            linkError.hidden = false;
        }
        else {
            all.forEach(dev => {
                const item = document.createElement("div");
                item.className = "device-item";
                item.innerHTML = `
          <span>${dev.label}</span>
          <span class="dev-type">${dev.type}</span>
          <span class="dev-connect">CONECTAR_</span>`;
                item.addEventListener("click", () => connectDevice(dev.addr, dev.type));
                deviceList.appendChild(item);
            });
            log(`Escaneo completado — ${all.length} dispositivo(s) encontrado(s).`, "ok");
        }
    }
    catch (e) {
        log(`ERROR: ${e}`, "error");
        linkError.textContent = `ERROR: ${e}`;
        linkError.hidden = false;
    }
    finally {
        btnScan.textContent = "ESCANEAR DISPOSITIVOS_";
        btnScan.removeAttribute("disabled");
    }
}
// ── Connect ───────────────────────────────────────────────────────────────────
async function connectDevice(addr, type) {
    log(`Iniciando vínculo con ${addr} (${type})…`);
    try {
        const result = await invoke("connect_device", {
            ip: type.startsWith("ADB") ? "127.0.0.1" : addr,
            dname: undefined,
        });
        log(`VÍNCULO ESTABLECIDO — ${JSON.stringify(result)}`, "ok");
        // Flash orange on the Link block (design system feedback rule)
        quadLink.classList.remove("flash");
        void quadLink.offsetWidth; // reflow to restart animation
        quadLink.classList.add("flash");
    }
    catch (e) {
        log(`ERROR DE VÍNCULO: ${e}`, "error");
    }
}
// ── Mapping — monitor + rotation ─────────────────────────────────────────────
document.querySelectorAll(".monitor-item").forEach(el => {
    el.addEventListener("click", () => {
        document.querySelectorAll(".monitor-item").forEach(m => m.classList.remove("selected"));
        el.classList.add("selected");
    });
});
async function applyMapping(rotation) {
    await invoke("set_tablet_mapping", {
        screenX: 0, screenY: 0, screenWidth: 1920, screenHeight: 1080,
        rotation,
        relativeMode,
    });
}
document.querySelectorAll(".rot-btn").forEach(btn => {
    btn.addEventListener("click", async () => {
        document.querySelectorAll(".rot-btn").forEach(b => b.classList.remove("selected"));
        btn.classList.add("selected");
        const deg = Number(btn.dataset["deg"] ?? 0);
        try {
            await applyMapping(deg);
            log(`Mapping actualizado — rotación ${deg}°, modo ${relativeMode ? "relativo" : "absoluto"}`, "ok");
        }
        catch (e) {
            log(`Error al actualizar mapping: ${e}`, "error");
        }
    });
});
btnRelative.addEventListener("click", async () => {
    relativeMode = true;
    btnRelative.classList.add("selected");
    btnAbsolute.classList.remove("selected");
    const activeDeg = Number(document.querySelector(".rot-btn.selected")?.dataset["deg"] ?? 0);
    try {
        await applyMapping(activeDeg);
        log("Modo relativo activado — el cursor sigue el delta del stylus.", "ok");
    }
    catch (e) {
        log(`Error al cambiar modo: ${e}`, "error");
    }
});
btnAbsolute.addEventListener("click", async () => {
    relativeMode = false;
    btnAbsolute.classList.add("selected");
    btnRelative.classList.remove("selected");
    const activeDeg = Number(document.querySelector(".rot-btn.selected")?.dataset["deg"] ?? 0);
    try {
        await applyMapping(activeDeg);
        log("Modo absoluto activado — el cursor mapea la posición literal.", "ok");
    }
    catch (e) {
        log(`Error al cambiar modo: ${e}`, "error");
    }
});
// ── Restart daemon ────────────────────────────────────────────────────────────
btnRestart.addEventListener("click", async () => {
    log("Reiniciando daemon…", "warn");
    try {
        await invoke("disconnect_device");
    }
    catch { /* daemon may already be down */ }
    await refreshStatus();
});
// ── Precision polling ─────────────────────────────────────────────────────────
async function refreshPrecision() {
    try {
        const data = await invoke("get_precision");
        if (data) {
            updatePrecision(data.pressure, data.tilt_x, data.tilt_y);
        }
    }
    catch {
        // Daemon not running — ignore silently, bars stay at last value
    }
}
// ── Pressure / tilt ───────────────────────────────────────────────────────────
function updatePrecision(pressure, tiltX, tiltY) {
    const pPct = Math.round((pressure / 8191) * 100);
    barPressure.style.width = `${pPct}%`;
    valPressure.textContent = String(pressure).padStart(4, "0");
    const txPct = Math.round(((tiltX + 90) / 180) * 100);
    barTiltX.style.width = `${txPct}%`;
    valTiltX.textContent = `${tiltX >= 0 ? "+" : ""}${tiltX}°`;
    const tyPct = Math.round(((tiltY + 90) / 180) * 100);
    barTiltY.style.width = `${tyPct}%`;
    valTiltY.textContent = `${tiltY >= 0 ? "+" : ""}${tiltY}°`;
}
// ── Init ──────────────────────────────────────────────────────────────────────
btnScan.addEventListener("click", handleScan);
document.addEventListener("DOMContentLoaded", () => {
    log("LinTab GUI iniciando…");
    refreshStatus();
    setInterval(refreshPrecision, 150);
});
