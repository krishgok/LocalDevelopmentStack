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
        if (Files.exists(existing)) {
            println("  Note: Dockerfile already exists. Generating Dockerfile.dev for local development.")
            println("        Review Dockerfile.dev before promoting it to production.")
        }
        return serviceDir.resolve("Dockerfile.dev")
    }

    private fun dockerfile() = """
        FROM php:8.2-cli-alpine
        RUN docker-php-ext-install pdo pdo_mysql pdo_pgsql \
            && curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer
        WORKDIR /app
        COPY composer.json composer.lock* ./
        RUN composer install --no-interaction --no-scripts --prefer-dist
        EXPOSE 8080
        CMD ["php", "-S", "0.0.0.0:8080", "-t", "public"]
    """.trimIndent()
}
