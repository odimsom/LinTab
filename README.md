# LinTab 🎨🐧

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform: Linux](https://img.shields.io/badge/Platform-Linux-orange.svg)](https://www.kernel.org)
[![Subsystem: uinput](https://img.shields.io/badge/Subsystem-uinput-blueviolet.svg)]()
[![Tech Stack: Rust & Kotlin](https://img.shields.io/badge/Stack-Rust%20%7C%20Kotlin%20%7C%20Tauri-brightgreen.svg)]()

**LinTab** es una solución open-source y completamente nativa para Linux que transforma cualquier dispositivo Android en una tableta digitalizadora de gráficos de ultra baja latencia (Zero-Lag), emulando hardware avanzado — presión e inclinación (*tilt*) — directamente sobre el subsistema de entrada del Kernel (`uinput`).

## 📄 Licencia y Autoría

Propiedad intelectual de **Francisco Daniel Castro Borrome** (Copyright © 2026).
Distribuido bajo la licencia **GPL-3.0**. Todos los archivos fuente incluyen el aviso de copyright en su cabecera. Consulta `LICENSE` para más información.

---

## 🚀 Características Principales

- **Zero-Lag Pipeline** — Comunicación primaria por USB vía ADB Port Forwarding; fallback automático a UDP sobre Wi-Fi.
- **Driver Virtual Nativo** — El sistema operativo reconoce el teléfono como una tableta digitalizadora real (no un mouse). Compatible con GIMP, Krita, Inkscape y Blender.
- **Captura Avanzada de Hardware** — Coordenadas absolutas, presión de 8192 niveles e inclinación en X/Y (S-Pen y stylus activos).
- **Arquitectura Dual (Daemon + CLI)** — Un único binario que opera como servicio de fondo o como herramienta de línea de comandos.
- **IPC por Unix Socket** — El daemon expone un canal local (`$XDG_RUNTIME_DIR/lintab.sock`) que consume tanto la GUI de Tauri como la CLI.

---

## 📂 Estructura del Monorepo

```
LinTab/
├── core-daemon/          # Servicio Rust: uinput + CLI + IPC server
├── linux-gui/            # Panel de control Tauri (Rust + TypeScript)
├── android-client/       # App Kotlin: captura de MotionEvent / stylus
└── shared-protocol/      # events.proto — serialización binaria compartida
```

### `core-daemon/` — módulos internos

| Módulo | Responsabilidad |
|---|---|
| `main.rs` | Punto de entrada, parser CLI (`clap`) y despacho de modos |
| `daemon.rs` | Modo background: crea el dispositivo uinput, lanza el transport loop y sirve el socket IPC |
| `ipc.rs` | Tipos `IpcCommand` / `IpcResponse`, `socket_path()`, helper `send_ipc_command()` |
| `transport/` | `serve_loop()` para el daemon · `serve()` para CLI one-shot (TCP/ADB) |
| `uinput/` | `TabletDevice`: registro y emisión de eventos en `/dev/uinput` |
| `scan/mdns.rs` | Descubrimiento de clientes Android vía mDNS/ZeroConf (`_lintab._tcp.local.`) |
| `scan/adb.rs` | Listado de dispositivos USB conectados mediante `adb devices` |

---

## 🖥️ CLI — Uso del Binario

El binario `lintab` opera en dos modos según los argumentos que reciba.

### Modo Daemon (por defecto)

```bash
lintab
```

Inicia el daemon en segundo plano. Crea el dispositivo virtual uinput, escucha conexiones entrantes del cliente Android y expone el socket IPC para que la GUI y la CLI puedan enviarle instrucciones.

El socket se crea en `$XDG_RUNTIME_DIR/lintab.sock` (ej. `/run/user/1000/lintab.sock`).

### Modo CLI — Comandos one-shot

Cada comando intenta comunicarse con el daemon en ejecución vía IPC. Si el daemon no está corriendo, ejecuta la acción directamente en el mismo proceso y termina.

```bash
# Descubrir dispositivos en la red local y por USB
lintab scan

# Conectar a un dispositivo por IP directa
lintab connect --ip 192.168.1.42

# Conectar a un dispositivo por nombre mDNS
lintab connect --dname pixel-7.local
```

### Protocolo IPC

La comunicación entre clientes (GUI / CLI) y el daemon usa **NDJSON** sobre el Unix socket — un objeto JSON por línea:

```jsonc
// Solicitud
{"cmd":"scan"}
{"cmd":"connect","ip":"192.168.1.42"}
{"cmd":"status"}

// Respuesta
{"ok":true,"data":{"mdns":[...],"adb":[...]}}
{"ok":false,"error":"Daemon is not running"}
```

Comandos disponibles: `status` · `scan` · `connect` · `disconnect` · `set_mapping`

---

## 🛠️ Requisitos de Inicialización

Para que el daemon pueda crear dispositivos virtuales sin `sudo`, configura `udev`:

```bash
sudo usermod -aG input $USER
echo 'KERNEL=="uinput", GROUP="input", MODE="0660"' | sudo tee /etc/udev/rules.d/99-uinput.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
```

> Cierra sesión y vuelve a entrar para que el cambio de grupo surta efecto.

---

## 🏷️ Tags

`linux-driver` `uinput` `drawing-tablet` `rust` `kotlin` `tauri` `android-stylus` `low-latency` `krita-hardware` `pop-os`
