package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class GoDockerfileGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = GoDockerfileGenerator()

    @Test
    fun `generates Dockerfile dev when no Dockerfile exists`() {
        generator.generate(tempDir, "my-service")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists())
        assertTrue(!tempDir.resolve("Dockerfile").toFile().exists())
    }

    @Test
    fun `generates Dockerfile dev alongside existing Dockerfile`() {
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM scratch")
        generator.generate(tempDir, "my-service")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists())
        assertTrue(tempDir.resolve("Dockerfile").toFile().exists())
        // Original Dockerfile is not modified
        assertContains(tempDir.resolve("Dockerfile").toFile().readText(), "FROM scratch")
    }

    @Test
    fun `Dockerfile dev uses multi-stage build`() {
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, "golang:1.22-alpine")
        assertContains(content, "alpine:3.19")
        assertContains(content, "AS builder")
    }

    @Test
    fun `Dockerfile dev exposes port 8080`() {
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, "EXPOSE 8080")
    }

    @Test
    fun `Dockerfile dev copies go mod and downloads dependencies`() {
        generator.generate(tempDir, "my-service")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, "go.mod")
        assertContains(content, "go mod download")
    }
}
