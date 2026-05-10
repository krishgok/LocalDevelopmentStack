package com.localdevstack

import com.localdevstack.detector.DetectionException
import com.localdevstack.detector.ExistingServiceDetector
import com.localdevstack.generator.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.logging.Level

@Command(
    name = "localdevstack",
    mixinStandardHelpOptions = true,
    version = ["1.1.0"],
    description = [
        "Scaffold a local development stack with a service and database.",
        "Supports new service generation or wrapping an existing service directory."
    ]
)
class LocalDevStackCli : Runnable {

    @Option(
        names = ["--service", "-s"],
        description = ["Service type. Supported: springboot, go, python, node, rust, dotnet, java, php, ruby. Auto-detected when --existing-dir is used."]
    )
    var serviceType: String? = null

    @Option(
        names = ["--database", "-d"],
        description = ["Database type. Supported: postgres, mysql, mongodb, cockroachdb, redis, mariadb, sqlserver, elasticsearch (default: \${DEFAULT-VALUE})"]
    )
    var databaseType: String = DEFAULT_DATABASE

    @Option(
        names = ["--output", "-o"],
        description = ["Output directory for a new service scaffold (default: \${DEFAULT-VALUE}). Not used with --existing-dir."]
    )
    var outputDir: String = DEFAULT_OUTPUT_DIR

    @Option(
        names = ["--name", "-n"],
        description = ["Project/service name (default: \${DEFAULT-VALUE})"]
    )
    var projectName: String = DEFAULT_PROJECT_NAME

    @Option(
        names = ["--force", "-f"],
        description = ["Overwrite existing output directory without prompting (new service mode only)"]
    )
    var force: Boolean = false

    @Option(
        names = ["--existing-dir", "-e"],
        description = ["Path to an existing service directory. Generates Dockerfile.dev + docker-compose.yml alongside the existing code."]
    )
    var existingDir: String? = null

    @Option(
        names = ["--port", "-p"],
        description = ["Host port to map to the service container. Auto-selects 8080→8081→8082 if not specified."]
    )
    var port: Int? = null

    @Option(
        names = ["--migration", "-m"],
        description = [
            "Migration tool. Supported per-database:",
            "  postgres/mysql/mariadb/sqlserver: flyway, liquibase",
            "  cockroachdb:                     flyway",
            "  mongodb:                         migrate-mongo, golang-migrate",
            "  redis/elasticsearch:             not supported",
            "Omit to skip migration scaffolding."
        ]
    )
    var migrationTool: String? = null

    // Set to true in tests to skip the docker availability check
    internal var skipDockerCheck: Boolean = false

    private val log = Logging.named("LocalDevStackCli")

