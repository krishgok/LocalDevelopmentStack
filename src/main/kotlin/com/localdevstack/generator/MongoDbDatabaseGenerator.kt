package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class MongoDbDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml())
        println("  [OK] MongoDB database     ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml() = """
        version: '3.8'

        services:
          mongodb:
            image: mongo:7
            container_name: local-mongodb
            environment:
              MONGO_INITDB_DATABASE: app_db
            ports:
              - "27017:27017"
            volumes:
              - mongodb_data:/data/db
            healthcheck:
              test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
              interval: 10s
              timeout: 5s
              retries: 5

        volumes:
          mongodb_data:
    """.trimIndent()
}
