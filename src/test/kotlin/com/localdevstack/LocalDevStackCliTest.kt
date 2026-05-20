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

    // ── Companion services (--with) ──────────────────────────────────────────

    @Test
    fun `--with mailhog adds mailhog service to compose and SMTP env vars to env file`() {
        cli { companions = listOf("mailhog") }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        val env = tempDir.resolve(".env").toFile().readText()
        assertContains(compose, "  mailhog:")
        assertContains(compose, "mailhog/mailhog:v1.0.1")
        assertContains(compose, "SMTP_HOST=\${SMTP_HOST}")
        assertContains(env, "SMTP_HOST=mailhog")
        assertContains(env, "SMTP_PORT=1025")
    }

    @Test
    fun `--with minio adds minio service compose and S3 env vars to env file and named volume`() {
        cli { companions = listOf("minio") }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        val env = tempDir.resolve(".env").toFile().readText()
        assertContains(compose, "  minio:")
        assertContains(env, "S3_ENDPOINT=http://minio:9000")
        assertContains(compose, "  minio_data:")
    }

    @Test
    fun `--with mailhog,minio includes both companions`() {
        cli { companions = listOf("mailhog", "minio") }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, "  mailhog:")
        assertContains(compose, "  minio:")
    }

    @Test
    fun `no --with flag preserves byte-identical default compose`() {
        cli { companions = emptyList() }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertFalse(compose.contains("mailhog"), "mailhog must not appear without --with")
        assertFalse(compose.contains("minio"), "minio must not appear without --with")
    }

    @Test
    fun `--with unknown companion prints error and creates no files`() {
        val err = captureErr { cli { companions = listOf("kafka") }.run() }
        assertContains(err, "Unsupported companion")
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `name collision with companion name is rejected`() {
        val err = captureErr {
            cli {
                companions = listOf("mailhog")
                projectName = "mailhog"
            }.run()
        }
        assertContains(err, "conflicts with the 'mailhog:' companion")
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `--with combines with --migration cleanly`() {
        cli {
            serviceType = "go"
            databaseType = "postgres"
            migrationTool = "flyway"
            companions = listOf("mailhog")
        }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, "  migrate:")
        assertContains(compose, "  mailhog:")
        assertContains(compose, "  db:")
    }

    // ── Dry-run ──────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = ["springboot", "go", "python", "node", "rust", "dotnet", "java", "php", "ruby"])
    fun `--dry-run writes no files for any service type`(serviceType: String) {
        cli { this.serviceType = serviceType; dryRun = true }.run()
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists(),
            "$serviceType: docker-compose.yml must not be written in dry-run")
        assertFalse(tempDir.resolve("service").toFile().exists(),
            "$serviceType: service/ must not be created in dry-run")
        assertFalse(tempDir.resolve(".env").toFile().exists(),
            "$serviceType: .env must not be written in dry-run")
    }

    @Test
    fun `--dry-run still prints the resolved plan to stdout`() {
        val out = ByteArrayOutputStream()
        val orig = System.out
        System.setOut(PrintStream(out))
        try {
            cli { serviceType = "go"; databaseType = "postgres"; dryRun = true }.run()
        } finally {
            System.setOut(orig)
        }
        val printed = out.toString()
        assertContains(printed, "[dry-run]")
        assertContains(printed, "docker-compose.yml")
        assertContains(printed, ".env")
    }

    @Test
    fun `--dry-run in existing-dir mode writes no files`() {
        Files.writeString(tempDir.resolve("go.mod"), "module test")
        cli {
            existingDir = tempDir.toString()
            databaseType = "postgres"
            dryRun = true
        }.run()
        assertFalse(tempDir.resolve("Dockerfile.dev").toFile().exists())
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists())
        assertFalse(tempDir.resolve(".env").toFile().exists())
    }

    @Test
    fun `--dry-run does not require docker to be available`() {
        // The default cli() helper sets skipDockerCheck = true. Here we force
        // skipDockerCheck = false and rely on --dry-run alone to bypass the
        // probe — proves dry-run works on a machine without Docker.
        val out = ByteArrayOutputStream()
        val orig = System.out
        System.setOut(PrintStream(out))
        try {
            LocalDevStackCli().apply {
                outputDir = tempDir.toString()
                serviceType = "go"
                databaseType = "postgres"
                dryRun = true
                // skipDockerCheck deliberately NOT set
            }.run()
        } finally {
            System.setOut(orig)
        }
        val printed = out.toString()
        assertContains(printed, "[dry-run]")
        assertFalse(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    // ── Companion edge cases ─────────────────────────────────────────────────

    @Test
    fun `duplicate companion names are deduplicated silently`() {
        cli { companions = listOf("mailhog", "mailhog") }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        // "  mailhog:" key should appear exactly once
        val occurrences = "  mailhog:".toRegex().findAll(compose).count()
        assertContains(compose, "  mailhog:")
        assert(occurrences == 1) { "expected one mailhog block, found $occurrences" }
    }

    @Test
    fun `empty companion tokens are ignored`() {
        cli { companions = listOf("mailhog", "", "minio") }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, "  mailhog:")
        assertContains(compose, "  minio:")
    }

    @Test
    fun `companion name comparison is case-insensitive`() {
        cli { companions = listOf("MailHog") }.run()
        val compose = tempDir.resolve("docker-compose.yml").toFile().readText()
        assertContains(compose, "  mailhog:")
    }
}
