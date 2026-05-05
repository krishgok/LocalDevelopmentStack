package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class NodeDockerfileGenerator : DockerfileGenerator {

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
        FROM node:18-alpine
        WORKDIR /app
        COPY package*.json ./
        RUN npm ci --omit=dev
        COPY . .
        EXPOSE 8080
        CMD ["node", "index.js"]
    """.trimIndent()
}
