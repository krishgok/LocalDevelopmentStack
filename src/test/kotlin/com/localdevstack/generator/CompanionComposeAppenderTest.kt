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

class CompanionComposeAppenderTest {

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbGenerators")
    fun `empty companion list produces byte-identical compose output for every db`(dbName: String, gen: DatabaseGenerator) {
        gen.generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")
        val before = Files.readString(composeFile)

        appendCompanionBlocksToCompose(composeFile, emptyList())

        val after = Files.readString(composeFile)
        assertEquals(before, after, "$dbName: compose must be byte-identical when no companions are requested")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbGenerators")
    fun `mailhog companion is inserted before volumes for every db`(dbName: String, gen: DatabaseGenerator) {
        gen.generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")

        appendCompanionBlocksToCompose(composeFile, listOf(MailhogCompanionGenerator()))

        val text = Files.readString(composeFile)
        val mailhogIdx = text.indexOf("  mailhog:")
        val volumesIdx = text.indexOf("\nvolumes:\n")
        assertTrue(mailhogIdx > 0, "$dbName: mailhog block must be present")
        assertTrue(volumesIdx > mailhogIdx,
            "$dbName: mailhog block must appear before the top-level volumes section")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dbGenerators")
    fun `minio companion adds named volume entry under volumes`(dbName: String, gen: DatabaseGenerator) {
        gen.generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")

        appendCompanionBlocksToCompose(composeFile, listOf(MinioCompanionGenerator()))

        val text = Files.readString(composeFile)
        val minioIdx = text.indexOf("  minio:")
        val volumesIdx = text.indexOf("\nvolumes:\n")
        val volumeEntryIdx = text.indexOf("  minio_data:")
        assertTrue(minioIdx > 0, "$dbName: minio block must be present")
        assertTrue(volumesIdx > 0, "$dbName: top-level volumes section must remain")
        assertTrue(volumeEntryIdx > volumesIdx,
            "$dbName: minio_data volume entry must appear inside the volumes section")
    }

    @Test
    fun `both companions together preserve db service and ordering`() {
        PostgresDatabaseGenerator().generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")
        appendCompanionBlocksToCompose(
            composeFile,
            listOf(MailhogCompanionGenerator(), MinioCompanionGenerator())
        )
        val text = Files.readString(composeFile)
        assertContains(text, "  db:")
        assertContains(text, "  mailhog:")
        assertContains(text, "  minio:")
        assertContains(text, "  postgres_data:")
        assertContains(text, "  minio_data:")
        val mailhogIdx = text.indexOf("  mailhog:")
        val minioIdx = text.indexOf("  minio:")
        val volumesIdx = text.indexOf("\nvolumes:\n")
        assertTrue(mailhogIdx < minioIdx,
            "mailhog block must appear before minio (preserves --with order)")
        assertTrue(minioIdx < volumesIdx,
            "both companion blocks must appear before the top-level volumes section")
    }

    @Test
    fun `companions plus migration both append correctly`() {
        PostgresDatabaseGenerator().generate(tempDir)
        val composeFile = tempDir.resolve("docker-compose.yml")
        // Migration first, then companions — mirrors CLI invocation order.
        appendMigrateBlockToCompose(composeFile, buildString {
            appendLine("  migrate:")
            appendLine("    image: flyway/flyway:10")
        })
        appendCompanionBlocksToCompose(composeFile, listOf(MailhogCompanionGenerator()))

        val text = Files.readString(composeFile)
        assertContains(text, "  db:")
        assertContains(text, "  migrate:")
        assertContains(text, "  mailhog:")
    }
}
