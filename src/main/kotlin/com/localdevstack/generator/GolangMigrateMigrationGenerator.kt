package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class GolangMigrateMigrationGenerator : MigrationGenerator {

    override val toolName = "golang-migrate"

    override fun generateScaffold(outputDir: Path, db: DbConnectionInfo, projectName: String) {
        val migrationsDir = outputDir.resolve("migrations")
        Files.createDirectories(migrationsDir)

        if (db.databaseType.lowercase() == "mongodb") {
            Files.writeString(migrationsDir.resolve("000001_init.up.json"), mongoUpExample())
            Files.writeString(migrationsDir.resolve("000001_init.down.json"), mongoDownExample())
        } else {
            Files.writeString(migrationsDir.resolve("000001_init.up.sql"), sqlUpExample(db.databaseType))
            Files.writeString(migrationsDir.resolve("000001_init.down.sql"), sqlDownExample())
        }
        println("  [OK] golang-migrate files ->  $migrationsDir")
    }

    override fun composeServiceBlock(db: DbConnectionInfo): String {
        val (sourceUrl, dbCondition) = sourceAndCondition(db)
        return buildString {
            appendLine("  migrate:")
            appendLine("    image: migrate/migrate:v4.17.1")
            appendLine("    profiles: [\"migrations\"]")
            appendLine("    restart: \"no\"")
            appendLine("    command: >")
            appendLine("      -path=/migrations")
            appendLine("      -database $sourceUrl")
            appendLine("      up")
            appendLine("    volumes:")
            appendLine("      - ./migrations:/migrations")
            appendLine("    depends_on:")
            appendLine("      db:")
            appendLine("        condition: $dbCondition")
        }
    }

    override fun createMigrationHint() =
        "To add a new migration: docker-compose run --rm migrate create -ext sql -dir /migrations -seq <name>, then edit the generated up/down files"

    private fun sourceAndCondition(db: DbConnectionInfo): Pair<String, String> = when (db.databaseType.lowercase()) {
        "postgres" -> "postgres://postgres:${db.password ?: ""}@db:5432/app_db?sslmode=disable" to "service_healthy"
        "mysql" -> "mysql://${db.user ?: "mysql"}:${db.password ?: ""}@tcp(db:3306)/app_db" to "service_healthy"
        "mariadb" -> "mysql://${db.user ?: "app_user"}:${db.password ?: ""}@tcp(db:3306)/app_db" to "service_healthy"
        "cockroachdb" -> "cockroachdb://root@db:26257/app_db?sslmode=disable" to "service_healthy"
        "sqlserver" -> "sqlserver://sa:${db.password ?: ""}@db:1433?database=app_db" to "service_healthy"
        "mongodb" -> "mongodb://db:27017/app_db" to "service_healthy"
        else -> error("golang-migrate does not support database type: ${db.databaseType}")
    }

    private fun sqlUpExample(databaseType: String): String =
        """
            -- Example golang-migrate up migration. Rename and edit to fit your schema.
            CREATE TABLE IF NOT EXISTS users (
                ${identityColumnSql(databaseType)},
                email VARCHAR(255) NOT NULL UNIQUE
            );
        """.trimIndent() + "\n"

    private fun sqlDownExample() = """
        DROP TABLE IF EXISTS users;
    """.trimIndent() + "\n"

    private fun mongoUpExample() = """
        [
          { "createCollection": "users" },
          { "createIndex": { "collection": "users", "keys": { "email": 1 }, "options": { "unique": true } } }
        ]
    """.trimIndent() + "\n"

    private fun mongoDownExample() = """
        [
          { "drop": "users" }
        ]
    """.trimIndent() + "\n"
}
