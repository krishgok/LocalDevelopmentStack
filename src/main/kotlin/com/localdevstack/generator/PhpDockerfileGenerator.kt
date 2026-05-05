package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class PhpDockerfileGenerator : DockerfileGenerator {

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
        FROM php:8.2-fpm-alpine
        WORKDIR /app
        RUN apk --no-cache add composer
        COPY composer.json composer.lock* ./
        RUN composer install --no-dev --optimize-autoloader
        COPY . .
        EXPOSE 8080
        CMD ["php", "artisan", "serve", "--host=0.0.0.0", "--port=8080"]
    """.trimIndent()
}
