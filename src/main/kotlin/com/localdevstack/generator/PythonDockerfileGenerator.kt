package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class PythonDockerfileGenerator : DockerfileGenerator {

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
        FROM python:3.10-slim
        WORKDIR /app
        COPY requirements.txt ./
        RUN pip install --no-cache-dir -r requirements.txt
        COPY . .
        EXPOSE 8080
        CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8080"]
    """.trimIndent()
}
