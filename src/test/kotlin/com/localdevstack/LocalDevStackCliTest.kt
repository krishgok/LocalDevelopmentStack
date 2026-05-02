package com.localdevstack

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class LocalDevStackCliTest {

    @TempDir
    lateinit var tempDir: Path

    private fun runCli(vararg args: String): Pair<String, String> {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val cli = LocalDevStackCli()
        val cmd = picocli.CommandLine(cli)
        cmd.out = PrintStream(stdout)
        cmd.err = PrintStream(stderr)
        // Parse options manually to keep test independent of CommandLine runner
        for (i in args.indices) {
            when (args[i]) {
                "--service", "-s"   -> if (i + 1 < args.size) cli.serviceType = args[i + 1]
                "--database", "-d"  -> if (i + 1 < args.size) cli.databaseType = args[i + 1]
                "--output", "-o"    -> if (i + 1 < args.size) cli.outputDir = args[i + 1]
                "--name", "-n"      -> if (i + 1 < args.size) cli.projectName = args[i + 1]
            }
        }
        cli.outputDir = tempDir.toString()
        val captureOut = ByteArrayOutputStream()
        val origOut = System.out
        System.setOut(PrintStream(captureOut))
        try { cli.run() } finally { System.setOut(origOut) }
        return captureOut.toString() to stderr.toString()
    }

    @Test
    fun `defaults produce springboot and postgres`() {
        val cli = LocalDevStackCli()
        cli.outputDir = tempDir.toString()
        cli.run()
        assertTrue(tempDir.resolve("service/build.gradle.kts").toFile().exists())
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @ParameterizedTest
    @ValueSource(strings = ["springboot", "go", "python", "node", "rust"])
    fun `all service types generate a service directory`(serviceType: String) {
        val cli = LocalDevStackCli()
        cli.serviceType = serviceType
        cli.outputDir = tempDir.toString()
        cli.run()
        assertTrue(tempDir.resolve("service").toFile().exists(),
            "Expected service/ directory for type $serviceType")
    }

    @ParameterizedTest
    @ValueSource(strings = ["postgres", "mysql", "mongodb", "cockroachdb"])
    fun `all database types generate a docker-compose yml`(dbType: String) {
        val cli = LocalDevStackCli()
        cli.databaseType = dbType
        cli.outputDir = tempDir.toString()
        cli.run()
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists(),
            "Expected docker-compose.yml for db type $dbType")
    }

    @Test
    fun `unsupported service type prints error and creates no files`() {
        val err = ByteArrayOutputStream()
        System.setErr(PrintStream(err))
        val cli = LocalDevStackCli()
        cli.serviceType = "django"
        cli.outputDir = tempDir.toString()
        cli.run()
        System.setErr(System.err)
        assertContains(err.toString(), "Unsupported service type")
        assertTrue(!tempDir.resolve("service").toFile().exists())
    }

    @Test
    fun `unsupported database type prints error and creates no files`() {
        val err = ByteArrayOutputStream()
        System.setErr(PrintStream(err))
        val cli = LocalDevStackCli()
        cli.databaseType = "oracle"
        cli.outputDir = tempDir.toString()
        cli.run()
        System.setErr(System.err)
        assertContains(err.toString(), "Unsupported database type")
        assertTrue(!tempDir.resolve("docker-compose.yml").toFile().exists())
    }

    @Test
    fun `project name is reflected in settings gradle`() {
        val cli = LocalDevStackCli()
        cli.projectName = "my-cool-api"
        cli.outputDir = tempDir.toString()
        cli.run()
        val settings = tempDir.resolve("service/settings.gradle.kts").toFile().readText()
        assertContains(settings, "my-cool-api")
    }

    @Test
    fun `service type matching is case-insensitive`() {
        val cli = LocalDevStackCli()
        cli.serviceType = "SpringBoot"
        cli.outputDir = tempDir.toString()
        cli.run()
        assertTrue(tempDir.resolve("service/build.gradle.kts").toFile().exists())
    }

    @Test
    fun `database type matching is case-insensitive`() {
        val cli = LocalDevStackCli()
        cli.databaseType = "Postgres"
        cli.outputDir = tempDir.toString()
        cli.run()
        assertTrue(tempDir.resolve("docker-compose.yml").toFile().exists())
    }
}
