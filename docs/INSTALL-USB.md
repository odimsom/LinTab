# LinTab — Instalación por USB (ADB)

La conexión USB es el modo recomendado: **latencia mínima** (~2–5 ms extra sobre USB) y sin dependencia de red.

## Cómo funciona

El daemon ejecuta `adb reverse tcp:7654 tcp:7654` automáticamente al detectar el dispositivo. La app Android se conecta a `127.0.0.1:7654` — el tráfico va por USB sin pasar por la red.

## Requisitos

- Cable USB con transferencia de datos
- ADB instalado en Linux
- Depuración USB activada en el Android

## Paso a paso

### 1 — Instalar ADB en Linux

```bash
# Arch / Manjaro
sudo pacman -S android-tools

# Debian / Ubuntu
sudo apt install adb

# Fedora
sudo dnf install android-tools
```

### 2 — Activar depuración USB en Android

`Ajustes → Acerca del teléfono → Número de compilación` (tocar 7 veces) → `Ajustes → Opciones de desarrollador → Depuración USB`.

### 3 — Instalar el daemon Linux

```bash
tar -xzf lintab-v*.tar.gz
sudo install -m 755 lintab /usr/local/bin/lintab
lintab setup --auto
# Cierra sesión y vuelve a entrar
```

### 4 — Instalar la app Android

```bash
# Conecta el teléfono y acepta el aviso de depuración USB
adb install lintab-v*.apk
```

### 5 — Iniciar

```bash
# Terminal 1: iniciar el daemon
lintab

# El daemon detecta el teléfono y ejecuta adb reverse automáticamente.
# Abre LinTab en Android — la conexión debe establecerse en segundos.
```

## Verificar conexión

```bash
lintab scan
# Debe mostrar el dispositivo en la sección ADB
```

## Solución de problemas

| Síntoma | Solución |
|---|---|
| `adb: command not found` | Instala android-tools (ver paso 1) |
| El teléfono no aparece en `adb devices` | Acepta el aviso de depuración en el Android |
| La app no se conecta | Verifica que el daemon esté corriendo (`lintab scan`) |
| Error de permisos en `/dev/uinput` | Ejecuta `lintab setup --auto` y cierra sesión |

## Rendimiento esperado

- **Latencia total**: 3–8 ms (USB 2.0) / 1–3 ms (USB 3.0)
- **Frecuencia**: hasta 240 Hz con stylus activo (S-Pen, Wacom)
- **Presión**: 8192 niveles
