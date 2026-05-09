package com.localdevstack.generator

import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllMigrationGeneratorsTest {

    @TempDir
    lateinit var tempDir: Path

    companion object {
        private val postgresInfo = DbConnectionInfo(
            databaseType = "postgres",
            jdbcUrl = "jdbc:postgresql://db:5432/app_db",
            user = "postgres",
            password = "postgres_dev_only"
        )
        private val mongoInfo = DbConnectionInfo(
            databaseType = "mongodb",
            mongoUri = "mongodb://db:27017/app_db"
        )

        @JvmStatic
        fun sqlGenerators(): List<Array<Any>> = listOf(
            arrayOf("flyway",         FlywayMigrationGenerator(),         postgresInfo),
            arrayOf("liquibase",      LiquibaseMigrationGenerator(),      postgresInfo),
            arrayOf("golang-migrate", GolangMigrateMigrationGenerator(),  postgresInfo),
        )

        @JvmStatic
        fun allGenerators(): List<Array<Any>> = sqlGenerators() + listOf(
            arrayOf("migrate-mongo",  MigrateMongoMigrationGenerator(),   mongoInfo),
            arrayOf("golang-migrate-mongo", GolangMigrateMigrationGenerator(), mongoInfo),
        )
    }

    // ── Common compose-block invariants across all generators ────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `compose block declares migrations profile`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        val block = gen.composeServiceBlock(db)
        assertContains(block, "profiles: [\"migrations\"]",
            message = "$name: migrate service must use the 'migrations' compose profile so it does not auto-start on `up`")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `compose block has migrate as the service name`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        val block = gen.composeServiceBlock(db)
        assertContains(block, "  migrate:",
            message = "$name: migrate service block must be keyed on 'migrate:'")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `compose block sets restart no`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        val block = gen.composeServiceBlock(db)
        assertContains(block, "restart: \"no\"",
            message = "$name: migrate service must have restart: no for one-shot semantics")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `compose block depends on db with service_healthy`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        val block = gen.composeServiceBlock(db)
        assertContains(block, "depends_on", message = "$name: must declare depends_on")
        assertContains(block, "service_healthy", message = "$name: must wait for db service_healthy")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `compose block is a service-level snippet not a full compose file`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        val block = gen.composeServiceBlock(db)
        assertFalse(block.contains("version:"), "$name: must not declare top-level 'version:'")
        assertFalse(block.startsWith("services:"), "$name: must not declare top-level 'services:' header")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `migration hint is non-empty`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        assertTrue(gen.createMigrationHint().isNotBlank(), "$name: migration hint must not be blank")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `tool name is non-empty`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        assertTrue(gen.toolName.isNotBlank(), "$name: toolName must not be blank")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allGenerators")
    fun `generateScaffold creates at least one file under outputDir`(name: String, gen: MigrationGenerator, db: DbConnectionInfo) {
        gen.generateScaffold(tempDir, db, "svc")
        val files = tempDir.toFile().walkTopDown().filter { it.isFile }.toList()
        assertTrue(files.isNotEmpty(), "$name: generateScaffold must create at least one file")
    }
}
