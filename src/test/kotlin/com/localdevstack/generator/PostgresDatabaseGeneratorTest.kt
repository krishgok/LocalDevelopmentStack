package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class PostgresDatabaseGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = PostgresDatabaseGenerator()

    @Test
    fun `generates docker-compose yml`() {
        generator.generate(tempDir)
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `docker-compose uses postgres 16 image`() {
        generator.generate(tempDir)
        val content = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(content, "postgres:16")
    }

    @Test
    fun `docker-compose exposes port 5432`() {
        generator.generate(tempDir)
        val content = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(content, "5432:5432")
    }

    @Test
    fun `docker-compose includes healthcheck`() {
        generator.generate(tempDir)
        val content = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(content, "healthcheck")
        assertContains(content, "pg_isready")
    }

    @Test
    fun `docker-compose declares a named volume`() {
        generator.generate(tempDir)
        val content = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(content, "postgres_data")
    }

    @Test
    fun `docker-compose includes default credentials`() {
        generator.generate(tempDir)
        val content = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(content, "POSTGRES_USER: postgres")
        assertContains(content, "POSTGRES_DB: app_db")
    }
}
