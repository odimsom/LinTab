// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

mod commands;

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            commands::get_daemon_status,
            commands::scan_devices,
            commands::connect_device,
            commands::disconnect_device,
            commands::set_tablet_mapping,
            commands::get_precision,
            commands::check_for_update,
        ])
        .run(tauri::generate_context!())
        .expect("error while running LinTab GUI");
}
