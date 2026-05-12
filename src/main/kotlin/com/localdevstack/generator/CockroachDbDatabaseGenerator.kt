package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class CockroachDbDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] CockroachDB database ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: cockroachdb/cockroach:latest-v23.2")
        appendLine("    container_name: local-cockroachdb")
        appendLine("    command: start-single-node --insecure")
        appendLine("    ports:")
        appendLine("      - \"26257:26257\"  # SQL (PostgreSQL wire protocol)")
        appendLine("      - \"8090:8080\"    # Admin UI (host 8090 avoids conflict with service on 8080)")
        appendLine("    volumes:")
        appendLine("      - cockroach_data:/cockroach/cockroach-data")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD\", \"curl\", \"-f\", \"http://localhost:8080/health?ready=1\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  cockroach_data:")
    }
}
