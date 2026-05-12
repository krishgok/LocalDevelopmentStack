package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class RedisDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] Redis                ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: redis:7-alpine")
        appendLine("    container_name: local-redis")
        appendLine("    ports:")
        appendLine("      - \"6379:6379\"")
        appendLine("    volumes:")
        appendLine("      - redis_data:/data")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD\", \"redis-cli\", \"ping\"]")
        appendLine("      interval: 10s")
        appendLine("      timeout: 5s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  redis_data:")
    }
}
