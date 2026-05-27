# LinTab — Instalación

## Instalar desde un gestor de paquetes

| Distro | Método | Comando |
|---|---|---|
| Arch / Manjaro | **AUR** | `yay -S lintab` |
| Ubuntu / Debian | **.deb** (GitHub Release) | `sudo dpkg -i lintab_*.deb` |
| Fedora / RHEL | **Copr** | `sudo dnf copr enable odimsom/lintab && sudo dnf install lintab` |
| Cualquier distro | **AppImage** | Descarga `LinTab_*.AppImage`, hazlo ejecutable y ábrelo |
| Ubuntu 20.04+ | **Snap** | `sudo snap install lintab` |
| Cualquier distro | **Flatpak** | `flatpak install flathub com.lintab.LinTab` |

---

LinTab convierte cualquier dispositivo Android en una tableta gráfica de ultra baja latencia para Linux. La instalación implica tres pasos: preparar Linux, instalar la app Android y conectar los dispositivos.

## Requisitos mínimos

| Componente | Requisito |
|---|---|
| Linux | Kernel 5.10+, acceso a `/dev/uinput` |
| Android | 8.0+ (API 26) |
| Conexión | USB con ADB o WiFi en la misma red |

## Guías de instalación

Elige el método según tu caso concreto:

| Escenario | Guía |
|---|---|
| Conexión por **USB** (recomendado, menor latencia) | [INSTALL-USB.md](docs/INSTALL-USB.md) |
| Conexión por **WiFi** (inalámbrico) | [INSTALL-WIFI.md](docs/INSTALL-WIFI.md) |
| Tablet **antigua o de gama baja** (capacitiva) | [INSTALL-OLD-TABLET.md](docs/INSTALL-OLD-TABLET.md) |

## Instalación rápida

### 1 — Daemon Linux

Descarga el binario de la [última release](https://github.com/odimsom/LinTab/releases/latest):

```bash
# Extrae el binario
tar -xzf lintab-v*.tar.gz
sudo install -m 755 lintab /usr/local/bin/lintab

# Configura permisos udev (una sola vez)
lintab setup --auto

# Cierra sesión y vuelve a entrar, luego inicia el daemon
lintab
```

### 2 — App Android

Descarga el APK desde la [última release](https://github.com/odimsom/LinTab/releases/latest) e instálalo en tu dispositivo:

```bash
# Vía ADB si el dispositivo está conectado por USB
adb install lintab-v*.apk
```

### 3 — Conectar

Abre LinTab en Android. El daemon lo detectará automáticamente por USB o WiFi.

## Verificar actualizaciones

```bash
lintab check-update
```

## Desinstalar

```bash
sudo rm /usr/local/bin/lintab
sudo rm /etc/udev/rules.d/99-lintab.rules
sudo udevadm control --reload-rules
```
