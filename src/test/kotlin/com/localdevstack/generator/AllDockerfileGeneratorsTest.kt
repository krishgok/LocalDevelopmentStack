package com.localdevstack.generator

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        // name, generator, hot-reload indicator, dependency manifest snippet
        @JvmStatic
        fun generatorsWithHotReload() = listOf(
            arrayOf("springboot", SpringBootDockerfileGenerator(), "bootRun",        "build.gradle"),
            arrayOf("go",         GoDockerfileGenerator(),         "air",            "go.mod"),
            arrayOf("python",     PythonDockerfileGenerator(),     "--reload",       "requirements"),
            arrayOf("node",       NodeDockerfileGenerator(),       "nodemon",        "package"),
            arrayOf("rust",       RustDockerfileGenerator(),       "cargo",          "Cargo.toml"),
            arrayOf("dotnet",     DotNetDockerfileGenerator(),     "watch",          ".csproj"),
            arrayOf("java",       JavaDockerfileGenerator(),       "spring-boot:run","pom.xml"),
            arrayOf("php",        PhpDockerfileGenerator(),        "php",            "composer"),
            arrayOf("ruby",       RubyDockerfileGenerator(),       "app.rb",         "Gemfile"),
        )
    }

    // ── Structural invariants ─────────────────────────────────────────────────

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
            message = "$name: existing Dockerfile was overwritten")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists(),
            "$name: Dockerfile.dev should still be created alongside existing Dockerfile")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev exposes port 8080`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        assertContains(tempDir.resolve("Dockerfile.dev").toFile().readText(), "8080",
            message = "$name: Dockerfile.dev must expose port 8080")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev specifies a FROM instruction`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        assertContains(tempDir.resolve("Dockerfile.dev").toFile().readText(), "FROM",
            message = "$name: Dockerfile.dev must have at least one FROM instruction")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev specifies a CMD or ENTRYPOINT`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertTrue(content.contains("CMD") || content.contains("ENTRYPOINT"),
            "$name: Dockerfile.dev must have CMD or ENTRYPOINT")
    }

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

    // ── Hot-reload invariants ─────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `Dockerfile dev does not copy entire source tree`(name: String, gen: DockerfileGenerator) {
        gen.generate(tempDir, "test-svc")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertFalse(content.contains("COPY . ."),
            "$name: source must be volume-mounted at runtime, not baked into image with COPY . .")
    }

    @ParameterizedTest(name = "{0} uses correct hot-reload tool")
    @MethodSource("generatorsWithHotReload")
    fun `Dockerfile dev references the hot-reload tool`(
        name: String, gen: DockerfileGenerator, hotReloadTool: String, @Suppress("UNUSED_PARAMETER") ignored: String
    ) {
        gen.generate(tempDir, "test-svc")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, hotReloadTool,
            message = "$name: Dockerfile.dev must reference hot-reload tool '$hotReloadTool'")
    }

    @ParameterizedTest(name = "{0} copies dependency manifest")
    @MethodSource("generatorsWithHotReload")
    fun `Dockerfile dev copies dependency manifest for layer caching`(
        name: String, gen: DockerfileGenerator, @Suppress("UNUSED_PARAMETER") ignored: String, depManifest: String
    ) {
        gen.generate(tempDir, "test-svc")
        val content = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(content, depManifest,
            message = "$name: Dockerfile.dev must COPY the dependency manifest ('$depManifest') for layer caching, even though source is volume-mounted")
    }
}
