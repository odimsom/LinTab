# LinTab — Instalación por WiFi (mDNS)

La conexión WiFi permite usar la tableta de forma **inalámbrica**. La latencia es ligeramente mayor que USB (10–30 ms extra según el router), pero es perfectamente usable para dibujo y apuntes.

## Cómo funciona

El daemon se anuncia en la red local via mDNS/ZeroConf (`_lintab._tcp.local.`). La app Android lo descubre automáticamente sin necesidad de introducir la IP.

## Requisitos

- Linux y Android **en la misma red WiFi**
- Router con soporte multicast (la mayoría los tienen por defecto)

## Paso a paso

### 1 — Instalar el daemon Linux

```bash
tar -xzf lintab-v*.tar.gz
sudo install -m 755 lintab /usr/local/bin/lintab
lintab setup --auto
# Cierra sesión y vuelve a entrar
```

### 2 — Instalar la app Android

Descarga e instala el APK desde la [release](https://github.com/odimsom/LinTab/releases/latest).

### 3 — Iniciar

```bash
# Inicia el daemon en Linux
lintab

# Abre LinTab en Android
# → La app busca el daemon automáticamente
# → En 3–8 segundos, la conexión debe establecerse
```

### 4 — Permisos Android (primera vez)

Android requiere permiso de red para el descubrimiento mDNS. LinTab lo solicita al primer inicio — concede el permiso cuando aparezca el diálogo.

- **Android 13+**: permiso `NEARBY_WIFI_DEVICES`
- **Android 11-12**: permiso `ACCESS_FINE_LOCATION`

### Conexión manual por IP

Si el descubrimiento automático falla, conecta usando la IP del PC Linux:

```bash
# En Linux, obtén la IP
ip addr show | grep "inet " | grep -v 127.0.0.1

# Conectar desde el CLI del daemon
lintab connect --ip 192.168.1.42
```

## Solución de problemas

| Síntoma | Solución |
|---|---|
| La app no encuentra el daemon | Verifica que estén en la misma red WiFi |
| Descubrimiento muy lento (+15 s) | El router puede filtrar multicast — usa IP manual |
| Alta latencia (>50 ms) | Acércate al router o usa USB |
| Android 12 sin descubrimiento | Concede el permiso de localización temporalmente |

## Optimizar latencia WiFi

1. Conecta tanto Linux como Android a **5 GHz** (menor congestión).
2. Usa **WiFi 6 (802.11ax)** si tu router lo soporta.
3. Desactiva la gestión de energía WiFi en Linux:
   ```bash
   sudo iw dev wlan0 set power_save off
   ```

## Rendimiento esperado

- **Latencia total**: 15–40 ms (WiFi 5) / 8–20 ms (WiFi 6)
- **Frecuencia efectiva**: hasta 120 Hz (limitado por la red)
- **Presión**: 8192 niveles (sin pérdida, el dato va en el payload)
