package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class GoDockerfileGenerator : DockerfileGenerator {

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
        FROM golang:1.22-alpine AS builder
        WORKDIR /app
        COPY go.mod go.sum* ./
        RUN go mod download
        COPY . .
        RUN go build -o service ./...

        # ── Runtime stage ────────────────────────────────────────────────────────────
        FROM alpine:3.19
        RUN apk --no-cache add ca-certificates
        WORKDIR /app
        COPY --from=builder /app/service .
        EXPOSE 8080
        CMD ["./service"]
    """.trimIndent()
}
