package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class MigrateMongoMigrationGenerator : MigrationGenerator {

    override val toolName = "migrate-mongo"

    override fun generateScaffold(outputDir: Path, db: DbConnectionInfo, projectName: String) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("Dockerfile.migrate"), dockerfile())
        Files.writeString(outputDir.resolve("migrate-mongo-config.js"), config())

        val migrationsDir = outputDir.resolve("migrations")
        Files.createDirectories(migrationsDir)
        Files.writeString(migrationsDir.resolve("0001-init.js"), exampleMigration())

        println("  [OK] migrate-mongo files  ->  ${outputDir.resolve("Dockerfile.migrate")}")
        println("                                  ${outputDir.resolve("migrate-mongo-config.js")}")
        println("                                  $migrationsDir")
    }

    override fun composeServiceBlock(db: DbConnectionInfo): String {
        val mongoUri = db.mongoUri ?: "mongodb://db:27017/app_db"
        return buildString {
            appendLine("  migrate:")
            appendLine("    build:")
            appendLine("      context: .")
            appendLine("      dockerfile: Dockerfile.migrate")
            appendLine("    profiles: [\"migrations\"]")
            appendLine("    restart: \"no\"")
            appendLine("    environment:")
            appendLine("      - MONGODB_URI=$mongoUri")
            appendLine("    volumes:")
            appendLine("      - ./migrations:/app/migrations")
            appendLine("      - ./migrate-mongo-config.js:/app/migrate-mongo-config.js")
            appendLine("    command: [\"up\"]")
            appendLine("    depends_on:")
            appendLine("      db:")
            appendLine("        condition: service_healthy")
        }
    }

    override fun createMigrationHint() =
        "To add a new migration: docker-compose run --rm migrate create <name>, then edit migrations/<timestamp>-<name>.js"

    private fun dockerfile() = """
        FROM node:20-alpine
        RUN npm install -g migrate-mongo@11.0.0
        WORKDIR /app
        ENTRYPOINT ["migrate-mongo"]
    """.trimIndent() + "\n"

    private fun config() = """
        const { MONGODB_URI } = process.env;
        const url = MONGODB_URI || "mongodb://db:27017/app_db";
        const dbName = (() => {
            try {
                const path = new URL(url).pathname.replace(/^\//, "");
                return path || "app_db";
            } catch (_e) {
                return "app_db";
            }
        })();

        module.exports = {
            mongodb: {
                url,
                databaseName: dbName,
                options: { useNewUrlParser: true, useUnifiedTopology: true }
            },
            migrationsDir: "migrations",
            changelogCollectionName: "changelog",
            migrationFileExtension: ".js",
            useFileHash: false,
            moduleSystem: "commonjs"
        };
    """.trimIndent() + "\n"

    private fun exampleMigration() = """
        // Example migrate-mongo migration. Rename and edit to fit your schema.
        module.exports = {
            async up(db) {
                await db.createCollection("users");
                await db.collection("users").createIndex({ email: 1 }, { unique: true });
            },
            async down(db) {
                await db.collection("users").drop();
            }
        };
    """.trimIndent() + "\n"
}
