package com.localdevstack.generator

data class ServiceComposeConfig(
    val name: String,
    val dockerfilePath: String = "Dockerfile.dev",
    val port: Int = 8080,
    val envVars: Map<String, String>
)

fun StringBuilder.appendServiceBlock(config: ServiceComposeConfig) {
    appendLine("  ${config.name}:")
    appendLine("    build:")
    appendLine("      context: .")
    appendLine("      dockerfile: ${config.dockerfilePath}")
    appendLine("    container_name: local-${config.name}")
    appendLine("    ports:")
    appendLine("      - \"${config.port}:${config.port}\"")
    if (config.envVars.isNotEmpty()) {
        appendLine("    environment:")
        config.envVars.forEach { (key, value) ->
            appendLine("      - $key=$value")
        }
    }
    appendLine("    depends_on:")
    appendLine("      db:")
    append("        condition: service_healthy")
}