    companion object {
        const val DEFAULT_PROJECT_NAME = "hello-service"
        const val DEFAULT_DATABASE = "postgres"
        const val DEFAULT_OUTPUT_DIR = "./local-dev-stack"
        const val DEFAULT_SERVICE_TYPE = "springboot"
        private const val DOCKER_PROBE_TIMEOUT_SECONDS: Long = 10
        private val PORT_CANDIDATES = listOf(8080, 8081, 8082)
        private val DEFAULT_VOLUMES = listOf(".:/app")

        private data class ServiceSpec(
            val service: () -> ServiceGenerator,
            val dockerfile: () -> DockerfileGenerator,
            val volumes: List<String>,
        )

        // Single source of truth for all 9 supported service types.
        // Adding a tenth type means adding one entry here — no further dispatch edits needed.
        private val SERVICES: Map<String, ServiceSpec> = linkedMapOf(
            "springboot" to ServiceSpec(::SpringBootServiceGenerator, ::SpringBootDockerfileGenerator,
                listOf(".:/app", "/app/build", "/app/.gradle")),
            "go"         to ServiceSpec(::GoServiceGenerator,         ::GoDockerfileGenerator,         DEFAULT_VOLUMES),
            "python"     to ServiceSpec(::PythonServiceGenerator,     ::PythonDockerfileGenerator,     DEFAULT_VOLUMES),
            "node"       to ServiceSpec(::NodeServiceGenerator,       ::NodeDockerfileGenerator,
                listOf(".:/app", "/app/node_modules")),
            "rust"       to ServiceSpec(::RustServiceGenerator,       ::RustDockerfileGenerator,
                listOf(".:/app", "/app/target")),
            "dotnet"     to ServiceSpec(::DotNetServiceGenerator,     ::DotNetDockerfileGenerator,
                listOf(".:/app", "/app/bin", "/app/obj")),
            "java"       to ServiceSpec(::JavaServiceGenerator,       ::JavaDockerfileGenerator,
                listOf(".:/app", "/app/target")),
            "php"        to ServiceSpec(::PhpServiceGenerator,        ::PhpDockerfileGenerator,
                listOf(".:/app", "/app/vendor")),
            "ruby"       to ServiceSpec(::RubyServiceGenerator,       ::RubyDockerfileGenerator,
                listOf(".:/app", "/app/vendor/bundle")),
        )

        private data class DbSpec(
            val database: () -> DatabaseGenerator,
            val envVars: Map<String, String>,
            val connectionInfo: (String) -> DbConnectionInfo,
        )

        // Single source of truth for all 8 supported database types.
        private val DATABASES: Map<String, DbSpec> = linkedMapOf(
            "postgres" to DbSpec(
                ::PostgresDatabaseGenerator,
                mapOf("DATABASE_URL" to "postgresql://postgres:postgres_dev_only@db:5432/app_db"),
                { DbConnectionInfo(it, jdbcUrl = "jdbc:postgresql://db:5432/app_db", user = "postgres", password = "postgres_dev_only") },
            ),
            "mysql" to DbSpec(
                ::MySqlDatabaseGenerator,
                mapOf("DATABASE_URL" to "mysql://mysql:mysql_dev_only@db:3306/app_db"),
                { DbConnectionInfo(it, jdbcUrl = "jdbc:mysql://db:3306/app_db", user = "mysql", password = "mysql_dev_only") },
            ),
            "mongodb" to DbSpec(
                ::MongoDbDatabaseGenerator,
                mapOf("MONGODB_URI" to "mongodb://db:27017/app_db"),
                { DbConnectionInfo(it, mongoUri = "mongodb://db:27017/app_db") },
            ),
            "cockroachdb" to DbSpec(
                ::CockroachDbDatabaseGenerator,
                mapOf("DATABASE_URL" to "postgresql://root@db:26257/app_db?sslmode=disable"),
                { DbConnectionInfo(it, jdbcUrl = "jdbc:postgresql://db:26257/app_db?sslmode=disable", user = "root", password = "") },
            ),
            "redis" to DbSpec(
                ::RedisDatabaseGenerator,
                mapOf("REDIS_URL" to "redis://db:6379"),
                { DbConnectionInfo(it) },
            ),
            "mariadb" to DbSpec(
                ::MariaDbDatabaseGenerator,
                mapOf("DATABASE_URL" to "mysql://app_user:mariadb_dev_only@db:3306/app_db"),
                { DbConnectionInfo(it, jdbcUrl = "jdbc:mariadb://db:3306/app_db", user = "app_user", password = "mariadb_dev_only") },
            ),
            "sqlserver" to DbSpec(
                ::SqlServerDatabaseGenerator,
                mapOf("DATABASE_URL" to "Server=db,1433;Database=app_db;User=sa;Password=DevOnly_123!"),
                { DbConnectionInfo(it, jdbcUrl = "jdbc:sqlserver://db:1433;databaseName=app_db;encrypt=false;trustServerCertificate=true", user = "sa", password = "DevOnly_123!") },
            ),
            "elasticsearch" to DbSpec(
                ::ElasticsearchDatabaseGenerator,
                mapOf("ELASTICSEARCH_URL" to "http://db:9200"),
                { DbConnectionInfo(it) },
            ),
        )

        // Compatibility matrix: which migration tools work with which database.
        // Empty list = database is recognised but has no compatible migration tool.
        // Mirrors the table in CLAUDE.md.
        private val SUPPORTED_MIGRATIONS: Map<String, List<String>> = mapOf(
            "postgres"      to listOf("flyway", "liquibase"),
            "mysql"         to listOf("flyway", "liquibase"),
            "mariadb"       to listOf("flyway", "liquibase"),
            "sqlserver"     to listOf("flyway", "liquibase"),
            "cockroachdb"   to listOf("flyway"),
            "mongodb"       to listOf("migrate-mongo", "golang-migrate"),
            "redis"         to emptyList(),
            "elasticsearch" to emptyList(),
        )

        private val SUPPORTED_SERVICES_LIST = SERVICES.keys.joinToString(", ")
        private val SUPPORTED_DATABASES_LIST = DATABASES.keys.joinToString(", ")
        private val MIGRATION_CAPABLE_DATABASES_LIST =
            SUPPORTED_MIGRATIONS.filterValues { it.isNotEmpty() }.keys.joinToString(", ")
    }

