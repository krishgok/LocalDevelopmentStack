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
        if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
        }
        return serviceDir.resolve("Dockerfile.dev")
    }

    private fun dockerfile() = """
        FROM golang:1.22-alpine
        RUN go install github.com/air-verse/air@latest
        WORKDIR /app
        COPY go.mod go.sum* ./
        RUN go mod download
        EXPOSE 8080
        CMD ["air", "--build.cmd", "go build -o /tmp/main ./...", "--build.bin", "/tmp/main"]
    """.trimIndent()
}
