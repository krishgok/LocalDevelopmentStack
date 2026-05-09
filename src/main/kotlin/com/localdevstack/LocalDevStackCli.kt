package com.localdevstack

import com.localdevstack.detector.DetectionException
import com.localdevstack.detector.ExistingServiceDetector
import com.localdevstack.generator.*
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path

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
        description = ["Database type. Supported: postgres, mysql, mongodb, cockroachdb, redis, mariadb, sqlserver, elasticsearch (default: \${DEFAULT-VALUE})"],
        required = true
    )
    var databaseType: String = "postgres"

    @Option(
        names = ["--output", "-o"],
        description = ["Output directory for a new service scaffold (default: \${DEFAULT-VALUE}). Not used with --existing-dir."]
    )
    var outputDir: String = "./local-dev-stack"

    @Option(
        names = ["--name", "-n"],
        description = ["Project/service name (default: \${DEFAULT-VALUE})"]
    )
    var projectName: String = "hello-service"

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

    override fun run() {
        if (!skipDockerCheck) { checkDockerAvailable() ?: return }

        if (existingDir != null) {
            runExistingServiceMode()
        } else {
            runNewServiceMode()
        }
    }

    // ── Docker check ─────────────────────────────────────────────────────────────

    private fun checkDockerAvailable(): Unit? {
        val result = runCatching {
            ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }.getOrNull()

        if (result == null || result != 0) {
            System.err.println("Error: Docker is not running or not installed.")
            System.err.println("  LocalDevelopmentStack requires Docker to run the generated stack.")
            System.err.println("  Install Docker Desktop: https://www.docker.com/products/docker-desktop")
            System.err.println("  Then start Docker and re-run this command.")
            return null
        }
        return Unit
    }

    // ── Existing service mode ────────────────────────────────────────────────────

    private fun runExistingServiceMode() {
        val existingPath = Path.of(existingDir!!).toAbsolutePath().normalize()

        if (!Files.isDirectory(existingPath)) {
            System.err.println("Error: --existing-dir '$existingPath' is not a directory or does not exist.")
            return
        }

        val resolvedServiceType = try {
            serviceType?.lowercase() ?: ExistingServiceDetector.detect(existingPath)
        } catch (e: DetectionException) {
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
        val resolvedName = if (projectName == "hello-service") existingPath.fileName.toString() else projectName

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

        dockerfileGenerator.generate(existingPath, resolvedName)
        databaseGenerator.generate(existingPath, serviceConfig)

        if (migrationGenerator != null) {
            val dbInfo = dbConnectionInfo(databaseType)
            migrationGenerator.generateScaffold(existingPath, dbInfo, resolvedName)
            appendMigrateBlockToCompose(
                existingPath.resolve("docker-compose.yml"),
                migrationGenerator.composeServiceBlock(dbInfo)
            )
        }

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
        if (migrationGenerator != null) {
            println("  5. Run migrations: docker-compose run --rm migrate")
            println("     ${migrationGenerator.createMigrationHint()}")
        }
        printMultiDbTip(existingDir!!, databaseType)
    }

    // ── New service mode ─────────────────────────────────────────────────────────

    private fun runNewServiceMode() {
        val effectiveServiceType = serviceType ?: "springboot"

        val serviceGenerator = resolveServiceGenerator(effectiveServiceType) ?: return
        val databaseGenerator = resolveDatabaseGenerator(databaseType) ?: return

        val migrationGenerator = migrationTool?.let { resolveMigrationGenerator(databaseType, it) ?: return }
        if (migrationGenerator != null && !validateNameForMigrations(projectName)) return

        val outputPath = Path.of(outputDir).toAbsolutePath().normalize()
        val cwd = Path.of("").toAbsolutePath()
        if (!outputPath.startsWith(cwd)) {
            System.err.println("Output directory must be within the current working directory: $cwd")
            return
        }

        val outputFile = outputPath.toFile()
        if (outputFile.exists() && (outputFile.list()?.isNotEmpty() == true)) {
            if (!force) {
                System.err.println("Output directory '$outputPath' already exists and is not empty.")
                System.err.println("Use --force to overwrite existing files.")
                return
            }
            println("WARNING: overwriting existing files in $outputPath")
        }

        println("Generating local development stack...")
        println("  Service  : $effectiveServiceType")
        println("  Database : $databaseType")
        println("  Output   : $outputPath")
        if (migrationGenerator != null) println("  Migration: ${migrationGenerator.toolName}")
        println()

        serviceGenerator.generate(outputPath, projectName)
        databaseGenerator.generate(outputPath)

        if (migrationGenerator != null) {
            val dbInfo = dbConnectionInfo(databaseType)
            migrationGenerator.generateScaffold(outputPath, dbInfo, projectName)
            appendMigrateBlockToCompose(
                outputPath.resolve("docker-compose.yml"),
                migrationGenerator.composeServiceBlock(dbInfo)
            )
        }

        println()
        println("Stack generated at: $outputPath")
        println()
        println("Next steps:")
        println("  1. cd $outputDir")
        println("  2. docker-compose up -d")
        println("  3. cd service && ${serviceGenerator.runCommand}")
        println("  4. curl http://localhost:8080/health")
        println("     → {\"status\":\"ok\"}")
        if (migrationGenerator != null) {
            println("  5. Run migrations: docker-compose run --rm migrate")
            println("     ${migrationGenerator.createMigrationHint()}")
        }
        printMultiDbTip(outputDir, databaseType)
    }

    // ── Port resolution ──────────────────────────────────────────────────────────

    private fun resolvePort(): Int {
        if (port != null) return port!!
        for (candidate in listOf(8080, 8081, 8082)) {
            if (isPortFree(candidate)) return candidate
        }
        println("Warning: Ports 8080 and 8081 are in use. Using port 8082.")
        println("  If that is also in use, edit the 'ports' field in the generated docker-compose.yml.")
        return 8082
    }

    private fun isPortFree(port: Int): Boolean = runCatching {
        ServerSocket(port).use { true }
    }.getOrDefault(false)

    // ── Multi-DB tip ─────────────────────────────────────────────────────────────

    private fun printMultiDbTip(dir: String, usedDb: String) {
        println()
        println("Tip: To add a second database (e.g. Redis for caching alongside $usedDb), run:")
        println("  localdevstack --existing-dir $dir --database redis --output ./tmp-redis")
        println("  Then copy the 'redis' service block from tmp-redis/docker-compose.yml")
        println("  into your existing docker-compose.yml under the 'services:' key.")
    }

    // ── Resolver helpers ─────────────────────────────────────────────────────────

    private fun resolveServiceGenerator(type: String): ServiceGenerator? = when (type.lowercase()) {
        "springboot" -> SpringBootServiceGenerator()
        "go"         -> GoServiceGenerator()
        "python"     -> PythonServiceGenerator()
        "node"       -> NodeServiceGenerator()
        "rust"       -> RustServiceGenerator()
        "dotnet"     -> DotNetServiceGenerator()
        "java"       -> JavaServiceGenerator()
        "php"        -> PhpServiceGenerator()
        "ruby"       -> RubyServiceGenerator()
        else -> {
            System.err.println("Unsupported service type: '$type'.")
            System.err.println("Supported: springboot, go, python, node, rust, dotnet, java, php, ruby")
            null
        }
    }

    private fun resolveDockerfileGenerator(type: String): DockerfileGenerator? = when (type.lowercase()) {
        "springboot" -> SpringBootDockerfileGenerator()
        "go"         -> GoDockerfileGenerator()
        "python"     -> PythonDockerfileGenerator()
        "node"       -> NodeDockerfileGenerator()
        "rust"       -> RustDockerfileGenerator()
        "dotnet"     -> DotNetDockerfileGenerator()
        "java"       -> JavaDockerfileGenerator()
        "php"        -> PhpDockerfileGenerator()
        "ruby"       -> RubyDockerfileGenerator()
        else -> {
            System.err.println("Unsupported service type: '$type'.")
            System.err.println("Supported: springboot, go, python, node, rust, dotnet, java, php, ruby")
            null
        }
    }

    private fun resolveDatabaseGenerator(type: String): DatabaseGenerator? = when (type.lowercase()) {
        "postgres"      -> PostgresDatabaseGenerator()
        "mysql"         -> MySqlDatabaseGenerator()
        "mongodb"       -> MongoDbDatabaseGenerator()
        "cockroachdb"   -> CockroachDbDatabaseGenerator()
        "redis"         -> RedisDatabaseGenerator()
        "mariadb"       -> MariaDbDatabaseGenerator()
        "sqlserver"     -> SqlServerDatabaseGenerator()
        "elasticsearch" -> ElasticsearchDatabaseGenerator()
        else -> {
            System.err.println("Unsupported database type: '$type'.")
            System.err.println("Supported: postgres, mysql, mongodb, cockroachdb, redis, mariadb, sqlserver, elasticsearch")
            null
        }
    }

    private fun serviceVolumes(type: String): List<String> = when (type.lowercase()) {
        "node"       -> listOf(".:/app", "/app/node_modules")
        "springboot" -> listOf(".:/app", "/app/build", "/app/.gradle")
        "java"       -> listOf(".:/app", "/app/target")
        "dotnet"     -> listOf(".:/app", "/app/bin", "/app/obj")
        "rust"       -> listOf(".:/app", "/app/target")
        "php"        -> listOf(".:/app", "/app/vendor")
        "ruby"       -> listOf(".:/app", "/app/vendor/bundle")
        else         -> listOf(".:/app")
    }

    private fun dbEnvVars(type: String): Map<String, String> = when (type.lowercase()) {
        "postgres"      -> mapOf("DATABASE_URL" to "postgresql://postgres:postgres_dev_only@db:5432/app_db")
        "mysql"         -> mapOf("DATABASE_URL" to "mysql://mysql:mysql_dev_only@db:3306/app_db")
        "mariadb"       -> mapOf("DATABASE_URL" to "mysql://app_user:mariadb_dev_only@db:3306/app_db")
        "mongodb"       -> mapOf("MONGODB_URI" to "mongodb://db:27017/app_db")
        "cockroachdb"   -> mapOf("DATABASE_URL" to "postgresql://root@db:26257/app_db?sslmode=disable")
        "redis"         -> mapOf("REDIS_URL" to "redis://db:6379")
        "sqlserver"     -> mapOf("DATABASE_URL" to "Server=db,1433;Database=app_db;User=sa;Password=DevOnly_123!")
        "elasticsearch" -> mapOf("ELASTICSEARCH_URL" to "http://db:9200")
        else            -> emptyMap()
    }

    // ── Migration support ────────────────────────────────────────────────────────

    private fun dbConnectionInfo(type: String): DbConnectionInfo {
        val lower = type.lowercase()
        return when (lower) {
            "postgres"    -> DbConnectionInfo(lower, jdbcUrl = "jdbc:postgresql://db:5432/app_db", user = "postgres", password = "postgres_dev_only")
            "mysql"       -> DbConnectionInfo(lower, jdbcUrl = "jdbc:mysql://db:3306/app_db", user = "mysql", password = "mysql_dev_only")
            "mariadb"     -> DbConnectionInfo(lower, jdbcUrl = "jdbc:mariadb://db:3306/app_db", user = "app_user", password = "mariadb_dev_only")
            "cockroachdb" -> DbConnectionInfo(lower, jdbcUrl = "jdbc:postgresql://db:26257/app_db?sslmode=disable", user = "root", password = "")
            "sqlserver"   -> DbConnectionInfo(lower, jdbcUrl = "jdbc:sqlserver://db:1433;databaseName=app_db;encrypt=false;trustServerCertificate=true", user = "sa", password = "DevOnly_123!")
            "mongodb"     -> DbConnectionInfo(lower, mongoUri = "mongodb://db:27017/app_db")
            else          -> DbConnectionInfo(lower)
        }
    }

    private fun resolveMigrationGenerator(databaseType: String, tool: String): MigrationGenerator? {
        val db = databaseType.lowercase()
        val t = tool.lowercase()

        val supported: Map<String, List<String>> = mapOf(
            "postgres"    to listOf("flyway", "liquibase"),
            "mysql"       to listOf("flyway", "liquibase"),
            "mariadb"     to listOf("flyway", "liquibase"),
            "sqlserver"   to listOf("flyway", "liquibase"),
            "cockroachdb" to listOf("flyway"),
            "mongodb"     to listOf("migrate-mongo", "golang-migrate"),
            "redis"       to emptyList(),
            "elasticsearch" to emptyList(),
        )

        val tools = supported[db]
        if (tools == null) {
            System.err.println("Unsupported database type for migrations: '$databaseType'.")
            return null
        }
        if (tools.isEmpty()) {
            System.err.println("Migrations are not supported for database '$databaseType'.")
            System.err.println("  Supported databases: postgres, mysql, mariadb, sqlserver, cockroachdb, mongodb")
            return null
        }
        if (t !in tools) {
            System.err.println("Migration tool '$tool' is not supported for database '$databaseType'.")
            System.err.println("  Supported for $databaseType: ${tools.joinToString(", ")}")
            return null
        }

        return when (t) {
            "flyway"         -> FlywayMigrationGenerator()
            "liquibase"      -> LiquibaseMigrationGenerator()
            "migrate-mongo"  -> MigrateMongoMigrationGenerator()
            "golang-migrate" -> GolangMigrateMigrationGenerator()
            else -> {
                System.err.println("Unknown migration tool: '$tool'.")
                null
            }
        }
    }

    private fun validateNameForMigrations(name: String): Boolean {
        val lower = name.lowercase()
        if (lower == "migrate" || lower == "db") {
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
                if (Files.isDirectory(migrationsDir) && migrationsDir.toFile().list()?.isNotEmpty() == true) {
                    collisions.add(migrationsDir)
                }
            }
            "liquibase" -> {
                val changelogDir = existingPath.resolve("db/changelog")
                if (Files.isDirectory(changelogDir) && changelogDir.toFile().list()?.isNotEmpty() == true) {
                    collisions.add(changelogDir)
                }
            }
        }
        if (generator.toolName == "migrate-mongo") {
            val dockerfileMigrate = existingPath.resolve("Dockerfile.migrate")
            if (Files.exists(dockerfileMigrate)) collisions.add(dockerfileMigrate)
        }

        if (collisions.isNotEmpty() && !force) {
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
