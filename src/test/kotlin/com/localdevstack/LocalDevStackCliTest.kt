package com.localdevstack

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalDevStackCliTest {

    @TempDir
    lateinit var tempDir: Path

    private fun cli(block: LocalDevStackCli.() -> Unit = {}): LocalDevStackCli {
        return LocalDevStackCli().apply {
            skipDockerCheck = true
            outputDir = tempDir.toString()
            block()
        }
    }

    private fun captureErr(block: () -> Unit): String {
        val err = ByteArrayOutputStream()
        val orig = System.err
        System.setErr(PrintStream(err))
        try { block() } finally { System.setErr(orig) }
        return err.toString()
    }

    // ── Happy path — new service scaffold ────────────────────────────────────

    @Test
    fun `defaults produce springboot service and postgres compose`() {
        cli().run()
        assertTrue(tempDir.resolve("service/build.gradle.kts").toFile().exists())
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @ParameterizedTest
    @ValueSource(strings = ["springboot", "go", "python", "node", "rust", "dotnet", "java", "php", "ruby"])
    fun `all service types generate a service directory`(serviceType: String) {
        cli { this.serviceType = serviceType }.run()
        assertTrue(tempDir.resolve("service").toFile().exists(),
            "Expected service/ for $serviceType")
    }

    @ParameterizedTest
    @ValueSource(strings = ["postgres", "mysql", "mongodb", "cockroachdb", "redis", "mariadb", "sqlserver", "elasticsearch"])
    fun `all database types generate docker-compose yml`(dbType: String) {
        cli { databaseType = dbType }.run()
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists(),
            "Expected docker-compose.yml for $dbType")
    }

    @Test
    fun `project name is reflected in spring boot settings file`() {
        cli { projectName = "my-cool-api" }.run()
        assertContains(tempDir.resolve("service/settings.gradle.kts").toFile().readText(), "my-cool-api")
    }

    @Test
    fun `service type matching is case-insensitive`() {
        cli { serviceType = "SpringBoot" }.run()
        assertTrue(tempDir.resolve("service/build.gradle.kts").toFile().exists())
    }

    @Test
    fun `database type matching is case-insensitive`() {
        cli { databaseType = "Postgres" }.run()
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `migration tool matching is case-insensitive`() {
        cli {
            serviceType = "go"
            databaseType = "postgres"
            migrationTool = "FLYWAY"
        }.run()
        assertTrue(tempDir.resolve("migrations/V001__init.sql").toFile().exists(),
            "Uppercase --migration FLYWAY should resolve to the Flyway generator")
        assertContains(tempDir.resolve("docker-compose.yml").toFile().readText(), "flyway/flyway:10")
    }

    @Test
    fun `force flag allows overwriting non-empty output directory`() {
        // Pre-populate the output directory
        Files.createDirectories(tempDir.resolve("service"))
        Files.writeString(tempDir.resolve("service/existing.txt"), "old content")
        cli { force = true }.run()
        // New generation succeeds alongside existing file
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `generated go service exposes health not api-hello`() {
        cli { serviceType = "go" }.run()
        val main = tempDir.resolve("service/main.go").toFile().readText()
        assertContains(main, "/health")
        assertFalse(main.contains("/api/hello"), "Must not contain /api/hello")
    }

    @Test
    fun `generated docker-compose uses db as service name`() {
        cli { serviceType = "go"; databaseType = "postgres" }.run()
        assertContains(tempDir.resolve("docker-compose.yml").toFile().readText(), "  db:")
    }

    @Test
    fun `new-scaffold writes Dockerfile dev under service subdir`() {
        cli { serviceType = "go"; databaseType = "postgres" }.run()
        assertTrue(tempDir.resolve("service/Dockerfile.dev").toFile().exists(),
            "Expected service/Dockerfile.dev for containerized new-scaffold output")
    }

    @Test
    fun `new-scaffold compose includes service block with build context to service subdir`() {
        cli {
            serviceType = "go"
            databaseType = "postgres"
            projectName = "single-cmd-api"
        }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, "context: ./service")
        assertContains(compose, "dockerfile: Dockerfile.dev")
        assertContains(compose, "  single-cmd-api:")
    }

    @Test
    fun `new-scaffold compose mounts source from service subdir for hot-reload`() {
        cli { serviceType = "go"; databaseType = "postgres" }.run()
        assertContains(tempDir.resolve("docker-compose.yml").toFile().readText(), "./service:/app")
    }

    @Test
    fun `new-scaffold node compose preserves anonymous node_modules mount`() {
        cli { serviceType = "node"; databaseType = "postgres" }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, "./service:/app")
        assertContains(compose, "/app/node_modules")
    }

    @ParameterizedTest
    @CsvSource(
        "postgres,      DATABASE_URL",
        "mysql,         DATABASE_URL",
        "mongodb,       MONGODB_URI",
        "redis,         REDIS_URL",
        "elasticsearch, ELASTICSEARCH_URL"
    )
    fun `existing-dir compose injects correct env var key for each database`(dbType: String, expectedKey: String) {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        cli {
            existingDir = tempDir.toString()
            databaseType = dbType
        }.run()
        assertContains(tempDir.resolve("docker-compose.yml").toFile().readText(), expectedKey)
    }

    // ── Happy path — existing service mode ───────────────────────────────────

    @Test
    fun `existing-dir mode generates Dockerfile dev and docker-compose`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        cli { existingDir = tempDir.toString(); databaseType = "postgres" }.run()
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists())
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `existing-dir mode does not overwrite existing Dockerfile`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM scratch")
        cli { existingDir = tempDir.toString(); databaseType = "postgres" }.run()
        assertContains(tempDir.resolve("Dockerfile").toFile().readText(), "FROM scratch")
        assertTrue(tempDir.resolve("Dockerfile.dev").toFile().exists())
    }

    @Test
    fun `existing-dir mode compose includes service block with depends_on`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        cli { existingDir = tempDir.toString(); databaseType = "postgres" }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, "depends_on")
        assertContains(compose, "service_healthy")
        assertContains(compose, "Dockerfile.dev")
    }

    @Test
    fun `existing-dir mode compose includes source volume mount`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        cli { existingDir = tempDir.toString(); databaseType = "postgres" }.run()
        assertContains(tempDir.resolve("docker-compose.yml").toFile().readText(), ".:/app")
    }

    @Test
    fun `existing-dir node service compose includes node_modules exclusion volume`() {
        Files.writeString(tempDir.resolve("package.json"), "{}")
        cli { existingDir = tempDir.toString(); databaseType = "postgres" }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, ".:/app")
        assertContains(compose, "/app/node_modules")
    }

    @Test
    fun `existing-dir mode explicit service type overrides auto-detection`() {
        // Directory has go.mod but we override to node
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        cli {
            existingDir = tempDir.toString()
            serviceType = "node"
            databaseType = "postgres"
        }.run()
        val dockerfile = tempDir.resolve("Dockerfile.dev").toFile().readText()
        assertContains(dockerfile, "node:18-alpine")
        assertFalse(dockerfile.contains("golang"), "Should use Node Dockerfile, not Go")
    }

    @Test
    fun `existing-dir mode uses explicit port in compose`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        cli {
            existingDir = tempDir.toString()
            databaseType = "postgres"
            port = 9090
        }.run()
        assertContains(tempDir.resolve("docker-compose.yml").toFile().readText(), "9090:9090")
    }

    @Test
    fun `existing-dir mode defaults project name to directory name`() {
        val serviceDir = tempDir.resolve("my-existing-api")
        Files.createDirectories(serviceDir)
        Files.writeString(serviceDir.resolve("go.mod"), "module test")
        cli {
            existingDir = serviceDir.toString()
            databaseType = "postgres"
        }.run()
        assertContains(serviceDir.resolve("docker-compose.yml").toFile().readText(), "my-existing-api")
    }

    // ── Unhappy path — new service mode ──────────────────────────────────────

    @Test
    fun `unsupported service type prints error and creates no service dir`() {
        val err = captureErr { cli { serviceType = "django" }.run() }
        assertContains(err, "Unsupported service type")
        assertFalse(tempDir.resolve("service").toFile().exists())
    }

    @Test
    fun `unsupported database type prints error and creates no compose file`() {
        val err = captureErr { cli { databaseType = "oracle" }.run() }
        assertContains(err, "Unsupported database type")
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `non-empty output directory without force flag prints error and creates no files`() {
        Files.createDirectories(tempDir.resolve("service"))
        Files.writeString(tempDir.resolve("service/existing.txt"), "old")
        val err = captureErr { cli().run() }
        assertContains(err, "already exists")
        assertContains(err, "--force")
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `output path outside current working directory is rejected`() {
        val err = captureErr {
            cli { outputDir = "../../outside-cwd" }.run()
        }
        assertContains(err, "inside the current working directory")
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    // ── Unhappy path — existing service mode ─────────────────────────────────

    @Test
    fun `existing-dir mode with nonexistent path prints error`() {
        val err = captureErr {
            cli { existingDir = "/nonexistent/path/xyz" }.run()
        }
        assertContains(err, "not a directory")
    }

    @Test
    fun `existing-dir mode with empty directory prints detection error`() {
        val emptyDir = tempDir.resolve("empty-svc")
        Files.createDirectories(emptyDir)
        val err = captureErr {
            cli { existingDir = emptyDir.toString() }.run()
        }
        assertContains(err, "Could not detect service type")
        assertContains(err, "--service")
    }

    @Test
    fun `existing-dir mode with polyglot directory prints disambiguation error`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        Files.writeString(tempDir.resolve("package.json"), "{}")
        val err = captureErr {
            cli { existingDir = tempDir.toString(); databaseType = "postgres" }.run()
        }
        assertContains(err, "go")
        assertContains(err, "node")
        assertContains(err, "--service")
        assertFalse(tempDir.resolve("Dockerfile.dev").toFile().exists(),
            "No files should be generated when detection is ambiguous")
    }

    @Test
    fun `unsupported service type in existing-dir mode prints error`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        val err = captureErr {
            cli {
                existingDir = tempDir.toString()
                serviceType = "cobol"
                databaseType = "postgres"
            }.run()
        }
        assertContains(err, "Unsupported service type")
        assertFalse(tempDir.resolve("Dockerfile.dev").toFile().exists())
    }
}
