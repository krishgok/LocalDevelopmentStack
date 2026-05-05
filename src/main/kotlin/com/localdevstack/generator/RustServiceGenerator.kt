package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class RustServiceGenerator : ServiceGenerator {

    override val runCommand = "cargo run"

    override fun generate(outputDir: Path, projectName: String) {
        val serviceDir = outputDir.resolve("service")
        val srcDir = serviceDir.resolve("src")
        val routesDir = srcDir.resolve("routes")
        val servicesDir = srcDir.resolve("services")

        listOf(serviceDir, srcDir, routesDir, servicesDir).forEach {
            Files.createDirectories(it)
        }

        Files.writeString(serviceDir.resolve("Cargo.toml"), cargoToml(projectName))
        Files.writeString(srcDir.resolve("main.rs"), mainRs())
        Files.writeString(routesDir.resolve("mod.rs"), routesModRs())
        Files.writeString(routesDir.resolve("health.rs"), healthRouteRs())
        Files.writeString(servicesDir.resolve("mod.rs"), servicesModRs())
        Files.writeString(servicesDir.resolve("health.rs"), healthServiceRs())

        println("  [OK] Rust service         ->  $serviceDir")
    }

    private fun cargoToml(projectName: String) = """
        [package]
        name = "$projectName"
        version = "0.1.0"
        edition = "2021"

        [dependencies]
        axum = "0.7"
        tokio = { version = "1", features = ["full"] }
        serde = { version = "1", features = ["derive"] }
        serde_json = "1"
    """.trimIndent()

    private fun mainRs() = """
        mod routes;
        mod services;

        use axum::{Router, routing::get};
        use routes::health::health_handler;

        #[tokio::main]
        async fn main() {
            let app = Router::new()
                .route("/health", get(health_handler));

            let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await.unwrap();
            println!("Server running on :8080");
            axum::serve(listener, app).await.unwrap();
        }
    """.trimIndent()

    private fun routesModRs() = """
        pub mod health;
    """.trimIndent()

    private fun healthRouteRs() = """
        use axum::Json;
        use serde_json::{json, Value};

        pub async fn health_handler() -> Json<Value> {
            Json(json!({ "status": "ok" }))
        }
    """.trimIndent()

    private fun servicesModRs() = """
        pub mod health;
    """.trimIndent()

    private fun healthServiceRs() = """
        pub struct HealthService;

        impl HealthService {
            pub fn new() -> Self {
                HealthService
            }

            pub fn status(&self) -> &str {
                "ok"
            }
        }
    """.trimIndent()
}
