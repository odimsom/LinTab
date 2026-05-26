// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

use evdev::{
    uinput::{VirtualDevice, VirtualDeviceBuilder},
    AbsInfo, AbsoluteAxisType, AttributeSet, EventType, InputEvent, Key, UinputAbsSetup,
};
use std::io::ErrorKind;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum UinputError {
    #[error(
        "\n\
         ╔══════════════════════════════════════════════════════════════════╗\n\
         ║  LinTab: Permiso denegado en /dev/uinput                        ║\n\
         ╚══════════════════════════════════════════════════════════════════╝\n\
         \n\
         Necesitas ejecutar estos comandos UNA SOLA VEZ:\n\
         \n\
         \x20  sudo usermod -aG input $USER\n\
         \x20  echo 'KERNEL==\"uinput\", GROUP=\"input\", MODE=\"0660\"' | \\\n\
         \x20      sudo tee /etc/udev/rules.d/99-lintab.rules\n\
         \x20  sudo udevadm control --reload-rules && sudo udevadm trigger\n\
         \n\
         O ejecuta:  lintab setup --auto\n\
         \n\
         Luego cierra sesión y vuelve a entrar. LinTab NUNCA requiere sudo."
    )]
    PermissionDenied,

    #[error("Error al crear el dispositivo uinput: {0}")]
    Create(std::io::Error),

    #[error("Error al emitir evento al kernel: {0}")]
    Emit(std::io::Error),
}

const AXIS_MAX: i32 = 32767;

pub struct TabletDevice {
    device: VirtualDevice,
}

impl TabletDevice {
    pub fn new() -> Result<Self, UinputError> {
        let builder = VirtualDeviceBuilder::new().map_err(|e| {
            if e.kind() == ErrorKind::PermissionDenied {
                UinputError::PermissionDenied
            } else {
                UinputError::Create(e)
            }
        })?;

        let mut keys = AttributeSet::<Key>::new();
        keys.insert(Key::BTN_TOOL_PEN);
        keys.insert(Key::BTN_TOUCH);
        keys.insert(Key::BTN_STYLUS);
        keys.insert(Key::BTN_STYLUS2);

        let device = builder
            .name("LinTab Virtual Tablet")
            .with_absolute_axis(&UinputAbsSetup::new(
                AbsoluteAxisType::ABS_X,
                AbsInfo::new(0, 0, AXIS_MAX, 0, 0, 100),
            ))
            .map_err(UinputError::Create)?
            .with_absolute_axis(&UinputAbsSetup::new(
                AbsoluteAxisType::ABS_Y,
                AbsInfo::new(0, 0, AXIS_MAX, 0, 0, 100),
            ))
            .map_err(UinputError::Create)?
            .with_absolute_axis(&UinputAbsSetup::new(
                AbsoluteAxisType::ABS_PRESSURE,
                AbsInfo::new(0, 0, 8191, 0, 0, 0),
            ))
            .map_err(UinputError::Create)?
            .with_absolute_axis(&UinputAbsSetup::new(
                AbsoluteAxisType::ABS_TILT_X,
                AbsInfo::new(0, -90, 90, 0, 0, 1),
            ))
            .map_err(UinputError::Create)?
            .with_absolute_axis(&UinputAbsSetup::new(
                AbsoluteAxisType::ABS_TILT_Y,
                AbsInfo::new(0, -90, 90, 0, 0, 1),
            ))
            .map_err(UinputError::Create)?
            .with_keys(&keys)
            .map_err(UinputError::Create)?
            .build()
            .map_err(UinputError::Create)?;

        Ok(Self { device })
    }

    /// Emit one stylus sample to the kernel.
    ///
    /// X/Y are in uinput abstract space 0–32767.
    /// The coordinate mapping applied upstream:
    ///   X_linux = (X_android / W_android) * 32767
    pub fn emit_pen_event(
        &mut self,
        x: i32,
        y: i32,
        pressure: i32,
        tilt_x: i32,
        tilt_y: i32,
    ) -> Result<(), UinputError> {
        let touching = pressure > 0;
        let events = [
            InputEvent::new(EventType::KEY,      Key::BTN_TOOL_PEN.0, 1),
            InputEvent::new(EventType::KEY,      Key::BTN_TOUCH.0,    touching as i32),
            InputEvent::new(EventType::ABSOLUTE, AbsoluteAxisType::ABS_X.0,        x),
            InputEvent::new(EventType::ABSOLUTE, AbsoluteAxisType::ABS_Y.0,        y),
            InputEvent::new(EventType::ABSOLUTE, AbsoluteAxisType::ABS_PRESSURE.0, pressure),
            InputEvent::new(EventType::ABSOLUTE, AbsoluteAxisType::ABS_TILT_X.0,   tilt_x),
            InputEvent::new(EventType::ABSOLUTE, AbsoluteAxisType::ABS_TILT_Y.0,   tilt_y),
            InputEvent::new(EventType::SYNCHRONIZATION, 0, 0),
        ];
        self.device.emit(&events).map_err(UinputError::Emit)
    }

    /// Signal pen lift (all buttons released, pressure 0).
    pub fn emit_pen_up(&mut self) -> Result<(), UinputError> {
        let events = [
            InputEvent::new(EventType::KEY,      Key::BTN_TOUCH.0,    0),
            InputEvent::new(EventType::KEY,      Key::BTN_TOOL_PEN.0, 0),
            InputEvent::new(EventType::ABSOLUTE, AbsoluteAxisType::ABS_PRESSURE.0, 0),
            InputEvent::new(EventType::SYNCHRONIZATION, 0, 0),
        ];
        self.device.emit(&events).map_err(UinputError::Emit)
    }
}
