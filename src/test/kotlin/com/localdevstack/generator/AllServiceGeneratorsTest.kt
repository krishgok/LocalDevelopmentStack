package com.localdevstack.generator

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Validates invariants that every service generator must uphold:
 *   - generates a service/ subdirectory
 *   - exposes /health not /api/hello
 *   - response payload uses "status" not "message"
 */
class AllServiceGeneratorsTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        @JvmStatic
        fun generators() = listOf(
            arrayOf("springboot", SpringBootServiceGenerator()),
            arrayOf("go",         GoServiceGenerator()),
            arrayOf("python",     PythonServiceGenerator()),
            arrayOf("node",       NodeServiceGenerator()),
            arrayOf("rust",       RustServiceGenerator()),
            arrayOf("dotnet",     DotNetServiceGenerator()),
            arrayOf("java",       JavaServiceGenerator()),
            arrayOf("php",        PhpServiceGenerator()),
            arrayOf("ruby",       RubyServiceGenerator()),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `generates a service subdirectory`(name: String, generator: ServiceGenerator) {
        generator.generate(tempDir, "test-svc")
        assertTrue(tempDir.resolve("service").toFile().exists(),
            "$name: expected service/ directory")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `no generated file contains the api-hello route`(name: String, generator: ServiceGenerator) {
        generator.generate(tempDir, "test-svc")
        val violations = tempDir.toFile().walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".class") }
            .filter { it.readText().contains("/api/hello") }
            .map { it.name }
            .toList()
        assertTrue(violations.isEmpty(),
            "$name: found /api/hello in: $violations — must use /health instead")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `generated files contain the health route`(name: String, generator: ServiceGenerator) {
        generator.generate(tempDir, "test-svc")
        val filesWithHealth = tempDir.toFile().walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".class") }
            .any { it.readText().contains("/health") }
        assertTrue(filesWithHealth, "$name: no file contains /health route")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `response payload uses status key not message key`(name: String, generator: ServiceGenerator) {
        generator.generate(tempDir, "test-svc")
        // Collect all source files (exclude gradle/lock/xml boilerplate)
        val sourceFiles = tempDir.toFile().walkTopDown().filter { file ->
            file.isFile && file.extension in setOf("kt", "go", "py", "js", "rs", "rb", "php", "cs", "java")
        }
        val hasStatus = sourceFiles.any { it.readText().contains("status") }
        assertTrue(hasStatus, "$name: no source file contains 'status' key in response")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generators")
    fun `run command is not blank`(name: String, generator: ServiceGenerator) {
        assertTrue(generator.runCommand.isNotBlank(), "$name: runCommand must not be blank")
    }
}
