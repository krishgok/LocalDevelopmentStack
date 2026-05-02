package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class PostgresDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml())
        println("  [OK] Postgres database    ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml() = """
        version: '3.8'

        # WARNING: these credentials are for LOCAL DEVELOPMENT ONLY.
        # Change POSTGRES_PASSWORD (and the application.properties datasource password)
        # before committing or deploying to any shared environment.
        # Better yet, use a .env file and reference variables with ${"\${VAR_NAME}"} syntax.

        services:
          postgres:
            image: postgres:16
            container_name: local-postgres
            environment:
              POSTGRES_DB: app_db
              POSTGRES_USER: postgres
              POSTGRES_PASSWORD: ${"$"}{POSTGRES_PASSWORD:-postgres_dev_only}
            ports:
              - "5432:5432"
            volumes:
              - postgres_data:/var/lib/postgresql/data
            healthcheck:
              test: ["CMD-SHELL", "pg_isready -U postgres"]
              interval: 10s
              timeout: 5s
              retries: 5

        volumes:
          postgres_data:
    """.trimIndent()
}
