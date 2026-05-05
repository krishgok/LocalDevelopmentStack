package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresDatabaseGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = PostgresDatabaseGenerator()

    // ── Happy path — database-only (no service block) ─────────────────────────

    @Test
    fun `generates docker-compose yml`() {
        generator.generate(tempDir)
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `docker-compose uses postgres 16 image`() {
        generator.generate(tempDir)
        assertContains(compose(), "postgres:16")
    }

    @Test
    fun `docker-compose uses db as service name`() {
        generator.generate(tempDir)
        assertContains(compose(), "  db:")
    }

    @Test
    fun `docker-compose exposes port 5432`() {
        generator.generate(tempDir)
        assertContains(compose(), "5432:5432")
    }

    @Test
    fun `docker-compose includes healthcheck with pg_isready`() {
        generator.generate(tempDir)
        assertContains(compose(), "healthcheck")
        assertContains(compose(), "pg_isready")
    }

    @Test
    fun `docker-compose declares a named volume`() {
        generator.generate(tempDir)
        assertContains(compose(), "postgres_data")
    }

    @Test
    fun `docker-compose sets POSTGRES_USER and POSTGRES_DB`() {
        generator.generate(tempDir)
        assertContains(compose(), "POSTGRES_USER: postgres")
        assertContains(compose(), "POSTGRES_DB: app_db")
    }

    @Test
    fun `docker-compose includes local development credential warning`() {
        generator.generate(tempDir)
        assertContains(compose(), "LOCAL DEVELOPMENT ONLY")
    }

    @Test
    fun `docker-compose does not include service block when no serviceConfig`() {
        generator.generate(tempDir)
        assertFalse(compose().contains("depends_on"), "No service block should appear without serviceConfig")
        assertFalse(compose().contains("Dockerfile.dev"), "No Dockerfile.dev reference without serviceConfig")
    }

    // ── Happy path — with service block ──────────────────────────────────────

    @Test
    fun `docker-compose includes service container when serviceConfig provided`() {
        generator.generate(tempDir, serviceConfig())
        assertContains(compose(), "my-api:")
    }

    @Test
    fun `docker-compose service block references Dockerfile dev`() {
        generator.generate(tempDir, serviceConfig())
        assertContains(compose(), "Dockerfile.dev")
    }

    @Test
    fun `docker-compose service block maps correct host port`() {
        generator.generate(tempDir, serviceConfig(port = 8081))
        assertContains(compose(), "8081:8081")
    }

    @Test
    fun `docker-compose service block injects DATABASE_URL env var`() {
        generator.generate(tempDir, serviceConfig())
        assertContains(compose(), "DATABASE_URL=")
        assertContains(compose(), "db:")   // host is 'db' (the service name)
    }

    @Test
    fun `docker-compose service block has depends_on db with service_healthy condition`() {
        generator.generate(tempDir, serviceConfig())
        assertContains(compose(), "depends_on")
        assertContains(compose(), "service_healthy")
    }

    // ── Unhappy path ──────────────────────────────────────────────────────────

    @Test
    fun `generates identical files on repeated calls to same directory`() {
        generator.generate(tempDir)
        val first = compose()
        generator.generate(tempDir)
        val second = compose()
        assertTrue(first == second, "Repeated generation should produce identical output")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun compose() = tempDir.resolve("docker-compose.yml").toFile().readText()

    private fun serviceConfig(port: Int = 8080) = ServiceComposeConfig(
        name = "my-api",
        port = port,
        envVars = mapOf("DATABASE_URL" to "postgresql://postgres:postgres_dev_only@db:5432/app_db")
    )
}
