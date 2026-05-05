package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class RustDockerfileGenerator : DockerfileGenerator {

    override fun generate(serviceDir: Path, projectName: String) {
        val target = dockerfilePath(serviceDir)
        Files.writeString(target, dockerfile())
        println("  [OK] Dockerfile.dev       ->  $target")
    }

    private fun dockerfilePath(serviceDir: Path): Path {
        val existing = serviceDir.resolve("Dockerfile")
        return if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
            serviceDir.resolve("Dockerfile.dev")
        } else {
            serviceDir.resolve("Dockerfile.dev")
        }
    }

    private fun dockerfile() = """
        # ── Build stage ─────────────────────────────────────────────────────────────
        FROM rust:1.75-slim AS builder
        WORKDIR /app
        COPY Cargo.toml Cargo.lock* ./
        RUN mkdir src && echo 'fn main(){}' > src/main.rs && cargo build --release && rm -rf src
        COPY . .
        RUN touch src/main.rs && cargo build --release

        # ── Runtime stage ────────────────────────────────────────────────────────────
        FROM debian:bookworm-slim
        RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
        WORKDIR /app
        COPY --from=builder /app/target/release/service .
        EXPOSE 8080
        CMD ["./service"]
    """.trimIndent()
}
