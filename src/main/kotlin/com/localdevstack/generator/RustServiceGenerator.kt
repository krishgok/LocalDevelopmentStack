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
        Files.writeString(routesDir.resolve("hello.rs"), helloRouteRs())
        Files.writeString(servicesDir.resolve("mod.rs"), servicesModRs())
        Files.writeString(servicesDir.resolve("hello.rs"), helloServiceRs())

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
        use routes::hello::hello_handler;

        #[tokio::main]
        async fn main() {
            let app = Router::new()
                .route("/api/hello", get(hello_handler));

            let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await.unwrap();
            println!("Server running on :8080");
            axum::serve(listener, app).await.unwrap();
        }
    """.trimIndent()

    private fun routesModRs() = """
        pub mod hello;
    """.trimIndent()

    private fun helloRouteRs() = """
        use axum::Json;
        use serde_json::{json, Value};
        use crate::services::hello::HelloService;

        pub async fn hello_handler() -> Json<Value> {
            let service = HelloService::new();
            Json(json!({ "message": service.get_greeting() }))
        }
    """.trimIndent()

    private fun servicesModRs() = """
        pub mod hello;
    """.trimIndent()

    private fun helloServiceRs() = """
        pub struct HelloService;

        impl HelloService {
            pub fn new() -> Self {
                HelloService
            }

            pub fn get_greeting(&self) -> &str {
                "Hello, World!"
            }
        }
    """.trimIndent()
}
