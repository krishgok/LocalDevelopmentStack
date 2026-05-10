package com.localdevstack.generator

class RustDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM rust:1.75-slim
        RUN cargo install cargo-watch
        WORKDIR /app
        COPY Cargo.toml Cargo.lock* ./
        RUN mkdir src && echo 'fn main() {}' > src/main.rs && cargo build && rm -rf src
        EXPOSE 8080
        CMD ["cargo", "watch", "-x", "run"]
    """.trimIndent()
}
