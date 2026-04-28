package com.localdevstack

import com.localdevstack.generator.CockroachDbDatabaseGenerator
import com.localdevstack.generator.GoServiceGenerator
import com.localdevstack.generator.MongoDbDatabaseGenerator
import com.localdevstack.generator.MySqlDatabaseGenerator
import com.localdevstack.generator.NodeServiceGenerator
import com.localdevstack.generator.PostgresDatabaseGenerator
import com.localdevstack.generator.PythonServiceGenerator
import com.localdevstack.generator.RustServiceGenerator
import com.localdevstack.generator.SpringBootServiceGenerator
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path

@Command(
    name = "localdevstack",
    mixinStandardHelpOptions = true,
    version = ["1.0.0"],
    description = ["Scaffolds a local development stack with a service and database."]
)
class LocalDevStackCli : Runnable {

    @Option(
        names = ["--service", "-s"],
        description = ["Service type to generate. Supported: springboot, go, python, node, rust (default: \${DEFAULT-VALUE})"]
    )
    var serviceType: String = "springboot"

    @Option(
        names = ["--database", "-d"],
        description = ["Database type to set up. Supported: postgres, mysql, mongodb, cockroachdb (default: \${DEFAULT-VALUE})"]
    )
    var databaseType: String = "postgres"

    @Option(
        names = ["--output", "-o"],
        description = ["Output directory for the generated stack (default: \${DEFAULT-VALUE})"]
    )
    var outputDir: String = "./local-dev-stack"

    @Option(
        names = ["--name", "-n"],
        description = ["Project/service name (default: \${DEFAULT-VALUE})"]
    )
    var projectName: String = "hello-service"

    override fun run() {
        val outputPath = Path.of(outputDir).toAbsolutePath()

        println("Generating local development stack...")
        println("  Service  : $serviceType")
        println("  Database : $databaseType")
        println("  Output   : $outputPath")
        println()

        val serviceGenerator = when (serviceType.lowercase()) {
            "springboot" -> SpringBootServiceGenerator()
            "go"         -> GoServiceGenerator()
            "python"     -> PythonServiceGenerator()
            "node"       -> NodeServiceGenerator()
            "rust"       -> RustServiceGenerator()
            else -> {
                System.err.println("Unsupported service type: '$serviceType'. Supported: springboot, go, python, node, rust")
                return
            }
        }

        val databaseGenerator = when (databaseType.lowercase()) {
            "postgres"     -> PostgresDatabaseGenerator()
            "mysql"        -> MySqlDatabaseGenerator()
            "mongodb"      -> MongoDbDatabaseGenerator()
            "cockroachdb"  -> CockroachDbDatabaseGenerator()
            else -> {
                System.err.println("Unsupported database type: '$databaseType'. Supported: postgres, mysql, mongodb, cockroachdb")
                return
            }
        }

        serviceGenerator.generate(outputPath, projectName)
        databaseGenerator.generate(outputPath)

        println()
        println("Stack generated at: $outputPath")
        println()
        println("Next steps:")
        println("  1. cd $outputDir")
        println("  2. docker-compose up -d")
        println("  3. cd service && ${serviceGenerator.runCommand}")
        println("  4. curl http://localhost:8080/api/hello")
    }
}
