package com.localdevstack.generator

data class ServiceComposeConfig(
    val name: String,
    val dockerfilePath: String = "Dockerfile.dev",
    val buildContext: String = ".",
    val port: Int = 8080,
    val envVars: Map<String, String>,
    val volumes: List<String> = emptyList()
)

fun StringBuilder.appendServiceBlock(config: ServiceComposeConfig) {
    appendLine("  ${config.name}:")
    appendLine("    build:")
    appendLine("      context: ${config.buildContext}")
    appendLine("      dockerfile: ${config.dockerfilePath}")
    appendLine("    container_name: local-${config.name}")
    appendLine("    ports:")
    appendLine("      - \"${config.port}:${config.port}\"")
    if (config.envVars.isNotEmpty()) {
        appendLine("    environment:")
        config.envVars.forEach { (key, _) ->
            // Reference each env var through compose interpolation so the value
            // lives in the generated `.env` file (gitignored) rather than being
            // baked into committed YAML. See EnvFileGenerator.
            appendLine("      - $key=\${$key}")
        }
    }
    if (config.volumes.isNotEmpty()) {
        appendLine("    volumes:")
        config.volumes.forEach { vol ->
            appendLine("      - $vol")
        }
    }
    appendLine("    depends_on:")
    appendLine("      db:")
    appendLine("        condition: service_healthy")
    appendLine("    healthcheck:")
    // wget-then-curl fallback covers every base image used by the 9 service
    // generators (alpine busybox has wget; slim/SDK debian images have curl).
    appendLine("      test: [\"CMD-SHELL\", \"wget -q -O- http://localhost:${config.port}/health || curl -fsS http://localhost:${config.port}/health || exit 1\"]")
    appendLine("      interval: 10s")
    appendLine("      timeout: 3s")
    appendLine("      retries: 6")
    appendLine("      start_period: 60s")
}