    override fun run() {
        log.info(
            "invoke: mode=${if (existingDir != null) "existing-dir" else "new-service"}" +
                " service=${serviceType ?: "(default)"} database=$databaseType" +
                " migration=${migrationTool ?: "(none)"} name=$projectName"
        )
        if (!skipDockerCheck) { checkDockerAvailable() ?: return }

        if (existingDir != null) {
            runExistingServiceMode()
        } else {
            runNewServiceMode()
        }
        log.info("invoke complete")
    }

    // ── Docker check ─────────────────────────────────────────────────────────────

    private fun checkDockerAvailable(): Unit? {
        val outcome: DockerProbe = runCatching {
            val process = ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(DOCKER_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                DockerProbe.Timeout
            } else {
                DockerProbe.Exit(process.exitValue())
            }
        }.getOrElse { DockerProbe.SpawnError(it) }

        if (outcome is DockerProbe.Exit && outcome.code == 0) {
            log.info("docker check ok")
            return Unit
        }

        when (outcome) {
            is DockerProbe.Exit -> log.warning("docker check failed (exit=${outcome.code})")
            DockerProbe.Timeout -> log.warning("docker check timed out after ${DOCKER_PROBE_TIMEOUT_SECONDS}s")
            is DockerProbe.SpawnError -> log.log(Level.WARNING, "docker check could not spawn process", outcome.cause)
        }
        System.err.println("Error: Docker is not running or not installed.")
        System.err.println("  LocalDevelopmentStack requires Docker to run the generated stack.")
        System.err.println("  Install Docker Desktop: https://www.docker.com/products/docker-desktop")
        System.err.println("  Then start Docker and re-run this command.")
        return null
    }

    private sealed class DockerProbe {
        data class Exit(val code: Int) : DockerProbe()
        object Timeout : DockerProbe()
        data class SpawnError(val cause: Throwable) : DockerProbe()
    }

    // ── Existing service mode ────────────────────────────────────────────────────

