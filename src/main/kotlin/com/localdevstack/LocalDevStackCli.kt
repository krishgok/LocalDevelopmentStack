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

        val resolvedPort = resolvePort()
        val resolvedName = if (projectName == "hello-service") existingPath.fileName.toString() else projectName

        val envVars = dbEnvVars(databaseType)
        val serviceConfig = ServiceComposeConfig(
            name = resolvedName,
            dockerfilePath = "Dockerfile.dev",
            port = resolvedPort,
            envVars = envVars
        )

        println("Wrapping existing service...")
        println("  Service  : $resolvedServiceType  (detected from ${existingPath.fileName})")
        println("  Database : $databaseType")
        println("  Port     : $resolvedPort")
        println()

        dockerfileGenerator.generate(existingPath, resolvedName)
        databaseGenerator.generate(existingPath, serviceConfig)

        println()
        println("Files generated in: $existingPath")
        println()
        println("Next steps:")
        println("  1. cd $existingDir")
        println("  2. docker-compose up --build")
        println("  3. Verify your service using your own endpoints.")
        println("     (LocalDevelopmentStack does not add or modify any endpoints in your service.)")
        printMultiDbTip(existingDir!!, databaseType)
    }

    // ── New service mode ─────────────────────────────────────────────────────────

    private fun runNewServiceMode() {
        val effectiveServiceType = serviceType ?: "springboot"

        val serviceGenerator = resolveServiceGenerator(effectiveServiceType) ?: return
        val databaseGenerator = resolveDatabaseGenerator(databaseType) ?: return

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
        println()

        serviceGenerator.generate(outputPath, projectName)
        databaseGenerator.generate(outputPath)

        println()
        println("Stack generated at: $outputPath")
        println()
        println("Next steps:")
        println("  1. cd $outputDir")
        println("  2. docker-compose up -d")
        println("  3. cd service && ${serviceGenerator.runCommand}")
        println("  4. curl http://localhost:8080/health")
        println("     → {\"status\":\"ok\"}")
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
}
