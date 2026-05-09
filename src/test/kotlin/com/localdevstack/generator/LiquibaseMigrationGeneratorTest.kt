package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquibaseMigrationGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = LiquibaseMigrationGenerator()

    private fun postgresInfo() = DbConnectionInfo(
        databaseType = "postgres",
        jdbcUrl = "jdbc:postgresql://db:5432/app_db",
        user = "postgres",
        password = "postgres_dev_only"
    )

    @Test
    fun `tool name is liquibase`() {
        assertEquals("liquibase", generator.toolName)
    }

    @Test
    fun `generateScaffold creates db changelog directory with master file`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        assertTrue(tempDir.resolve("db/changelog/db.changelog-master.sql").toFile().exists())
    }

    @Test
    fun `changelog uses formatted SQL header`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val sql = tempDir.resolve("db/changelog/db.changelog-master.sql").toFile().readText()
        assertContains(sql, "--liquibase formatted sql")
        assertContains(sql, "--changeset")
    }

    @Test
    fun `changelog creates a users table`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val sql = tempDir.resolve("db/changelog/db.changelog-master.sql").toFile().readText()
        assertContains(sql, "CREATE TABLE")
        assertContains(sql, "users")
    }

    @Test
    fun `compose block uses pinned liquibase image`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "image: liquibase/liquibase:4.27")
    }

    @Test
    fun `compose block declares migrations profile`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "profiles: [\"migrations\"]")
    }

    @Test
    fun `compose block uses double-dash long flags`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "--url=jdbc:postgresql://db:5432/app_db")
        assertContains(block, "--username=postgres")
        assertContains(block, "--password=postgres_dev_only")
        assertContains(block, "update")
    }

    @Test
    fun `compose block mounts changelog directory`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "./db/changelog:/liquibase/changelog")
    }

    @Test
    fun `compose block references the master changelog file`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "--changeLogFile=/liquibase/changelog/db.changelog-master.sql")
    }

    @Test
    fun `compose block depends on db service_healthy`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "depends_on")
        assertContains(block, "service_healthy")
    }

    @Test
    fun `compose block has restart no`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "restart: \"no\"")
    }

    @Test
    fun `migration hint references master changelog`() {
        assertContains(generator.createMigrationHint(), "db.changelog-master.sql")
    }

    @Test
    fun `repeated scaffold generation produces identical output`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val first = tempDir.resolve("db/changelog/db.changelog-master.sql").toFile().readText()
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val second = tempDir.resolve("db/changelog/db.changelog-master.sql").toFile().readText()
        assertTrue(first == second, "Repeated scaffolding should be byte-identical")
    }
}
