package com.localdevstack.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnvAndGitignoreGenerationTest {

    @TempDir
    lateinit var tempDir: Path

    // ── EnvFileGenerator ─────────────────────────────────────────────────────

    @Test
    fun `env file holds resolved values one per line`() {
        EnvFileGenerator().generate(tempDir, mapOf(
            "DATABASE_URL" to "postgresql://postgres:postgres_dev_only@db:5432/app_db",
            "SMTP_HOST" to "mailhog",
        ))
        val env = Files.readString(tempDir.resolve(".env"))
        assertContains(env, "DATABASE_URL=postgresql://postgres:postgres_dev_only@db:5432/app_db")
        assertContains(env, "SMTP_HOST=mailhog")
    }

    @Test
    fun `env example holds placeholder values not real secrets`() {
        EnvFileGenerator().generate(tempDir, mapOf("DATABASE_URL" to "postgresql://real:secret@db/x"))
        val example = Files.readString(tempDir.resolve(".env.example"))
        assertContains(example, "DATABASE_URL=<change-me>")
        assertFalse(example.contains("real:secret"), ".env.example must not leak real values")
    }

    @Test
    fun `empty env var map writes no files`() {
        EnvFileGenerator().generate(tempDir, emptyMap())
        assertFalse(Files.exists(tempDir.resolve(".env")))
        assertFalse(Files.exists(tempDir.resolve(".env.example")))
    }

    @Test
    fun `env values with shell-sensitive chars are quoted`() {
        EnvFileGenerator().generate(tempDir, mapOf(
            "DATABASE_URL" to "Server=db,1433;Database=app_db;User=sa;Password=DevOnly_123!",
            "SAFE_URL" to "postgresql://user:pass@db:5432/app",
        ))
        val env = Files.readString(tempDir.resolve(".env"))
        assertContains(env, "DATABASE_URL=\"Server=db,1433;Database=app_db;User=sa;Password=DevOnly_123!\"",
            message = "values containing '!' must be quoted so `source .env` does not trigger history expansion")
        assertContains(env, "SAFE_URL=postgresql://user:pass@db:5432/app",
            message = "values without shell-sensitive chars stay unquoted")
    }

    @Test
    fun `env values with embedded quotes get backslash-escaped inside quoting`() {
        EnvFileGenerator().generate(tempDir, mapOf("K" to """foo"bar"""))
        val env = Files.readString(tempDir.resolve(".env"))
        assertContains(env, """K="foo\"bar"""")
    }

    // ── GitignoreGenerator ───────────────────────────────────────────────────

    @Test
    fun `fresh gitignore contains env and local globs`() {
        GitignoreGenerator().generate(tempDir)
        val gi = Files.readString(tempDir.resolve(".gitignore"))
        assertContains(gi, ".env")
        assertContains(gi, "*.local")
    }

    @Test
    fun `existing gitignore is appended with env entry not overwritten`() {
        Files.writeString(tempDir.resolve(".gitignore"), "node_modules/\nbuild/\n")
        GitignoreGenerator().generate(tempDir)
        val gi = Files.readString(tempDir.resolve(".gitignore"))
        assertContains(gi, "node_modules/")
        assertContains(gi, "build/")
        assertContains(gi, ".env")
    }

    @Test
    fun `gitignore with env already present is left unchanged`() {
        val original = "node_modules/\n.env\n"
        Files.writeString(tempDir.resolve(".gitignore"), original)
        GitignoreGenerator().generate(tempDir)
        val gi = Files.readString(tempDir.resolve(".gitignore"))
        assertEquals(original, gi, "no changes when .env is already gitignored")
    }

    @Test
    fun `repeated calls do not duplicate entries`() {
        GitignoreGenerator().generate(tempDir)
        GitignoreGenerator().generate(tempDir)
        val gi = Files.readString(tempDir.resolve(".gitignore"))
        val envOccurrences = gi.lineSequence().count { it.trim() == ".env" }
        assertEquals(1, envOccurrences, "second run must not duplicate .env entry")
    }

    // ── Compose interpolation contract ───────────────────────────────────────

    @Test
    fun `service env vars in compose use VAR interpolation not literals`() {
        val config = ServiceComposeConfig(
            name = "svc",
            port = 8080,
            envVars = mapOf("DATABASE_URL" to "postgresql://x:y@db/z"),
        )
        val rendered = buildString { appendServiceBlock(config) }
        assertContains(rendered, "DATABASE_URL=\${DATABASE_URL}")
        assertFalse(rendered.contains("postgresql://x:y@db/z"),
            "literal credential value must not appear in compose; it belongs in .env")
    }

    @Test
    fun `service compose block includes healthcheck stanza targeting health endpoint`() {
        val config = ServiceComposeConfig(name = "svc", port = 8080, envVars = emptyMap())
        val rendered = buildString { appendServiceBlock(config) }
        assertContains(rendered, "    healthcheck:")
        assertContains(rendered, "http://localhost:8080/health")
        assertContains(rendered, "start_period: 60s")
    }

    @Test
    fun `service healthcheck uses the configured port not a hardcoded 8080`() {
        val config = ServiceComposeConfig(name = "svc", port = 9090, envVars = emptyMap())
        val rendered = buildString { appendServiceBlock(config) }
        assertContains(rendered, "http://localhost:9090/health")
        assertFalse(rendered.contains("http://localhost:8080/health"))
    }
}
