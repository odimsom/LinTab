// Copyright (c) 2026 Francisco Daniel Castro Borrome. Todos los derechos reservados.
// SPDX-License-Identifier: GPL-3.0-or-later

fn main() -> Result<(), Box<dyn std::error::Error>> {
    prost_build::Config::new()
        .compile_protos(
            &["../shared-protocol/events.proto"],
            &["../shared-protocol/"],
        )?;
    println!("cargo:rerun-if-changed=../shared-protocol/events.proto");
    Ok(())
}
