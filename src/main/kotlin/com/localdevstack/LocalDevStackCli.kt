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
    version = ["1.2.0"],
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

    @Option(
        names = ["--with", "-w"],
        split = ",",
        description = [
            "Opt-in companion services to include in the generated docker-compose.yml.",
            "Comma-separated or repeatable. Supported: mailhog, minio.",
            "  mailhog: SMTP catcher (web UI on :8025, SMTP on :1025)",
            "  minio:   S3-compatible object store (API on :9000, console on :9001)"
        ]
    )
    var companions: List<String> = emptyList()

    @Option(
        names = ["--dry-run"],
        description = ["Print the resolved plan and the files that would be written, then exit without touching the filesystem."]
    )
    var dryRun: Boolean = false

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

        private data class CompanionSpec(val factory: () -> CompanionGenerator)

        // Single source of truth for all supported companion services.
        // Adding a new companion means adding one entry here.
        private val COMPANIONS: Map<String, CompanionSpec> = linkedMapOf(
            "mailhog" to CompanionSpec(::MailhogCompanionGenerator),
            "minio"   to CompanionSpec(::MinioCompanionGenerator),
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
        private val SUPPORTED_COMPANIONS_LIST = COMPANIONS.keys.joinToString(", ")
        private val MIGRATION_CAPABLE_DATABASES_LIST =
            SUPPORTED_MIGRATIONS.filterValues { it.isNotEmpty() }.keys.joinToString(", ")
    }

    override fun run() {
        log.info(
            "invoke: mode=${if (existingDir != null) "existing-dir" else "new-service"}" +
                " service=${serviceType ?: "(default)"} database=$databaseType" +
                " migration=${migrationTool ?: "(none)"} companions=${companions.joinToString(",").ifEmpty { "(none)" }}" +
                " dryRun=$dryRun name=$projectName"
        )
        // Dry-run skips the Docker probe so users on CI / docs hosts can preview
        // generation without Docker installed.
        if (!skipDockerCheck && !dryRun) { checkDockerAvailable() ?: return }

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

        val companionGenerators = resolveCompanions() ?: return
        if (companionGenerators.isNotEmpty() && !validateNameForCompanions(projectName, companionGenerators)) return

        val resolvedPort = resolvePort()
        val resolvedName = if (projectName == DEFAULT_PROJECT_NAME) existingPath.fileName.toString() else projectName
        log.info("resolved: name=$resolvedName port=$resolvedPort")

        val envVars = mergedEnvVars(databaseType, companionGenerators)
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
        if (companionGenerators.isNotEmpty()) println("  Companions: ${companionGenerators.joinToString(", ") { it.companionName }}")
        println()

        if (dryRun) {
            printDryRunPlan(existingPath, mode = "existing-dir", migrationGenerator, companionGenerators, includeServiceScaffold = false)
            return
        }

        log.info("generating Dockerfile.dev at $existingPath")
        dockerfileGenerator.generate(existingPath, resolvedName)
        log.info("generating docker-compose.yml at $existingPath")
        databaseGenerator.generate(existingPath, serviceConfig)

        runMigrationStage(existingPath, resolvedName, migrationGenerator)
        runCompanionStage(existingPath, companionGenerators)

        EnvFileGenerator().generate(existingPath, envVars)
        GitignoreGenerator().generate(existingPath)

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
        val migrationStep = 5
        printMigrationTrailer(migrationGenerator, stepNumber = migrationStep)
        printCompanionTrailer(companionGenerators)
        printMultiDbTip(existingDir!!, databaseType)
    }

    // ── New service mode ─────────────────────────────────────────────────────────

    private fun runNewServiceMode() {
        val effectiveServiceType = serviceType ?: DEFAULT_SERVICE_TYPE

        val serviceGenerator = resolveServiceGenerator(effectiveServiceType) ?: return
        val dockerfileGenerator = resolveDockerfileGenerator(effectiveServiceType) ?: return
        val databaseGenerator = resolveDatabaseGenerator(databaseType) ?: return

        val migrationGenerator = migrationTool?.let { resolveMigrationGenerator(databaseType, it) ?: return }
        if (migrationGenerator != null && !validateNameForMigrations(projectName)) return

        val companionGenerators = resolveCompanions() ?: return
        if (companionGenerators.isNotEmpty() && !validateNameForCompanions(projectName, companionGenerators)) return

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

        val resolvedPort = resolvePort()
        val serviceDir = outputPath.resolve("service")
        val serviceConfig = ServiceComposeConfig(
            name = projectName,
            dockerfilePath = "Dockerfile.dev",
            buildContext = "./service",
            port = resolvedPort,
            envVars = mergedEnvVars(databaseType, companionGenerators),
            volumes = newScaffoldVolumes(effectiveServiceType)
        )

        println("Generating local development stack...")
        println("  Service  : $effectiveServiceType")
        println("  Database : $databaseType")
        println("  Port     : $resolvedPort")
        println("  Output   : $outputPath")
        if (migrationGenerator != null) println("  Migration: ${migrationGenerator.toolName}")
        if (companionGenerators.isNotEmpty()) println("  Companions: ${companionGenerators.joinToString(", ") { it.companionName }}")
        println()

        if (dryRun) {
            printDryRunPlan(outputPath, mode = "new-service", migrationGenerator, companionGenerators, includeServiceScaffold = true)
            return
        }

        log.info("generating service ($effectiveServiceType) at $outputPath")
        serviceGenerator.generate(outputPath, projectName)
        log.info("generating Dockerfile.dev at $serviceDir")
        dockerfileGenerator.generate(serviceDir, projectName)
        log.info("generating docker-compose.yml at $outputPath")
        databaseGenerator.generate(outputPath, serviceConfig)

        runMigrationStage(outputPath, projectName, migrationGenerator)
        runCompanionStage(outputPath, companionGenerators)

        EnvFileGenerator().generate(outputPath, serviceConfig.envVars)
        GitignoreGenerator().generate(outputPath)

        println()
        println("Stack generated at: $outputPath")
        println()
        println("Next steps:")
        println("  1. cd $outputDir")
        println("  2. docker-compose up --build")
        println("     ↳ Builds the dev image and starts the database and your service.")
        println("     ↳ Hot-reload is enabled — source changes are picked up automatically.")
        println("  3. curl http://localhost:$resolvedPort/health")
        println("     → {\"status\":\"ok\"}")
        printMigrationTrailer(migrationGenerator, stepNumber = 4)
        printCompanionTrailer(companionGenerators)
        printMultiDbTip(outputDir, databaseType)
    }

    // Translate per-type volume mounts from the existing-dir layout (source at
    // root, e.g. `.:/app`) to the new-scaffold layout (source under `service/`).
    // Anonymous container-only mounts like `/app/node_modules` pass through.
    private fun newScaffoldVolumes(serviceType: String): List<String> =
        serviceVolumes(serviceType).map { v ->
            if (v.startsWith(".:")) "./service:" + v.substringAfter(".:") else v
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

    // ── Companion helpers ────────────────────────────────────────────────────────

    private fun resolveCompanions(): List<CompanionGenerator>? {
        if (companions.isEmpty()) return emptyList()
        val seen = linkedSetOf<String>()
        val resolved = mutableListOf<CompanionGenerator>()
        for (raw in companions) {
            val name = raw.trim().lowercase()
            if (name.isEmpty()) continue
            if (!seen.add(name)) {
                log.warning("duplicate companion '$name' ignored")
                continue
            }
            val spec = COMPANIONS[name]
            if (spec == null) {
                log.warning("unsupported companion: $raw")
                System.err.println("Unsupported companion: '$raw'.")
                System.err.println("Supported: $SUPPORTED_COMPANIONS_LIST")
                return null
            }
            resolved.add(spec.factory())
        }
        return resolved
    }

    private fun validateNameForCompanions(name: String, generators: List<CompanionGenerator>): Boolean {
        val lower = name.lowercase()
        val clash = generators.firstOrNull { it.companionName == lower }
        if (clash != null) {
            log.warning("name '$name' conflicts with companion service '${clash.companionName}'")
            System.err.println("Error: --name '$name' conflicts with the '${clash.companionName}:' companion service in the generated docker-compose.yml.")
            System.err.println("  Choose a different --name when --with $lower is set.")
            return false
        }
        return true
    }

    private fun mergedEnvVars(dbType: String, generators: List<CompanionGenerator>): Map<String, String> {
        if (generators.isEmpty()) return dbEnvVars(dbType)
        val merged = linkedMapOf<String, String>()
        merged.putAll(dbEnvVars(dbType))
        generators.forEach { gen ->
            gen.envOverlay().forEach { (key, value) ->
                if (merged.containsKey(key)) {
                    log.warning("companion '${gen.companionName}' env key '$key' overrides existing value")
                }
                merged[key] = value
            }
        }
        return merged
    }

    private fun runCompanionStage(outputPath: Path, generators: List<CompanionGenerator>) {
        if (generators.isEmpty()) return
        log.info("appending companion blocks: ${generators.joinToString(",") { it.companionName }}")
        appendCompanionBlocksToCompose(outputPath.resolve("docker-compose.yml"), generators)
        generators.forEach { println("  [OK] Companion: ${it.companionName}") }
    }

    private fun printCompanionTrailer(generators: List<CompanionGenerator>) {
        if (generators.isEmpty()) return
        println()
        println("Companion services available after 'docker-compose up':")
        generators.forEach { gen ->
            when (gen.companionName) {
                "mailhog" -> println("  - MailHog: web UI http://localhost:8025  (SMTP at mailhog:1025 inside the network)")
                "minio"   -> println("  - MinIO:   console http://localhost:9001 (S3 API at http://localhost:9000; user=minio_dev pass=minio_dev_only)")
                else      -> println("  - ${gen.companionName}")
            }
        }
    }

    // ── Dry-run plan output ──────────────────────────────────────────────────────

    private fun printDryRunPlan(
        outputPath: Path,
        mode: String,
        migrationGenerator: MigrationGenerator?,
        companionGenerators: List<CompanionGenerator>,
        includeServiceScaffold: Boolean,
    ) {
        log.info("dry-run: skipping all file writes")
        println("[dry-run] No files will be written. Files that would be generated:")
        val base = outputPath.toString()
        val files = buildList {
            if (includeServiceScaffold) {
                add("$base${java.io.File.separator}service${java.io.File.separator}    (language-specific source files)")
                add("$base${java.io.File.separator}service${java.io.File.separator}Dockerfile.dev")
            } else {
                add("$base${java.io.File.separator}Dockerfile.dev")
            }
            add("$base${java.io.File.separator}docker-compose.yml")
            add("$base${java.io.File.separator}.env")
            add("$base${java.io.File.separator}.env.example")
            add("$base${java.io.File.separator}.gitignore" +
                if (mode == "existing-dir") "  (.env appended if file already exists)" else "")
            if (migrationGenerator != null) {
                when (migrationGenerator.toolName) {
                    "liquibase" -> add("$base${java.io.File.separator}db${java.io.File.separator}changelog${java.io.File.separator}db.changelog-master.sql")
                    "migrate-mongo" -> {
                        add("$base${java.io.File.separator}migrations${java.io.File.separator}0001-init.js")
                        add("$base${java.io.File.separator}migrate-mongo-config.js")
                        add("$base${java.io.File.separator}Dockerfile.migrate")
                    }
                    "golang-migrate" -> add("$base${java.io.File.separator}migrations${java.io.File.separator}000001_init.up.sql")
                    else -> add("$base${java.io.File.separator}migrations${java.io.File.separator}V001__init.sql")
                }
            }
        }
        files.forEach { println("  - $it") }
        if (companionGenerators.isNotEmpty()) {
            println("Companion services that would be appended to docker-compose.yml:")
            companionGenerators.forEach { println("  - ${it.companionName}") }
        }
        println()
        println("Re-run without --dry-run to write these files.")
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
