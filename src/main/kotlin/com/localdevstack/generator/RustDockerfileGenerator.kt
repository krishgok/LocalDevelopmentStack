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
        if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
        }
        return serviceDir.resolve("Dockerfile.dev")
    }

    private fun dockerfile() = """
        FROM rust:1.75-slim
        RUN cargo install cargo-watch
        WORKDIR /app
        COPY Cargo.toml Cargo.lock* ./
        RUN mkdir src && echo 'fn main() {}' > src/main.rs && cargo build && rm -rf src
        EXPOSE 8080
        CMD ["cargo", "watch", "-x", "run"]
    """.trimIndent()
}
