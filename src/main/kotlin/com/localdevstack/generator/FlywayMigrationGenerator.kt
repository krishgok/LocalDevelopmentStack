package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class FlywayMigrationGenerator : MigrationGenerator {

    override val toolName = "flyway"

    override fun generateScaffold(outputDir: Path, db: DbConnectionInfo, projectName: String) {
        val migrationsDir = outputDir.resolve("migrations")
        Files.createDirectories(migrationsDir)
        Files.writeString(migrationsDir.resolve("V001__init.sql"), exampleMigration(db.databaseType))
        println("  [OK] Flyway migrations    ->  $migrationsDir")
    }

    override fun composeServiceBlock(db: DbConnectionInfo): String {
        val jdbcUrl = requireNotNull(db.jdbcUrl) { "Flyway requires a JDBC URL" }
        val user = db.user ?: ""
        val password = db.password ?: ""
        return buildString {
            appendLine("  migrate:")
            appendLine("    image: flyway/flyway:10")
            appendLine("    profiles: [\"migrations\"]")
            appendLine("    restart: \"no\"")
            appendLine("    command: >")
            appendLine("      -url=$jdbcUrl")
            appendLine("      -user=$user")
            appendLine("      -password=$password")
            appendLine("      -connectRetries=60")
            appendLine("      migrate")
            appendLine("    volumes:")
            appendLine("      - ./migrations:/flyway/sql")
            appendLine("    depends_on:")
            appendLine("      db:")
            appendLine("        condition: service_healthy")
        }
    }

    override fun createMigrationHint() =
        "To add a new migration: create a file at migrations/V<N>__<name>.sql, then run: docker-compose run --rm migrate"

    private fun exampleMigration(databaseType: String): String {
        val identityColumn = when (databaseType.lowercase()) {
            "postgres", "cockroachdb" -> "id    SERIAL PRIMARY KEY"
            "sqlserver"               -> "id    INT IDENTITY(1,1) PRIMARY KEY"
            else                      -> "id    INT AUTO_INCREMENT PRIMARY KEY"
        }
        return """
            -- Example Flyway migration. Rename and edit to fit your schema.
            CREATE TABLE IF NOT EXISTS users (
                $identityColumn,
                email VARCHAR(255) NOT NULL UNIQUE
            );
        """.trimIndent() + "\n"
    }
}
