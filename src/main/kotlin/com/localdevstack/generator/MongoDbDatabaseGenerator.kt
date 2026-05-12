package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class MongoDbDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] MongoDB database     ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("# MongoDB runs without authentication by default in this local setup.")
        appendLine("# Add MONGO_INITDB_ROOT_USERNAME / MONGO_INITDB_ROOT_PASSWORD for any shared environment.")
        appendLine()
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: mongo:7")
        appendLine("    container_name: local-mongodb")
        appendLine("    environment:")
        appendLine("      MONGO_INITDB_DATABASE: app_db")
        appendLine("    ports:")
        appendLine("      - \"27017:27017\"")
        appendLine("    volumes:")
        appendLine("      - mongodb_data:/data/db")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD\", \"mongosh\", \"--eval\", \"db.adminCommand('ping')\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  mongodb_data:")
    }
}
