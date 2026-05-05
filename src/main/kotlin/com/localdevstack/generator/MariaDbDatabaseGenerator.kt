package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class MariaDbDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] MariaDB              ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("version: '3.8'")
        appendLine()
        appendLine("# WARNING: these credentials are for LOCAL DEVELOPMENT ONLY.")
        appendLine()
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: mariadb:11")
        appendLine("    container_name: local-mariadb")
        appendLine("    environment:")
        appendLine("      MARIADB_DATABASE: app_db")
        appendLine("      MARIADB_USER: app_user")
        appendLine("      MARIADB_PASSWORD: \${MARIADB_PASSWORD:-mariadb_dev_only}")
        appendLine("      MARIADB_ROOT_PASSWORD: \${MARIADB_ROOT_PASSWORD:-root_dev_only}")
        appendLine("    ports:")
        appendLine("      - \"3306:3306\"")
        appendLine("    volumes:")
        appendLine("      - mariadb_data:/var/lib/mysql")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD\", \"healthcheck.sh\", \"--connect\", \"--innodb_initialized\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  mariadb_data:")
    }
}
