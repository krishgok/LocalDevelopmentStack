package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SpringBootServiceGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val generator = SpringBootServiceGenerator()

    @Test
    fun `generates all expected files`() {
        generator.generate(tempDir, "my-service")
        val serviceDir = tempDir.resolve("service")
        assertTrue(serviceDir.resolve("build.gradle.kts").toFile().exists())
        assertTrue(serviceDir.resolve("settings.gradle.kts").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/kotlin/com/example/Application.kt").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/kotlin/com/example/controller/HelloController.kt").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/kotlin/com/example/service/HelloService.kt").toFile().exists())
        assertTrue(serviceDir.resolve("src/main/resources/application.properties").toFile().exists())
    }

    @Test
    fun `settings gradle contains project name`() {
        generator.generate(tempDir, "awesome-api")
        val content = tempDir.resolve("service/settings.gradle.kts").toFile().readText()
        assertContains(content, "awesome-api")
    }

    @Test
    fun `build gradle contains spring boot dependency`() {
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

    @Test
    fun `HelloService returns greeting`() {
        generator.generate(tempDir, "svc")
        val content = tempDir.resolve("service/src/main/kotlin/com/example/service/HelloService.kt").toFile().readText()
        assertContains(content, "Hello, World!")
    }
}
