package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class JavaDockerfileGenerator : DockerfileGenerator {

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
        FROM eclipse-temurin:21-jdk-alpine AS builder
        WORKDIR /app
        COPY pom.xml ./
        RUN mvn dependency:go-offline -B 2>/dev/null || true
        COPY src ./src
        RUN mvn package -DskipTests --no-transfer-progress

        # ── Runtime stage ────────────────────────────────────────────────────────────
        FROM eclipse-temurin:21-jre-alpine
        WORKDIR /app
        COPY --from=builder /app/target/*.jar app.jar
        EXPOSE 8080
        ENTRYPOINT ["java", "-jar", "app.jar"]
    """.trimIndent()
}