    private fun runExistingServiceMode() {
        val existingPath = Path.of(existingDir!!).toAbsolutePath().normalize()
        log.info("existing-dir mode: path=$existingPath")

        if (!Files.isDirectory(existingPath)) {
            log.warning("existing-dir is not a directory: $existingPath")
            System.err.println("Error: --existing-dir '$existingPath' is not a directory or does not exist.")
            return
        }

        val resolvedServiceType = try {
            val explicit = serviceType?.lowercase()
            if (explicit != null) {
                log.info("service type: $explicit (explicit)")
                explicit
            } else {
                val detected = ExistingServiceDetector.detect(existingPath)
                log.info("service type: $detected (auto-detected)")
                detected
            }
        } catch (e: DetectionException) {
            log.log(Level.WARNING, "service detection failed", e)
            System.err.println(e.message)
            return
        }

        val dockerfileGenerator = resolveDockerfileGenerator(resolvedServiceType) ?: return
        val databaseGenerator = resolveDatabaseGenerator(databaseType) ?: return

        val migrationGenerator = migrationTool?.let { resolveMigrationGenerator(databaseType, it) ?: return }
        if (migrationGenerator != null) {
            if (!validateNameForMigrations(projectName)) return
            if (!checkMigrationCollision(existingPath, migrationGenerator)) return
        }

        val resolvedPort = resolvePort()
        val resolvedName = if (projectName == DEFAULT_PROJECT_NAME) existingPath.fileName.toString() else projectName
        log.info("resolved: name=$resolvedName port=$resolvedPort")

        val envVars = dbEnvVars(databaseType)
        val serviceConfig = ServiceComposeConfig(
            name = resolvedName,
            dockerfilePath = "Dockerfile.dev",
            port = resolvedPort,
            envVars = envVars,
            volumes = serviceVolumes(resolvedServiceType)
        )

        println("Wrapping existing service...")
        println("  Service  : $resolvedServiceType  (detected from ${existingPath.fileName})")
        println("  Database : $databaseType")
        println("  Port     : $resolvedPort")
        if (migrationGenerator != null) println("  Migration: ${migrationGenerator.toolName}")
        println()

        log.info("generating Dockerfile.dev at $existingPath")
        dockerfileGenerator.generate(existingPath, resolvedName)
        log.info("generating docker-compose.yml at $existingPath")
        databaseGenerator.generate(existingPath, serviceConfig)

        runMigrationStage(existingPath, resolvedName, migrationGenerator)

        println()
        println("Files generated in: $existingPath")
        println()
        println("Next steps:")
        println("  1. cd $existingDir")
        println("  2. docker-compose up --build")
        println("     ↳ Builds the dev image and starts the database and your service.")
        println("     ↳ Hot-reload is enabled — source changes are picked up automatically.")
        println("     ↳ No rebuild needed while the containers are running.")
        println("  3. Edit your source files; the hot-reload watcher inside the container")
        println("     recompiles/restarts your service automatically.")
        println("  4. Verify your service using your own endpoints.")
        println("     (LocalDevelopmentStack does not add or modify any endpoints in your service.)")
        printMigrationTrailer(migrationGenerator, stepNumber = 5)
        printMultiDbTip(existingDir!!, databaseType)
    }

    // ── New service mode ─────────────────────────────────────────────────────────

    private fun runNewServiceMode() {
        val effectiveServiceType = serviceType ?: DEFAULT_SERVICE_TYPE

        val serviceGenerator = resolveServiceGenerator(effectiveServiceType) ?: return
        val databaseGenerator = resolveDatabaseGenerator(databaseType) ?: return

        val migrationGenerator = migrationTool?.let { resolveMigrationGenerator(databaseType, it) ?: return }
        if (migrationGenerator != null && !validateNameForMigrations(projectName)) return

        val outputPath = Path.of(outputDir).toAbsolutePath().normalize()
        val cwd = Path.of("").toAbsolutePath()
        if (!outputPath.startsWith(cwd)) {
            log.warning("output rejected (outside cwd): outputPath=$outputPath cwd=$cwd")
            System.err.println("Output directory must be inside the current working directory ($cwd); got: $outputPath")
            return
        }
        log.info("new-service mode: service=$effectiveServiceType outputPath=$outputPath")

        if (Files.isDirectory(outputPath) && hasAnyEntry(outputPath)) {
            if (!force) {
                log.warning("non-empty output rejected: $outputPath")
                System.err.println("Output directory '$outputPath' already exists and is not empty.")
                System.err.println("Use --force to overwrite existing files.")
                return
            }
            log.info("force overwrite: $outputPath")
            println("WARNING: overwriting existing files in $outputPath")
        }

        println("Generating local development stack...")
        println("  Service  : $effectiveServiceType")
        println("  Database : $databaseType")
        println("  Output   : $outputPath")
        if (migrationGenerator != null) println("  Migration: ${migrationGenerator.toolName}")
        println()

        log.info("generating service ($effectiveServiceType) at $outputPath")
        serviceGenerator.generate(outputPath, projectName)
        log.info("generating docker-compose.yml at $outputPath")
        databaseGenerator.generate(outputPath)

        runMigrationStage(outputPath, projectName, migrationGenerator)

        println()
        println("Stack generated at: $outputPath")
        println()
        println("Next steps:")
        println("  1. cd $outputDir")
        println("  2. docker-compose up -d")
        println("  3. cd service && ${serviceGenerator.runCommand}")
        println("  4. curl http://localhost:8080/health")
        println("     → {\"status\":\"ok\"}")
        printMigrationTrailer(migrationGenerator, stepNumber = 5)
        printMultiDbTip(outputDir, databaseType)
    }

