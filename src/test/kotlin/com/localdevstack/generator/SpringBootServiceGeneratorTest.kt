package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpringBootServiceGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = SpringBootServiceGenerator()

    // ── Happy path — file presence ────────────────────────────────────────────

    @Test
    fun `generates all expected files`() {
        generator.generate(tempDir, "my-service")
        val serviceDir = tempDir.resolve("service")
        assertTrue(serviceDir.resolve("build.gradle.kts").toFile().exists())
        assertTrue(serviceDir.resolve("settings.gradle.kts").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/kotlin/com/example/Application.kt").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/kotlin/com/example/controller/HealthController.kt").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/kotlin/com/example/service/HealthService.kt").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/resources/application.properties").toFile().exists())
    }

    @Test
    fun `does not generate legacy hello files`() {
        generator.generate(tempDir, "my-service")
        val serviceDir = tempDir.resolve("service")
        assertFalse(serviceDir.resolve("src/main/kotlin/com/example/controller/HelloController.kt").toFile().exists(),
            "HelloController.kt should not exist — replaced by HealthController.kt")
        assertFalse(serviceDir.resolve("src/main/kotlin/com/example/service/HelloService.kt").toFile().exists(),
            "HelloService.kt should not exist — replaced by HealthService.kt")
    }

    @Test
    fun `settings gradle contains project name`() {
        generator.generate(tempDir, "awesome-api")
        val content = tempDir.resolve("service/settings.gradle.kts").toFile().readText()
        assertContains(content, "awesome-api")
    }

    @Test
    fun `build gradle contains spring boot web dependency`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/build.gradle.kts").toFile().readText()
        assertContains(content, "spring-boot-starter-web")
    }

    @Test
    fun `application properties contains postgres datasource`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/src/main/resources/application.properties").toFile().readText()
        assertContains(content, "spring.datasource.url")
        assertContains(content, "postgresql")
    }

    @Test
    fun `run command is gradle bootRun`() {
        assertTrue(generator.runCommand == "gradle bootRun")
    }

    @Test
    fun `Application kt contains SpringBootApplication annotation`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/src/main/kotlin/com/example/Application.kt").toFile().readText()
        assertContains(content, "@SpringBootApplication")
    }

    // ── Happy path — health endpoint ─────────────────────────────────────────

    @Test
    fun `HealthController maps GET slash health`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/src/main/kotlin/com/example/controller/HealthController.kt").toFile().readText()
        assertContains(content, "/health")
        assertContains(content, "@GetMapping")
    }

    @Test
    fun `HealthController returns status key not message key`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/src/main/kotlin/com/example/controller/HealthController.kt").toFile().readText()
        assertContains(content, "status")
        assertFalse(content.contains("message"), "Controller should not use 'message' key")
    }

    @Test
    fun `HealthService returns ok`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/src/main/kotlin/com/example/service/HealthService.kt").toFile().readText()
        assertContains(content, "\"ok\"")
    }

    @Test
    fun `HealthController does not expose api hello route`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/src/main/kotlin/com/example/controller/HealthController.kt").toFile().readText()
        assertFalse(content.contains("/api/hello"), "Must not expose /api/hello")
    }

    // ── Unhappy path ──────────────────────────────────────────────────────────

    // Regression: Dockerfile.dev previously referenced `gradlew` and `gradle/`,
    // which SpringBootServiceGenerator never emits. `docker-compose up --build`
    // failed at the COPY step in new-scaffold mode. Every COPY source in the
    // generated Dockerfile must exist in the build context (the service dir).
    @Test
    fun `Dockerfile dev COPY sources all exist in generated service dir`() {
        generator.generate(tempDir, "svc")
        val serviceDir = tempDir.resolve("service")
        SpringBootDockerfileGenerator().generate(serviceDir, "svc")

        val dockerfile = serviceDir.resolve("Dockerfile.dev").toFile().readText()
        val copySources = Regex("""^\s*COPY\s+(.+?)\s+\S+\s*$""", RegexOption.MULTILINE)
            .findAll(dockerfile)
            .flatMap { it.groupValues[1].trim().split(Regex("\\s+")) }
            .toList()

        assertTrue(copySources.isNotEmpty(), "expected at least one COPY directive")
        val missing = copySources.filterNot { serviceDir.resolve(it).toFile().exists() }
        assertTrue(missing.isEmpty(),
            "Dockerfile.dev COPYs files missing from the service dir: $missing")
    }

    @Test
    fun `different project names produce distinct settings files`() {
        val tempDir2 = createTempDir("lds-test2")
        try {
            generator.generate(tempDir, "service-alpha")
            generator.generate(tempDir2.toPath(), "service-beta")
            assertContains(tempDir.resolve("service/settings.gradle.kts").toFile().readText(), "service-alpha")
            assertContains(tempDir2.toPath().resolve("service/settings.gradle.kts").toFile().readText(), "service-beta")
        } finally {
            tempDir2.deleteRecursively()
        }
    }
}
