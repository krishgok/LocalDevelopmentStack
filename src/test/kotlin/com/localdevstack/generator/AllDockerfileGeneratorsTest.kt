package com.localdevstack.generator

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Validates invariants that every Dockerfile generator must uphold:
 *   - always writes Dockerfile.dev (never Dockerfile)
 *   - never overwrites an existing Dockerfile
 *   - exposes port 8080
 *   - uses a sensible base image
 */
class AllDockerfileGeneratorsTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        @JvmStatic
        fun generators() = listOf(
            arrayOf("springboot", SpringBootDockerfileGenerator()),
            arrayOf("go",         GoDockerfileGenerator()),
            arrayOf("python",     PythonDockerfileGenerator()),
            arrayOf("node",       NodeDockerfileGenerator()),
            arrayOf("rust",       RustDockerfileGenerator()),
            arrayOf("dotnet",     DotNetDockerfileGenerator()),
            arrayOf("java",       JavaDockerfileGenerator()),
            arrayOf("php",        PhpDockerfileGenerator()),
            arrayOf("ruby",       RubyDockerfileGenerator()),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `generates Dockerfile dev file`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists(),
            "$name: Dockerfile.dev not found")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `does not generate a plain Dockerfile when none exists`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        assertFalse(tempDir.resolve("Dockerfile").toFile().exists(),
            "$name: must not create a plain Dockerfile")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `does not overwrite existing Dockerfile`(name: String, gen: DockerfileGenerator) {
        val sentinel = "FROM my-custom-base"
        Files.writeString(tempDir.resolve("Dockerfile"), sentinel)
        gen.generate(tempDir, "test-svc")
        assertContains(tempDir.resolve("Dockerfile").toFile().readText(), sentinel,
            "$name: existing Dockerfile was overwritten")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists(),
            "$name: Dockerfile.dev should still be created alongside existing Dockerfile")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev exposes port 8080`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        assertContains(tempDir.resolve("Dockerfile.dev").toFile().readText(), "8080",
            "$name: Dockerfile.dev must expose port 8080")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev specifies a FROM instruction`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        assertContains(tempDir.resolve("Dockerfile.dev").toFile().readText(), "FROM",
            "$name: Dockerfile.dev must have at least one FROM instruction")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev specifies a CMD or ENTRYPOINT`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertTrue(content.contains("CMD") || content.contains("ENTRYPOINT"),
            "$name: Dockerfile.dev must have CMD or ENTRYPOINT")
    }

    // ── Happy path — content spot-checks ─────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev is non-empty`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().length() > 0,
            "$name: Dockerfile.dev must not be empty")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `repeated generation overwrites Dockerfile dev with identical content`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        val first = tempDir.resolve("Dockerfile.dev").toFile().readText()
        gen.generate(tempDir, "test-svc")
        val second = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertTrue(first == second, "$name: repeated generation should produce identical Dockerfile.dev")
    }
}