    // ── Port resolution ──────────────────────────────────────────────────────────

    private fun resolvePort(): Int {
        if (port != null) return port!!
        for (candidate in PORT_CANDIDATES) {
            if (isPortFree(candidate)) return candidate
        }
        val fallback = PORT_CANDIDATES.last()
        println("Warning: Ports ${PORT_CANDIDATES.dropLast(1).joinToString(", ")} are in use. Using port $fallback.")
        println("  If that is also in use, edit the 'ports' field in the generated docker-compose.yml.")
        return fallback
    }

    private fun isPortFree(port: Int): Boolean = runCatching {
        ServerSocket(port).use { true }
    }.getOrDefault(false)

    private fun hasAnyEntry(dir: Path): Boolean = runCatching {
        Files.list(dir).use { it.findAny().isPresent }
    }.getOrDefault(false)

    // ── Migration helpers ────────────────────────────────────────────────────────

    private fun runMigrationStage(outputPath: Path, projectName: String, generator: MigrationGenerator?) {
        if (generator == null) return
        val dbInfo = dbConnectionInfo(databaseType)
        log.info("generating migration scaffold (${generator.toolName}) at $outputPath")
        generator.generateScaffold(outputPath, dbInfo, projectName)
        appendMigrateBlockToCompose(
            outputPath.resolve("docker-compose.yml"),
            generator.composeServiceBlock(dbInfo)
        )
    }

    private fun printMigrationTrailer(generator: MigrationGenerator?, stepNumber: Int) {
        if (generator == null) return
        println("  $stepNumber. Run migrations: docker-compose run --rm migrate")
        println("     ${generator.createMigrationHint()}")
    }

    // ── Multi-DB tip ─────────────────────────────────────────────────────────────

    private fun printMultiDbTip(dir: String, usedDb: String) {
        println()
        println("Tip: To add a second database (e.g. Redis for caching alongside $usedDb), run:")
        println("  localdevstack --existing-dir $dir --database redis --output ./tmp-redis")
        println("  Then copy the 'redis' service block from tmp-redis/docker-compose.yml")
        println("  into your existing docker-compose.yml under the 'services:' key.")
    }

    // ── Resolver helpers ─────────────────────────────────────────────────────────

    private fun resolveServiceGenerator(type: String): ServiceGenerator? {
        val spec = SERVICES[type.lowercase()]
        if (spec == null) {
            log.warning("unsupported service type: $type")
            System.err.println("Unsupported service type: '$type'.")
            System.err.println("Supported: $SUPPORTED_SERVICES_LIST")
            return null
        }
        return spec.service()
    }

    private fun resolveDockerfileGenerator(type: String): DockerfileGenerator? {
        val spec = SERVICES[type.lowercase()]
        if (spec == null) {
            log.warning("unsupported service type for Dockerfile: $type")
            System.err.println("Unsupported service type: '$type'.")
            System.err.println("Supported: $SUPPORTED_SERVICES_LIST")
            return null
        }
        return spec.dockerfile()
    }

    private fun resolveDatabaseGenerator(type: String): DatabaseGenerator? {
        val spec = DATABASES[type.lowercase()]
        if (spec == null) {
            log.warning("unsupported database type: $type")
            System.err.println("Unsupported database type: '$type'.")
            System.err.println("Supported: $SUPPORTED_DATABASES_LIST")
            return null
        }
        return spec.database()
    }

