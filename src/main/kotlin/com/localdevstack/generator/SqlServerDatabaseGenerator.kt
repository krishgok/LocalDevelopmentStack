package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class SqlServerDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] SQL Server           ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("version: '3.8'")
        appendLine()
        appendLine("# WARNING: these credentials are for LOCAL DEVELOPMENT ONLY.")
        appendLine("# SQL Server requires a strong password (min 8 chars, upper+lower+digit+symbol).")
        appendLine()
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: mcr.microsoft.com/mssql/server:2022-latest")
        appendLine("    container_name: local-sqlserver")
        appendLine("    environment:")
        appendLine("      ACCEPT_EULA: \"Y\"")
        appendLine("      SA_PASSWORD: \${SA_PASSWORD:-DevOnly_123!}")
        appendLine("      MSSQL_DB: app_db")
        appendLine("    ports:")
        appendLine("      - \"1433:1433\"")
        appendLine("    volumes:")
        appendLine("      - sqlserver_data:/var/opt/mssql")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD-SHELL\", \"/opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P \$${SA_PASSWORD:-DevOnly_123!} -Q 'SELECT 1' || exit 1\"]")
        appendLine("      interval: 15s")
        appendLine("      timeout: 10s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  sqlserver_data:")
    }
}
