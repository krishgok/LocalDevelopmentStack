package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class PostgresDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] Postgres database    ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("# WARNING: these credentials are for LOCAL DEVELOPMENT ONLY.")
        appendLine("# Change POSTGRES_PASSWORD before committing or deploying to any shared environment.")
        appendLine()
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: postgres:16")
        appendLine("    container_name: local-postgres")
        appendLine("    environment:")
        appendLine("      POSTGRES_DB: app_db")
        appendLine("      POSTGRES_USER: postgres")
        appendLine("      POSTGRES_PASSWORD: \${POSTGRES_PASSWORD:-postgres_dev_only}")
        appendLine("    ports:")
        appendLine("      - \"5432:5432\"")
        appendLine("    volumes:")
        appendLine("      - postgres_data:/var/lib/postgresql/data")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD-SHELL\", \"pg_isready -U postgres\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  postgres_data:")
    }
}
