package com.localdevstack.generator

import java.nio.file.Files
import java.nio.file.Path

class ElasticsearchDatabaseGenerator : DatabaseGenerator {

    override fun generate(outputDir: Path, serviceConfig: ServiceComposeConfig?) {
        Files.createDirectories(outputDir)
        Files.writeString(outputDir.resolve("docker-compose.yml"), dockerComposeYml(serviceConfig))
        println("  [OK] Elasticsearch        ->  ${outputDir.resolve("docker-compose.yml")}")
    }

    private fun dockerComposeYml(serviceConfig: ServiceComposeConfig?) = buildString {
        appendLine("services:")
        appendLine("  db:")
        appendLine("    image: elasticsearch:8.12.2")
        appendLine("    container_name: local-elasticsearch")
        appendLine("    environment:")
        appendLine("      - discovery.type=single-node")
        appendLine("      - xpack.security.enabled=false")
        appendLine("      - \"ES_JAVA_OPTS=-Xms512m -Xmx512m\"")
        appendLine("    ports:")
        appendLine("      - \"9200:9200\"")
        appendLine("    volumes:")
        appendLine("      - elasticsearch_data:/usr/share/elasticsearch/data")
        appendLine("    healthcheck:")
        appendLine("      test: [\"CMD-SHELL\", \"curl -f http://localhost:9200/_cluster/health || exit 1\"]")
        appendLine("      interval: 15s")
        appendLine("      timeout: 10s")
        appendLine("      retries: 5")
        if (serviceConfig != null) {
            appendLine()
            appendServiceBlock(serviceConfig)
        }
        appendLine()
        appendLine("volumes:")
        append("  elasticsearch_data:")
    }
}
