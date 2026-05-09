package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlywayMigrationGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = FlywayMigrationGenerator()

    private fun postgresInfo() = DbConnectionInfo(
        databaseType = "postgres",
        jdbcUrl = "jdbc:postgresql://db:5432/app_db",
        user = "postgres",
        password = "postgres_dev_only"
    )

    @Test
    fun `tool name is flyway`() {
        assertEquals("flyway", generator.toolName)
    }

    @Test
    fun `generateScaffold creates migrations directory with V001 init sql`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        assertTrue(tempDir.resolve("migrations/V001__init.sql").toFile().exists())
    }

    @Test
    fun `example migration creates a users table`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val sql = tempDir.resolve("migrations/V001__init.sql").toFile().readText()
        assertContains(sql, "CREATE TABLE")
        assertContains(sql, "users")
    }

    @Test
    fun `postgres example uses SERIAL identity`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val sql = tempDir.resolve("migrations/V001__init.sql").toFile().readText()
        assertContains(sql, "SERIAL PRIMARY KEY")
    }

    @Test
    fun `sqlserver example uses IDENTITY column`() {
        val info = DbConnectionInfo("sqlserver", jdbcUrl = "jdbc:sqlserver://db:1433", user = "sa", password = "X")
        generator.generateScaffold(tempDir, info, "svc")
        val sql = tempDir.resolve("migrations/V001__init.sql").toFile().readText()
        assertContains(sql, "INT IDENTITY(1,1) PRIMARY KEY")
    }

    @Test
    fun `mysql example uses AUTO_INCREMENT identity`() {
        val info = DbConnectionInfo("mysql", jdbcUrl = "jdbc:mysql://db:3306/app_db", user = "mysql", password = "X")
        generator.generateScaffold(tempDir, info, "svc")
        val sql = tempDir.resolve("migrations/V001__init.sql").toFile().readText()
        assertContains(sql, "AUTO_INCREMENT")
    }

    @Test
    fun `compose block declares migrations profile`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "profiles: [\"migrations\"]")
    }

    @Test
    fun `compose block uses pinned flyway image`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "image: flyway/flyway:10")
    }

    @Test
    fun `compose block injects jdbc url user and password`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "-url=jdbc:postgresql://db:5432/app_db")
        assertContains(block, "-user=postgres")
        assertContains(block, "-password=postgres_dev_only")
    }

    @Test
    fun `compose block sets restart no for one-shot semantics`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "restart: \"no\"")
    }

    @Test
    fun `compose block mounts migrations into flyway sql path`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "./migrations:/flyway/sql")
    }

    @Test
    fun `compose block depends on db with service_healthy condition`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "depends_on")
        assertContains(block, "service_healthy")
    }

    @Test
    fun `compose block includes connect retries`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertContains(block, "-connectRetries=60")
    }

    @Test
    fun `migration hint mentions docker-compose run`() {
        assertContains(generator.createMigrationHint(), "docker-compose run --rm migrate")
    }

    @Test
    fun `repeated scaffold generation produces identical output`() {
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val first = tempDir.resolve("migrations/V001__init.sql").toFile().readText()
        generator.generateScaffold(tempDir, postgresInfo(), "svc")
        val second = tempDir.resolve("migrations/V001__init.sql").toFile().readText()
        assertTrue(first == second, "Repeated scaffolding should be byte-identical")
    }

    @Test
    fun `compose block does not include version key (not a top-level compose section)`() {
        val block = generator.composeServiceBlock(postgresInfo())
        assertFalse(block.contains("version:"), "migrate block must be a service-level snippet, not a full compose file")
    }
}
