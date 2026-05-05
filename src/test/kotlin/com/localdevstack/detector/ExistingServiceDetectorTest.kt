package com.localdevstack.detector

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExistingServiceDetectorTest {

    @TempDir
    lateinit var tempDir: Path

    // ── Happy path — single sentinel files ───────────────────────────────────

    @ParameterizedTest
    @CsvSource(
        "go.mod,            go",
        "Cargo.toml,        rust",
        "pom.xml,           java",
        "Gemfile,           ruby",
        "composer.json,     php",
        "package.json,      node",
        "requirements.txt,  python",
        "pyproject.toml,    python"
    )
    fun `detects language from sentinel file`(sentinel: String, expectedType: String) {
        Files.writeString(tempDir.resolve(sentinel), "placeholder")
        assertEquals(expectedType, ExistingServiceDetector.detect(tempDir))
    }

    @Test
    fun `detects springboot from build gradle kts`() {
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }")
        assertEquals("springboot", ExistingServiceDetector.detect(tempDir))
    }

    @Test
    fun `detects springboot from build gradle without kts extension`() {
        Files.writeString(tempDir.resolve("build.gradle"), "apply plugin: 'java'")
        assertEquals("springboot", ExistingServiceDetector.detect(tempDir))
    }

    @Test
    fun `pyproject toml alone is detected as python`() {
        Files.writeString(tempDir.resolve("pyproject.toml"), "[tool.poetry]")
        assertEquals("python", ExistingServiceDetector.detect(tempDir))
    }

    @Test
    fun `requirements txt and pyproject toml together still resolve to python not polyglot`() {
        Files.writeString(tempDir.resolve("requirements.txt"), "fastapi")
        Files.writeString(tempDir.resolve("pyproject.toml"), "[tool.poetry]")
        // Both map to same type — should not throw
        assertEquals("python", ExistingServiceDetector.detect(tempDir))
    }

    // ── Happy path — detection is root-level only ────────────────────────────

    @Test
    fun `sentinel in subdirectory is not detected`() {
        val subDir = tempDir.resolve("nested")
        Files.createDirectories(subDir)
        Files.writeString(subDir.resolve("go.mod"), "module test")
        // Root has no sentinel — should throw, not detect from subdir
        assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
    }

    // ── Unhappy path — empty directory ───────────────────────────────────────

    @Test
    fun `empty directory throws DetectionException`() {
        assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
    }

    @Test
    fun `empty directory error mentions Could not detect service type`() {
        val ex = assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
        assertContains(ex.message!!, "Could not detect service type")
    }

    @Test
    fun `empty directory error mentions --service flag`() {
        val ex = assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
        assertContains(ex.message!!, "--service")
    }

    @Test
    fun `empty directory error includes example commands`() {
        val ex = assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
        assertContains(ex.message!!, "localdevstack")
        assertContains(ex.message!!, "--database")
    }

    // ── Unhappy path — polyglot directory ────────────────────────────────────

    @Test
    fun `polyglot directory throws DetectionException`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        Files.writeString(tempDir.resolve("package.json"), "{}")
        assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
    }

    @Test
    fun `polyglot error lists all detected types`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        Files.writeString(tempDir.resolve("package.json"), "{}")
        val ex = assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
        assertContains(ex.message!!, "go")
        assertContains(ex.message!!, "node")
    }

    @Test
    fun `polyglot error provides --service example for each detected type`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        Files.writeString(tempDir.resolve("package.json"), "{}")
        val ex = assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
        // Error should include at least two --service examples
        val count = ex.message!!.split("--service").size - 1
        assertTrue(count >= 2, "Expected at least 2 --service examples, got $count")
    }

    @Test
    fun `polyglot error includes directory name for context`() {
        val namedDir = tempDir.resolve("my-service")
        Files.createDirectories(namedDir)
        Files.writeString(namedDir.resolve("go.mod"), "module test")
        Files.writeString(namedDir.resolve("package.json"), "{}")
        val ex = assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(namedDir)
        }
        assertContains(ex.message!!, "my-service")
    }

    @Test
    fun `three-way polyglot error lists all three types`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        Files.writeString(tempDir.resolve("package.json"), "{}")
        Files.writeString(tempDir.resolve("Cargo.toml"), "[package]")
        val ex = assertFailsWith<DetectionException> {
            ExistingServiceDetector.detect(tempDir)
        }
        assertContains(ex.message!!, "go")
        assertContains(ex.message!!, "node")
        assertContains(ex.message!!, "rust")
    }
}
