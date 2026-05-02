package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class MySqlDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml())
        println("  [OK] MySQL database       ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml() = """
        version: '3.8'

        # WARNING: these credentials are for LOCAL DEVELOPMENT ONLY.
        # Change MYSQL_ROOT_PASSWORD and MYSQL_PASSWORD before committing or deploying.

        services:
          mysql:
            image: mysql:8
            container_name: local-mysql
            environment:
              MYSQL_DATABASE: app_db
              MYSQL_ROOT_PASSWORD: ${"$"}{MYSQL_ROOT_PASSWORD:-root_dev_only}
              MYSQL_USER: mysql
              MYSQL_PASSWORD: ${"$"}{MYSQL_PASSWORD:-mysql_dev_only}
            ports:
              - "3306:3306"
            volumes:
              - mysql_data:/var/lib/mysql
            healthcheck:
              test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${"$"}{MYSQL_ROOT_PASSWORD:-root_dev_only}"]
              interval: 10s
              timeout: 5s
              retries: 5

        volumes:
          mysql_data:
    """.trimIndent()
}
