package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class CockroachDbDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml())
        println("  [OK] CockroachDB database ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml() = """
        version: '3.8'

        services:
          cockroachdb:
            image: cockroachdb/cockroach:latest-v23.2
            container_name: local-cockroachdb
            command: start-single-node --insecure
            ports:
              - "26257:26257"  # SQL (PostgreSQL wire protocol)
              - "8090:8080"    # Admin UI (host port 8090 avoids conflict with the service on 8080)
            volumes:
              - cockroach_data:/cockroach/cockroach-data
            healthcheck:
              test: ["CMD", "curl", "-f", "http://localhost:8080/health?ready=1"]
              interval: 10s
              timeout: 5s
              retries: 5

        volumes:
          cockroach_data:
    """.trimIndent()
}
