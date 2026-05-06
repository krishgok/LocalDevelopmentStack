package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoDockerfileGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = GoDockerfileGenerator()

    @Test
    fun `generates Dockerfile dev when no Dockerfile exists`() {
        generator.generate(tempDir, "my-service")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists())
        assertFalse(tempDir.resolve("Dockerfile").toFile().exists())
    }

    @Test
    fun `generates Dockerfile dev alongside existing Dockerfile`() {
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM scratch")
        generator.generate(tempDir, "my-service")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists())
        assertTrue(tempDir.resolve("Dockerfile").toFile().exists())
        assertContains(tempDir.resolve("Dockerfile").toFile().readText(), "FROM scratch")
    }

    @Test
    fun `Dockerfile dev uses air for hot reload`() {
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, "golang:1.22-alpine")
        assertContains(content, "air")
        assertContains(content, "go mod download")
    }

    @Test
    fun `Dockerfile dev is single stage not multi stage`() {
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertFalse(content.contains("AS builder"), "Hot-reload Dockerfile must be single-stage")
        assertFalse(content.contains("alpine:3.19"), "Runtime-only alpine stage must not exist in hot-reload mode")
    }

    @Test
    fun `Dockerfile dev exposes port 8080`() {
        generator.generate(tempDir, "my-service")
        assertContains(tempDir.resolve("Dockerfile.dev").toFile().readText(), "EXPOSE 8080")
    }

    @Test
    fun `Dockerfile dev does not copy source files`() {
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertFalse(content.contains("COPY . ."), "Source is volume-mounted at runtime — must not COPY . .")
    }
}
