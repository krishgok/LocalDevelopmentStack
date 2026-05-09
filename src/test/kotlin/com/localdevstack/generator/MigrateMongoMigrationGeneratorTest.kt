package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigrateMongoMigrationGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = MigrateMongoMigrationGenerator()

    private fun mongoInfo() = DbConnectionInfo(
        databaseType = "mongodb",
        mongoUri = "mongodb://db:27017/app_db"
    )

    @Test
    fun `tool name is migrate-mongo`() {
        assertEquals("migrate-mongo", generator.toolName)
    }

    @Test
    fun `generateScaffold creates Dockerfile migrate config and example migration`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        assertTrue(tempDir.resolve("Dockerfile.migrate").toFile().exists())
        assertTrue(tempDir.resolve("migrate-mongo-config.js").toFile().exists())
        assertTrue(tempDir.resolve("migrations/0001-init.js").toFile().exists())
    }

    @Test
    fun `Dockerfile migrate is based on node alpine and pins migrate-mongo`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        val df = tempDir.resolve("Dockerfile.migrate").toFile().readText()
        assertContains(df, "FROM node:20-alpine")
        assertContains(df, "npm install -g migrate-mongo@")
    }

    @Test
    fun `Dockerfile migrate sets entrypoint to migrate-mongo`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        val df = tempDir.resolve("Dockerfile.migrate").toFile().readText()
        assertContains(df, "ENTRYPOINT [\"migrate-mongo\"]")
    }

    @Test
    fun `Dockerfile migrate does not COPY source`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        val df = tempDir.resolve("Dockerfile.migrate").toFile().readText()
        assertFalse(df.contains("COPY . ."), "Dockerfile.migrate must not copy entire project")
    }

    @Test
    fun `config reads MONGODB_URI from env`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        val cfg = tempDir.resolve("migrate-mongo-config.js").toFile().readText()
        assertContains(cfg, "MONGODB_URI")
    }

    @Test
    fun `example migration uses up and down semantics`() {
        generator.generateScaffold(tempDir, mongoInfo(), "svc")
        val mig = tempDir.resolve("migrations/0001-init.js").toFile().readText()
        assertContains(mig, "async up(db)")
        assertContains(mig, "async down(db)")
        assertContains(mig, "users")
    }

    @Test
    fun `compose block builds from Dockerfile migrate`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "dockerfile: Dockerfile.migrate")
    }

    @Test
    fun `compose block declares migrations profile`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "profiles: [\"migrations\"]")
    }

    @Test
    fun `compose block injects MONGODB_URI env var`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "MONGODB_URI=mongodb://db:27017/app_db")
    }

    @Test
    fun `compose block mounts migrations and config`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "./migrations:/app/migrations")
        assertContains(block, "./migrate-mongo-config.js:/app/migrate-mongo-config.js")
    }

    @Test
    fun `compose block runs the up command`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "command: [\"up\"]")
    }

    @Test
    fun `compose block depends on db service_healthy`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "depends_on")
        assertContains(block, "service_healthy")
    }

    @Test
    fun `compose block has restart no`() {
        val block = generator.composeServiceBlock(mongoInfo())
        assertContains(block, "restart: \"no\"")
    }
}