    private fun serviceVolumes(type: String): List<String> =
        SERVICES[type.lowercase()]?.volumes ?: DEFAULT_VOLUMES

    private fun dbEnvVars(type: String): Map<String, String> =
        DATABASES[type.lowercase()]?.envVars ?: emptyMap()

    // ── Migration support ────────────────────────────────────────────────────────

    private fun dbConnectionInfo(type: String): DbConnectionInfo {
        val lower = type.lowercase()
        return DATABASES[lower]?.connectionInfo?.invoke(lower) ?: DbConnectionInfo(lower)
    }

    private fun resolveMigrationGenerator(databaseType: String, tool: String): MigrationGenerator? {
        val db = databaseType.lowercase()
        val t = tool.lowercase()

        val tools = SUPPORTED_MIGRATIONS[db]
        if (tools == null) {
            log.warning("unsupported database for migrations: $databaseType")
            System.err.println("Unsupported database type for migrations: '$databaseType'.")
            return null
        }
        if (tools.isEmpty()) {
            log.warning("no migration tools support database: $databaseType")
            System.err.println("Migrations are not supported for database '$databaseType'.")
            System.err.println("  Supported databases: $MIGRATION_CAPABLE_DATABASES_LIST")
            return null
        }
        if (t !in tools) {
            log.warning("incompatible (database, tool): $databaseType / $tool")
            System.err.println("Migration tool '$tool' is not supported for database '$databaseType'.")
            System.err.println("  Supported for $databaseType: ${tools.joinToString(", ")}")
            return null
        }

        log.info("migration generator: $t for $db")
        return when (t) {
            "flyway"         -> FlywayMigrationGenerator()
            "liquibase"      -> LiquibaseMigrationGenerator()
            "migrate-mongo"  -> MigrateMongoMigrationGenerator()
            "golang-migrate" -> GolangMigrateMigrationGenerator()
            else -> {
                log.warning("unknown migration tool: $tool")
                System.err.println("Unknown migration tool: '$tool'.")
                null
            }
        }
    }

    private fun validateNameForMigrations(name: String): Boolean {
        val lower = name.lowercase()
        if (lower == "migrate" || lower == "db") {
            log.warning("name '$name' conflicts with reserved compose service '$lower'")
            System.err.println("Error: --name '$name' conflicts with the '$lower:' service in the generated docker-compose.yml.")
            System.err.println("  Choose a different --name when --migration is set.")
            return false
        }
        return true
    }

    private fun checkMigrationCollision(existingPath: Path, generator: MigrationGenerator): Boolean {
        val collisions = mutableListOf<Path>()
        when (generator.toolName) {
            "flyway", "golang-migrate", "migrate-mongo" -> {
                val migrationsDir = existingPath.resolve("migrations")
                if (Files.isDirectory(migrationsDir) && hasAnyEntry(migrationsDir)) {
                    collisions.add(migrationsDir)
                }
            }
            "liquibase" -> {
                val changelogDir = existingPath.resolve("db/changelog")
                if (Files.isDirectory(changelogDir) && hasAnyEntry(changelogDir)) {
                    collisions.add(changelogDir)
                }
            }
        }
        if (generator.toolName == "migrate-mongo") {
            val dockerfileMigrate = existingPath.resolve("Dockerfile.migrate")
            if (Files.exists(dockerfileMigrate)) collisions.add(dockerfileMigrate)
        }

        if (collisions.isNotEmpty() && !force) {
            log.warning("migration scaffold collisions: ${collisions.joinToString(", ")}")
            System.err.println("Error: migration scaffolding would overwrite existing files:")
            collisions.forEach { System.err.println("  - $it") }
            System.err.println("Use --force to overwrite, or remove the conflicting files first.")
            return false
        }
        if (collisions.isNotEmpty() && force) {
            println("WARNING: --force is set; overwriting existing migration files in:")
            collisions.forEach { println("  - $it") }
        }
        return true
    }
}
