package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class LiquibaseMigrationGenerator : MigrationGenerator {

    override val toolName = "liquibase"

    override fun generateScaffold(outputDir: Path, db: DbConnectionInfo, projectName: String) {
        val changelogDir = outputDir.resolve("db/changelog")
        Files.createDirectories(changelogDir)
        Files.writeString(changelogDir.resolve("db.changelog-master.sql"), exampleChangelog(db.databaseType))
        println("  [OK] Liquibase changelog  ->  $changelogDir")
    }

    override fun composeServiceBlock(db: DbConnectionInfo): String {
        val jdbcUrl = requireNotNull(db.jdbcUrl) { "Liquibase requires a JDBC URL" }
        val user = db.user ?: ""
        val password = db.password ?: ""
        return buildString {
            appendLine("  migrate:")
            appendLine("    image: liquibase/liquibase:4.27")
            appendLine("    profiles: [\"migrations\"]")
            appendLine("    restart: \"no\"")
            appendLine("    command: >")
            appendLine("      --url=$jdbcUrl")
            appendLine("      --username=$user")
            appendLine("      --password=$password")
            appendLine("      --changeLogFile=/liquibase/changelog/db.changelog-master.sql")
            appendLine("      update")
            appendLine("    volumes:")
            appendLine("      - ./db/changelog:/liquibase/changelog")
            appendLine("    depends_on:")
            appendLine("      db:")
            appendLine("        condition: service_healthy")
        }
    }

    override fun createMigrationHint() =
        "To add a new changeset: append to db/changelog/db.changelog-master.sql (Liquibase formatted SQL), then run: docker-compose run --rm migrate"

    private fun exampleChangelog(databaseType: String): String {
        val identityColumn = when (databaseType.lowercase()) {
            "postgres" -> "id    SERIAL PRIMARY KEY"
            "sqlserver" -> "id    INT IDENTITY(1,1) PRIMARY KEY"
            else        -> "id    INT AUTO_INCREMENT PRIMARY KEY"
        }
        return """
            --liquibase formatted sql

            --changeset localdevstack:1
            CREATE TABLE IF NOT EXISTS users (
                $identityColumn,
                email VARCHAR(255) NOT NULL UNIQUE
            );
        """.trimIndent() + "\n"
    }
}
