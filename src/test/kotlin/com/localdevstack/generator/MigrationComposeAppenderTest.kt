package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationComposeAppenderTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        @JvmStatic
        fun dbGenerators(): List<Array<Any>> = listOf(
            arrayOf("postgres",      PostgresDatabaseGenerator()),
            arrayOf("mysql",         MySqlDatabaseGenerator()),
            arrayOf("mariadb",       MariaDbDatabaseGenerator()),
            arrayOf("cockroachdb",   CockroachDbDatabaseGenerator()),
            arrayOf("sqlserver",     SqlServerDatabaseGenerator()),
            arrayOf("mongodb",       MongoDbDatabaseGenerator()),
            arrayOf("redis",         RedisDatabaseGenerator()),
            arrayOf("elasticsearch", ElasticsearchDatabaseGenerator()),
        )
    }

    private val sampleBlock = buildString {
        appendLine("  migrate:")
        appendLine("    image: flyway/flyway:10")
        appendLine("    profiles: [\"migrations\"]")
        appendLine("    depends_on:")
        appendLine("      db:")
        appendLine("        condition: service_healthy")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbGenerators")
    fun `appender inserts migrate block before volumes for every db`(dbName: String, gen: DatabaseGenerator) {
        gen.generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")

        appendMigrateBlockToCompose(composeFile, sampleBlock)

        val text = Files.readString(composeFile)
        val migrateIdx = text.indexOf("  migrate:")
        val volumesIdx = text.indexOf("\nvolumes:\n")

        assertTrue(migrateIdx > 0, "$dbName: migrate block should be present")
        assertTrue(volumesIdx > migrateIdx,
            "$dbName: migrate block must appear before the top-level volumes section")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbGenerators")
    fun `appender preserves db service block`(dbName: String, gen: DatabaseGenerator) {
        gen.generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")
        appendMigrateBlockToCompose(composeFile, sampleBlock)
        val text = Files.readString(composeFile)
        assertContains(text, "  db:", message = "$dbName: db service must still be present after appending")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbGenerators")
    fun `appender preserves trailing volumes section content`(dbName: String, gen: DatabaseGenerator) {
        gen.generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")
        val before = Files.readString(composeFile)
        val volumesPart = before.substring(before.indexOf("\nvolumes:\n"))

        appendMigrateBlockToCompose(composeFile, sampleBlock)

        val after = Files.readString(composeFile)
        assertTrue(after.endsWith(volumesPart) || after.contains(volumesPart),
            "$dbName: original volumes section content must be preserved verbatim")
    }

    @Test
    fun `appender is repeatable - inserts twice produce two migrate blocks`() {
        // We don't try to dedupe; the CLI is expected to call once per run.
        val gen = PostgresDatabaseGenerator()
        gen.generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")
        appendMigrateBlockToCompose(composeFile, sampleBlock)
        appendMigrateBlockToCompose(composeFile, sampleBlock)
        val text = Files.readString(composeFile)
        val occurrences = text.split("  migrate:").size - 1
        assertEquals(2, occurrences, "Two consecutive appends should yield two migrate blocks")
    }

    @Test
    fun `appender with no volumes section appends at end`() {
        val composeFile = tempDir.resolve("docker-compose.yml")
        Files.writeString(composeFile, "version: '3.8'\nservices:\n  db:\n    image: postgres:16\n")

        appendMigrateBlockToCompose(composeFile, sampleBlock)

        val text = Files.readString(composeFile)
        assertContains(text, "  migrate:")
        assertContains(text, "  db:")
    }
}
