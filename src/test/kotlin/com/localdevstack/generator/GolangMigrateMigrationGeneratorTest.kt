package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GolangMigrateMigrationGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = GolangMigrateMigrationGenerator()

    private fun postgresInfo() = DbConnectionInfo(
        databaseType = "postgres",
        jdbcUrl = "jdbc:postgresql://db:5432/app_db",
        user = "postgres",
        password = "postgres_dev_only"
    )

    private fun mongoInfo() = DbConnectionInfo(
        databaseType = "mongodb",
        mongoUri = "mongodb://db:27017/app_db"
    )

    @Test
    fun `tool name is golang-migrate`() {
        assertEquals("golang-migrate", generator.toolName)
    }

    @Test
    fun `sql scaffold creates paired up and down files`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        assertTrue(tempDir.resolve("migrations/000001_init.up.sql").toFile().exists())
        assertTrue(tempDir.resolve("migrations/000001_init.down.sql").toFile().exists())
    }

    @Test
    fun `sql up migration creates users table`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val up = tempDir.resolve("migrations/000001_init.up.sql").toFile().readText()
        assertContains(up, "CREATE TABLE")
        assertContains(up, "users")
    }

    @Test
    fun `sql down migration drops users table`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val down = tempDir.resolve("migrations/000001_init.down.sql").toFile().readText()
        assertContains(down, "DROP TABLE")
        assertContains(down, "users")
    }

    @Test
    fun `mongo scaffold creates paired up and down json files`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        assertTrue(tempDir.resolve("migrations/000001_init.up.json").toFile().exists())
        assertTrue(tempDir.resolve("migrations/000001_init.down.json").toFile().exists())
    }

    @Test
    fun `mongo up migration uses createCollection`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        val up = tempDir.resolve("migrations/000001_init.up.json").toFile().readText()
        assertContains(up, "createCollection")
        assertContains(up, "users")
    }

    @Test
    fun `compose block uses pinned migrate image`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "image: migrate/migrate:v4.17.1")
    }

    @Test
    fun `compose block declares migrations profile`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "profiles: [\"migrations\"]")
    }

    @Test
    fun `postgres compose block uses postgres source url with sslmode disable`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "postgres://postgres:postgres_dev_only@db:5432/app_db")
        assertContains(block, "sslmode=disable")
    }

    @Test
    fun `mysql compose block uses mysql source url`() {
        val info = DbConnectionInfo("mysql", jdbcUrl = "jdbc:mysql://db:3306", user = "mysql", password = "x")
        val block = generator.composeServiceBlock(info)
        assertContains(block, "mysql://mysql:x@tcp(db:3306)/app_db")
    }

    @Test
    fun `mongo compose block uses mongodb source url`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "mongodb://db:27017/app_db")
    }

    @Test
    fun `compose block mounts migrations into migrate path`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "./migrations:/migrations")
    }

    @Test
    fun `compose block uses up subcommand`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, " up")
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
}
