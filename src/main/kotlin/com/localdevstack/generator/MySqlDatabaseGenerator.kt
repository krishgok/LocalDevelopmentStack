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

        services:
          mysql:
            image: mysql:8
            container_name: local-mysql
            environment:
              MYSQL_DATABASE: app_db
              MYSQL_ROOT_PASSWORD: root
              MYSQL_USER: mysql
              MYSQL_PASSWORD: mysql
            ports:
              - "3306:3306"
            volumes:
              - mysql_data:/var/lib/mysql
            healthcheck:
              test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-proot"]
              interval: 10s
              timeout: 5s
              retries: 5

        volumes:
          mysql_data:
    """.trimIndent()
}
