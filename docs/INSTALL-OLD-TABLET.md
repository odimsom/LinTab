# LinTab — Tablets antiguas y hardware básico

LinTab tiene un **pipeline de compensación adaptativo** diseñado específicamente para tablets antiguas, capacitivas o de gama baja. Esta guía explica cómo sacarle el máximo partido a hardware limitado.

## Diagnóstico previo: ¿qué tipo de hardware tienes?

| Característica | Premium (S-Pen, Wacom) | Intermedio (capacitivo moderno) | Básico (antiguo) |
|---|---|---|---|
| Tipo de stylus | Activo EMR/AES | Capacitivo pasivo | Solo dedo |
| Hover sin tocar | Sí | No | No |
| Presión | 4096–8192 niveles | Simulada (0/1) | Ninguna |
| Frecuencia | 120–240 Hz | 60–90 Hz | 30–60 Hz |
| Jitter típico | < 1 px | 1–4 px | 4–10 px |

## Primer inicio: calibración automática

Al abrir LinTab por primera vez, selecciona **"04. AUTO DETECTAR HARDWARE"**. La app realizará un benchmark de 5 segundos:

1. Aparece un área de dibujo en negro con borde naranja.
2. Dibuja trazos libres durante 5 segundos (líneas, curvas, puntos).
3. LinTab mide: jitter, frecuencia de muestreo y latencia.
4. Aplica automáticamente la configuración óptima.

### ¿Qué configura el benchmark?

| Parámetro | Básico | Intermedio | Premium |
|---|---|---|---|
| Delayed commit | 12 ms | 6 ms | 0 ms |
| Smoothing | 0.48 (fuerte) | 0.28 (moderado) | 0.08 (suave) |
| Predicción | 14 ms | 10 ms | 6 ms |
| Touch slop | 14 px | 8 px | 3 px |
| Fake hover | Activo | Según hover real | Desactivado |

## Técnicas de compensación activas en hardware básico

### 1. Delayed Commit (12 ms)
LinTab **no envía el punto inmediatamente** al tocar la pantalla. Espera 12 ms (≈1 frame) para acumular más muestras y estabilizar la trayectoria inicial, eliminando los saltos bruscos al inicio de cada trazo.

### 2. Smoothing exponencial (α = 0.48)
Cada coordenada pasa por un filtro:
```
x_suavizado = x_suavizado + (x_nuevo - x_suavizado) × 0.52
```
Elimina el jitter (temblor de coordenadas) sin introducir lag visible.

### 3. Predicción lineal (14 ms)
LinTab extrapola la posición futura usando la velocidad actual, compensando la latencia de red/USB. El cursor "anticipa" el movimiento del stylus.

### 4. Touch Slop inteligente (14 px)
Un toque que no supere los 14 px de desplazamiento se trata como **tap** (clic), no como trazo. Evita micro-líneas accidentales al tocar.

### 5. Fake Hover
Mantiene el cursor visible entre trazos interpolando la última posición conocida, simulando el hover del stylus activo aunque el hardware no lo soporte.

## Recomendaciones de configuración

### Modo óptimo para tablets antiguas

En la pantalla inicial, selecciona según tu uso:

| Uso | Modo recomendado |
|---|---|
| Dibujo / ilustración | `AUTO DETECTAR` → modo Absoluto |
| Apuntes / escritura | `02. MODO APUNTES` |
| Control remoto / cursor | `03. MODO TOUCHPAD` |

### Ajustes adicionales en la GUI Linux

1. Abre la GUI (`linux-gui`).
2. Selecciona **Modo Relativo** si el cursor salta o pierde posición.
3. Usa **rotación 0°** para tablets en portrait natural.

## Solución de problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| Líneas con temblor | Jitter de hardware | Aumenta smoothing → selecciona modo AUTO |
| Punto inicial incorrecto | Sin delayed commit | Reinicia y usa calibración AUTO |
| Cursor salta entre trazos | Sin fake hover | Activa modo AUTO (tier BASIC activa fake hover) |
| La app no responde rápido | Frecuencia baja (30 Hz) | Usa predicción 14 ms (automática en tier BASIC) |
| Presión siempre al máximo | Hardware sin presión real | Normal — la presión se simula en 0/1 |

## Tablets probadas (hardware básico)

- Samsung Galaxy Tab A (2016–2019) — Tier: BASIC
- Lenovo Tab M8 — Tier: BASIC/INTERMEDIATE  
- Amazon Fire HD — Tier: BASIC
- Huawei MediaPad T5 — Tier: INTERMEDIATE
- Xiaomi Pad 5 (sin S-Pen) — Tier: INTERMEDIATE

## ¿Mi tablet es demasiado antigua?

LinTab funciona con cualquier Android 8.0+ que pueda ejecutar la app. El mínimo recomendado para dibujo usable:
- Frecuencia de touch ≥ 30 Hz
- RAM ≥ 2 GB
- WiFi o puerto USB con soporte ADB

Si la experiencia es aún demasiado imprecisa incluso con compensación máxima, considera usar **Modo Touchpad** (relativo) para navegación general en lugar de dibujo.
