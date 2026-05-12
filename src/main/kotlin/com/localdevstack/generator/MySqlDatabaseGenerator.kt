package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class MySqlDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] MySQL database       ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("# WARNING: these credentials are for LOCAL DEVELOPMENT ONLY.")
        appendLine("# Change MYSQL_ROOT_PASSWORD and MYSQL_PASSWORD before committing or deploying.")
        appendLine()
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: mysql:8")
        appendLine("    container_name: local-mysql")
        appendLine("    environment:")
        appendLine("      MYSQL_DATABASE: app_db")
        appendLine("      MYSQL_ROOT_PASSWORD: \${MYSQL_ROOT_PASSWORD:-root_dev_only}")
        appendLine("      MYSQL_USER: mysql")
        appendLine("      MYSQL_PASSWORD: \${MYSQL_PASSWORD:-mysql_dev_only}")
        appendLine("    ports:")
        appendLine("      - \"3306:3306\"")
        appendLine("    volumes:")
        appendLine("      - mysql_data:/var/lib/mysql")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD\", \"mysqladmin\", \"ping\", \"-h\", \"localhost\", \"-u\", \"root\", \"-p\${MYSQL_ROOT_PASSWORD:-root_dev_only}\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  mysql_data:")
    }
}
