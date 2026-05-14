package com.localdevstack.generator

class RustDockerfileGenerator : DockerfileGenerator() {
    override fun dockerfile() = """
        FROM rust:1.75-slim
        # Install pre-built cargo-watch binary. Compiling from source takes ~12 min
        # on a cold build; the pinned release tarball lands in seconds.
        RUN apt-get update && apt-get install -y --no-install-recommends curl xz-utils ca-certificates \
            && rm -rf /var/lib/apt/lists/* \
            && curl -sSL https://github.com/watchexec/cargo-watch/releases/download/v8.5.2/cargo-watch-v8.5.2-x86_64-unknown-linux-gnu.tar.xz \
               | tar -xJ -C /tmp \
            && mv /tmp/cargo-watch-v8.5.2-x86_64-unknown-linux-gnu/cargo-watch /usr/local/cargo/bin/ \
            && rm -rf /tmp/cargo-watch-v8.5.2-x86_64-unknown-linux-gnu
        WORKDIR /app
        COPY Cargo.toml Cargo.lock* ./
        RUN mkdir src && echo 'fn main() {}' > src/main.rs && cargo build && rm -rf src
        EXPOSE 8080
        CMD ["cargo", "watch", "-x", "run"]
    """.trimIndent()
}
